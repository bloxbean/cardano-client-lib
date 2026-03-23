package com.bloxbean.cardano.client.crypto;

import com.bloxbean.cardano.client.crypto.kes.Sum6KesSigner;
import com.bloxbean.cardano.client.crypto.kes.Sum6KesVerifier;
import com.bloxbean.cardano.client.crypto.vrf.EcVrfVerifier;
import com.bloxbean.cardano.client.crypto.vrf.VrfResult;
import com.bloxbean.cardano.client.crypto.vrf.bc.BcVrfProver;
import com.bloxbean.cardano.client.crypto.vrf.bc.BcVrfVerifier;
import com.bloxbean.cardano.client.crypto.vrf.cardano.CardanoLeaderCheck;
import com.bloxbean.cardano.client.crypto.vrf.cardano.CardanoVrfInput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end block production crypto pipeline test using devnet delegate keys.
 * <p>
 * Validates: VRF prove + verify, leader eligibility check, KES sign + verify.
 */
class BlockProductionCryptoTest {

    private static BlockProducerKeys keys;

    private final BcVrfProver vrfProver = new BcVrfProver();
    private final BcVrfVerifier bcVerifier = new BcVrfVerifier();
    private final EcVrfVerifier ecVerifier = new EcVrfVerifier();
    private final Sum6KesSigner kesSigner = new Sum6KesSigner();
    private final Sum6KesVerifier kesVerifier = new Sum6KesVerifier();

    @BeforeAll
    static void loadKeys() throws URISyntaxException {
        Path base = Paths.get(BlockProductionCryptoTest.class.getResource("/devnet").toURI());
        keys = BlockProducerKeys.load(
                base.resolve("delegate1.vrf.skey"),
                base.resolve("delegate1.kes.skey"),
                base.resolve("opcert1.cert")
        );

        assertEquals(64, keys.getVrfSkey().length, "VRF skey should be 64 bytes");
        assertEquals(608, keys.getKesSkey().length, "KES skey should be 608 bytes");
        assertNotNull(keys.getOpCert(), "OpCert should not be null");
    }

    @Test
    void vrfProveAndVerifyWithBothVerifiers() {
        long slot = 100;
        byte[] epochNonce = Blake2bUtil.blake2bHash256("devnet-nonce".getBytes());

        // Construct VRF input (Praos method)
        byte[] alpha = CardanoVrfInput.mkInputVrf(slot, epochNonce);
        assertEquals(32, alpha.length);

        // Prove with devnet VRF secret key
        byte[] vrfProof = vrfProver.prove(keys.getVrfSkey(), alpha);
        assertEquals(80, vrfProof.length, "VRF proof should be 80 bytes");

        // Extract VRF vkey from the skey (last 32 bytes)
        byte[] vrfVkey = Arrays.copyOfRange(keys.getVrfSkey(), 32, 64);

        // Verify with BcVrfVerifier
        VrfResult bcResult = bcVerifier.verify(vrfVkey, vrfProof, alpha);
        assertTrue(bcResult.isValid(), "Proof should verify with BcVrfVerifier");
        assertNotNull(bcResult.getOutput());
        assertEquals(64, bcResult.getOutput().length, "VRF output should be 64 bytes");

        // Verify with EcVrfVerifier
        VrfResult ecResult = ecVerifier.verify(vrfVkey, vrfProof, alpha);
        assertTrue(ecResult.isValid(), "Proof should verify with EcVrfVerifier");

        // Both verifiers must produce identical output
        assertArrayEquals(bcResult.getOutput(), ecResult.getOutput(),
                "BcVrfVerifier and EcVrfVerifier must produce identical VRF output");
    }

    @Test
    void leaderEligibilityWithFullStake() {
        long slot = 100;
        byte[] epochNonce = Blake2bUtil.blake2bHash256("devnet-nonce".getBytes());

        byte[] alpha = CardanoVrfInput.mkInputVrf(slot, epochNonce);
        byte[] vrfProof = vrfProver.prove(keys.getVrfSkey(), alpha);

        byte[] vrfVkey = Arrays.copyOfRange(keys.getVrfSkey(), 32, 64);
        VrfResult vrfResult = bcVerifier.verify(vrfVkey, vrfProof, alpha);
        assertTrue(vrfResult.isValid());

        // Derive leader value from VRF output
        byte[] leaderValue = CardanoLeaderCheck.vrfLeaderValue(vrfResult.getOutput());
        assertEquals(32, leaderValue.length, "Leader value should be 32 bytes");

        // With activeSlotCoeff=1.0, every slot has a leader (short-circuits to true)
        boolean eligible = CardanoLeaderCheck.checkLeaderValue(
                leaderValue, BigDecimal.ONE, BigDecimal.ONE);
        assertTrue(eligible, "Pool should always be eligible when activeSlotCoeff=1.0");

        // With realistic f=0.05, eligibility depends on the VRF output (~5% chance per slot)
        boolean realisticEligible = CardanoLeaderCheck.checkLeaderValue(
                leaderValue, BigDecimal.ONE, new BigDecimal("0.05"));
        // Just verify it returns a valid result without error (not asserting true/false)
    }

    @Test
    void verifyAndCheckLeaderEndToEnd() {
        long slot = 200;
        byte[] epochNonce = Blake2bUtil.blake2bHash256("devnet-nonce".getBytes());

        byte[] alpha = CardanoVrfInput.mkInputVrf(slot, epochNonce);
        byte[] vrfProof = vrfProver.prove(keys.getVrfSkey(), alpha);
        byte[] vrfVkey = Arrays.copyOfRange(keys.getVrfSkey(), 32, 64);

        // Use the combined verify + leader check method with f=1.0 (guaranteed eligible)
        boolean result = CardanoLeaderCheck.verifyAndCheckLeader(
                vrfVkey, vrfProof, slot, epochNonce, BigDecimal.ONE, BigDecimal.ONE);
        assertTrue(result, "verifyAndCheckLeader should pass with valid proof and f=1.0");
    }

    @Test
    void kesSignAndVerify() {
        // Mock a block header body hash
        byte[] headerBody = Blake2bUtil.blake2bHash256("block-header-body".getBytes());

        // Sign at period 0 (devnet initial key)
        int kesPeriod = 0;
        byte[] kesSig = kesSigner.sign(keys.getKesSkey(), headerBody, kesPeriod);
        assertEquals(448, kesSig.length, "KES signature should be 448 bytes");

        // Derive KES root verification key from the secret key
        byte[] kesRootVk = kesSigner.deriveVerificationKey(keys.getKesSkey());
        assertEquals(32, kesRootVk.length, "KES root vk should be 32 bytes");

        // Verify
        boolean verified = kesVerifier.verify(kesSig, headerBody, kesRootVk, kesPeriod);
        assertTrue(verified, "KES signature should verify at period 0");
    }

    @Test
    void kesSignAndVerifyAtDifferentPeriods() {
        byte[] headerBody = Blake2bUtil.blake2bHash256("block-header-body-2".getBytes());
        byte[] kesRootVk = kesSigner.deriveVerificationKey(keys.getKesSkey());

        // Sign and verify at several periods
        for (int period : new int[]{0, 1, 15, 31, 32, 63}) {
            byte[] sig = kesSigner.sign(keys.getKesSkey(), headerBody, period);
            boolean verified = kesVerifier.verify(sig, headerBody, kesRootVk, period);
            assertTrue(verified, "KES signature should verify at period " + period);
        }
    }

    @Test
    void opCertKesVkeyMatchesDerivedRootVk() {
        // The opcert's kesVkey is the hot key (raw Ed25519-like vk)
        byte[] opcertKesVk = keys.getOpCert().getKesVkey();
        assertEquals(32, opcertKesVk.length, "OpCert KES vkey should be 32 bytes");

        // The root vk derived from the skey is blake2b_256(lhs_vk || rhs_vk)
        byte[] derivedRootVk = kesSigner.deriveVerificationKey(keys.getKesSkey());
        assertEquals(32, derivedRootVk.length);

        // In a devnet setup, the opcert's hot key should match the derived root vk
        assertArrayEquals(opcertKesVk, derivedRootVk,
                "OpCert hot KES vkey should match root vk derived from KES skey");
    }

    @Test
    void fullBlockProductionPipeline() {
        // --- Step 1: VRF prove for slot leadership ---
        long slot = 42;
        byte[] epochNonce = Blake2bUtil.blake2bHash256("devnet-epoch-nonce".getBytes());
        byte[] alpha = CardanoVrfInput.mkInputVrf(slot, epochNonce);
        byte[] vrfProof = vrfProver.prove(keys.getVrfSkey(), alpha);

        byte[] vrfVkey = Arrays.copyOfRange(keys.getVrfSkey(), 32, 64);
        VrfResult vrfResult = bcVerifier.verify(vrfVkey, vrfProof, alpha);
        assertTrue(vrfResult.isValid(), "VRF proof must be valid");

        // --- Step 2: Leader eligibility (f=1.0 guarantees every slot has a leader) ---
        byte[] leaderValue = CardanoLeaderCheck.vrfLeaderValue(vrfResult.getOutput());
        boolean eligible = CardanoLeaderCheck.checkLeaderValue(
                leaderValue, BigDecimal.ONE, BigDecimal.ONE);
        assertTrue(eligible, "Must be eligible with f=1.0");

        // --- Step 3: Nonce contribution ---
        byte[] nonceValue = CardanoLeaderCheck.vrfNonceValue(vrfResult.getOutput());
        assertEquals(32, nonceValue.length, "Nonce value should be 32 bytes");

        // --- Step 4: KES sign the block header ---
        byte[] headerBodyHash = Blake2bUtil.blake2bHash256("mock-block-header".getBytes());
        int kesPeriod = 0;
        byte[] kesSig = kesSigner.sign(keys.getKesSkey(), headerBodyHash, kesPeriod);

        byte[] kesRootVk = kesSigner.deriveVerificationKey(keys.getKesSkey());
        boolean kesValid = kesVerifier.verify(kesSig, headerBodyHash, kesRootVk, kesPeriod);
        assertTrue(kesValid, "KES signature must verify");

        // --- Step 5: Cross-verify VRF with EcVrfVerifier ---
        VrfResult ecResult = ecVerifier.verify(vrfVkey, vrfProof, alpha);
        assertTrue(ecResult.isValid(), "EcVrfVerifier must also validate the proof");
        assertArrayEquals(vrfResult.getOutput(), ecResult.getOutput(),
                "Both verifiers must produce identical output");
    }
}
