package com.bloxbean.cardano.client.crypto.vrf.cardano;

import com.bloxbean.cardano.client.crypto.vrf.EcVrfVerifier;
import com.bloxbean.cardano.client.crypto.vrf.VrfResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

class CardanoLeaderCheckTest {

    private static final BigDecimal MAINNET_F = new BigDecimal("0.05"); // mainnet active slot coefficient

    // --- vrfLeaderValue / vrfNonceValue tests ---

    @Test
    void vrfLeaderValue_and_vrfNonceValue_produceDifferentHashes() {
        byte[] vrfOutput = new byte[64];
        for (int i = 0; i < 64; i++) vrfOutput[i] = (byte) i;

        byte[] leaderVal = CardanoLeaderCheck.vrfLeaderValue(vrfOutput);
        byte[] nonceVal = CardanoLeaderCheck.vrfNonceValue(vrfOutput);

        assertEquals(32, leaderVal.length);
        assertEquals(32, nonceVal.length);
        assertFalse(java.util.Arrays.equals(leaderVal, nonceVal),
                "Leader and nonce values must differ for same VRF output");
    }

    @Test
    void vrfLeaderValue_deterministicForSameInput() {
        byte[] vrfOutput = new byte[64];
        vrfOutput[0] = (byte) 0xFF;

        byte[] result1 = CardanoLeaderCheck.vrfLeaderValue(vrfOutput);
        byte[] result2 = CardanoLeaderCheck.vrfLeaderValue(vrfOutput);

        assertArrayEquals(result1, result2);
    }

    @Test
    void vrfNonceValue_deterministicForSameInput() {
        byte[] vrfOutput = new byte[64];
        vrfOutput[63] = (byte) 0xAB;

        byte[] result1 = CardanoLeaderCheck.vrfNonceValue(vrfOutput);
        byte[] result2 = CardanoLeaderCheck.vrfNonceValue(vrfOutput);

        assertArrayEquals(result1, result2);
    }

    @Test
    void vrfLeaderValue_rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> CardanoLeaderCheck.vrfLeaderValue(null));
    }

    @Test
    void vrfNonceValue_rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> CardanoLeaderCheck.vrfNonceValue(null));
    }

    // --- checkLeaderValue boundary tests ---

    @Test
    void checkLeaderValue_fullStake_alwaysEligible() {
        // sigma=1.0 means threshold = 1 - (1 - f)^1 = f
        // For f=0.05, threshold = 0.05, certNatMax * 0.05 is ~5% of range
        // A zero leaderHash (certNat=0) should always be eligible
        byte[] zeroHash = new byte[32]; // certNat = 0
        assertTrue(CardanoLeaderCheck.checkLeaderValue(zeroHash, BigDecimal.ONE, MAINNET_F));
    }

    @Test
    void checkLeaderValue_zeroStake_neverEligible() {
        // sigma=0.0 means threshold = 1 - (1 - f)^0 = 1 - 1 = 0
        // No certNat can be < 0, so never eligible
        byte[] anyHash = new byte[32];
        anyHash[0] = 1;
        assertFalse(CardanoLeaderCheck.checkLeaderValue(anyHash, BigDecimal.ZERO, MAINNET_F));
    }

    @Test
    void checkLeaderValue_zeroStake_evenZeroHash() {
        // sigma=0, threshold=0 => certNat=0 is NOT < 0
        byte[] zeroHash = new byte[32];
        assertFalse(CardanoLeaderCheck.checkLeaderValue(zeroHash, BigDecimal.ZERO, MAINNET_F));
    }

    @Test
    void checkLeaderValue_maxHash_fullStake_notEligible() {
        // Max hash (all 0xFF) = 2^256 - 1, threshold for sigma=1 f=0.05 is ~0.05 * 2^256
        // 2^256 - 1 >> 0.05 * 2^256, so not eligible
        byte[] maxHash = new byte[32];
        java.util.Arrays.fill(maxHash, (byte) 0xFF);
        assertFalse(CardanoLeaderCheck.checkLeaderValue(maxHash, BigDecimal.ONE, MAINNET_F));
    }

    @Test
    void checkLeaderValue_knownThresholdComparison() {
        // For sigma=0.001 (0.1% stake), f=0.05
        // threshold = 1 - (1-0.05)^0.001 = 1 - 0.95^0.001
        // 0.95^0.001 ≈ exp(0.001 * ln(0.95)) ≈ exp(0.001 * (-0.05129)) ≈ exp(-0.00005129)
        // ≈ 1 - 0.00005129 ≈ 0.99994871
        // threshold ≈ 0.00005129
        // certNatMax * threshold ≈ 2^256 * 5.129e-5 ≈ 5.93e72

        BigDecimal sigma = new BigDecimal("0.001");

        // A very small certNat should be eligible
        byte[] smallHash = new byte[32];
        smallHash[31] = 1; // certNat = 1
        assertTrue(CardanoLeaderCheck.checkLeaderValue(smallHash, sigma, MAINNET_F));

        // A very large certNat (close to max) should NOT be eligible
        byte[] largeHash = new byte[32];
        java.util.Arrays.fill(largeHash, (byte) 0xFF);
        assertFalse(CardanoLeaderCheck.checkLeaderValue(largeHash, sigma, MAINNET_F));
    }

    @Test
    void checkLeaderValue_activeSlotCoeff1_alwaysEligible() {
        // f=1.0 means every slot has a leader
        // threshold = 1 - (1-1)^sigma = 1 - 0 = 1 (for any sigma > 0)
        // certNat < certNatMax * 1 is always true for valid certNat
        byte[] anyHash = new byte[32];
        anyHash[0] = (byte) 0xFE; // large but < 2^256
        assertTrue(CardanoLeaderCheck.checkLeaderValue(
                anyHash, new BigDecimal("0.001"), BigDecimal.ONE));
    }

    @Test
    void checkLeaderValue_64byteHash_tpraos() {
        // TPraos uses 64-byte (512-bit) leader hashes
        // sigma=1.0, f=0.05 => threshold ~0.05
        // Zero hash should be eligible
        byte[] zeroHash = new byte[64];
        assertTrue(CardanoLeaderCheck.checkLeaderValue(zeroHash, BigDecimal.ONE, MAINNET_F));

        // Max hash should not be eligible
        byte[] maxHash = new byte[64];
        java.util.Arrays.fill(maxHash, (byte) 0xFF);
        assertFalse(CardanoLeaderCheck.checkLeaderValue(maxHash, BigDecimal.ONE, MAINNET_F));
    }

    @Test
    void checkLeaderValue_rejectsNullHash() {
        assertThrows(IllegalArgumentException.class,
                () -> CardanoLeaderCheck.checkLeaderValue(null, BigDecimal.ONE, MAINNET_F));
    }

    @Test
    void checkLeaderValue_rejectsEmptyHash() {
        assertThrows(IllegalArgumentException.class,
                () -> CardanoLeaderCheck.checkLeaderValue(new byte[0], BigDecimal.ONE, MAINNET_F));
    }

    @Test
    void checkLeaderValue_rejectsNegativeSigma() {
        assertThrows(IllegalArgumentException.class,
                () -> CardanoLeaderCheck.checkLeaderValue(new byte[32], new BigDecimal("-0.1"), MAINNET_F));
    }

    @Test
    void checkLeaderValue_rejectsSigmaGreaterThanOne() {
        assertThrows(IllegalArgumentException.class,
                () -> CardanoLeaderCheck.checkLeaderValue(new byte[32], new BigDecimal("1.1"), MAINNET_F));
    }

    @Test
    void checkLeaderValue_rejectsZeroActiveSlotCoeff() {
        assertThrows(IllegalArgumentException.class,
                () -> CardanoLeaderCheck.checkLeaderValue(new byte[32], BigDecimal.ONE, BigDecimal.ZERO));
    }

    // --- ln / exp internal precision tests ---

    @Test
    void ln_accuracy() {
        MathContext mc = new MathContext(40, RoundingMode.HALF_EVEN);
        // ln(0.95) should be approximately -0.051293294387550044...
        BigDecimal result = CardanoLeaderCheck.ln(new BigDecimal("0.95"), mc);
        BigDecimal expected = new BigDecimal("-0.05129329438755004387");
        // Check first 15 digits match
        assertTrue(result.subtract(expected).abs().compareTo(new BigDecimal("1E-15")) < 0,
                "ln(0.95) = " + result + " should be close to " + expected);
    }

    @Test
    void exp_accuracy() {
        MathContext mc = new MathContext(40, RoundingMode.HALF_EVEN);
        // exp(-0.05) should be approximately 0.951229424500714...
        BigDecimal result = CardanoLeaderCheck.exp(new BigDecimal("-0.05"), mc);
        BigDecimal expected = new BigDecimal("0.95122942450071402");
        assertTrue(result.subtract(expected).abs().compareTo(new BigDecimal("1E-15")) < 0,
                "exp(-0.05) = " + result + " should be close to " + expected);
    }

    // --- verifyAndCheckLeader tests ---

    @Test
    void verifyAndCheckLeader_invalidProof_returnsFalse() {
        byte[] fakeVkey = new byte[32];
        byte[] fakeProof = new byte[80];
        byte[] epochNonce = new byte[32];

        // Invalid VRF proof should cause verification to fail
        assertFalse(CardanoLeaderCheck.verifyAndCheckLeader(
                fakeVkey, fakeProof, 100, epochNonce,
                BigDecimal.ONE, MAINNET_F));
    }

    @Test
    void verifyAndCheckLeader_withRealVrfData() {
        // Test using known IETF test vector 1 VRF key/proof
        // Note: This uses a synthetic alpha (from mkInputVrf) which won't match
        // the test vector's alpha, so VRF verification will correctly fail.
        // This test ensures the method handles VRF failure gracefully.
        byte[] pk = hexToBytes("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");
        byte[] pi = hexToBytes(
                "b6b4699f87d56126c9117a7da55bd0085246f4c56dbc95d20172612e9d38e8d7"
                        + "ca65e573a126ed88d4e30a46f80a666854d675cf3ba81de0de043c3774f06156"
                        + "0f55edc256a787afe701677c0f602900");
        byte[] epochNonce = new byte[32];

        // The alpha for slot 0 + zero nonce won't match the empty alpha from the test vector,
        // so VRF verify returns invalid
        assertFalse(CardanoLeaderCheck.verifyAndCheckLeader(
                pk, pi, 0, epochNonce, BigDecimal.ONE, MAINNET_F));
    }

    // ========================================================================
    // Real mainnet block data for verification tests
    // ========================================================================

    // --- Block #10000000 (epoch 470, slot 117736136) ---
    // Pool: pool1llxh8l0h8g9ghz3nrzh7ndvev4x43vnk72nsemzm795vxqs6dp8
    //   pool stake: 17,979,009,392,314 lovelace
    private static final long BLOCK_10M_SLOT = 117736136L;
    private static final String BLOCK_10M_VRF_VKEY =
            "5ebd45dfdf4ee76829d195ec24771904ee3947387f3e65005deb2b7ceab393c0";
    private static final String BLOCK_10M_VRF_OUTPUT =
            "8eef20bfce43e2a0a64d53cb31af230862509ac2aa9536613cf8102039564fad"
                    + "3563236e7f5e5d1cf52eafb017b9f8e63b511ede8c798fd49c766b37bf0e054e";
    private static final String BLOCK_10M_VRF_PROOF =
            "4dd5f5e34a33a4e162cd1957a95a471c8985710da24d1bfd0cbc95d909dbaa33"
                    + "b35680a86d4dc6f64348ef60db5bdd323809fccfd4d4f480cd4d5feae4d59f6b"
                    + "7d7ec2e9faa2bc8873d3963981075703";
    // sigma = pool_stake / total_active_stake (epoch 470)
    private static final BigDecimal BLOCK_10M_SIGMA = new BigDecimal("17979009392314")
            .divide(new BigDecimal("22861134936826292"), 40, RoundingMode.HALF_EVEN);

    // --- Block #9999999 (epoch 470, slot 117736127) ---
    // Pool: pool16ajaae2n5lsyr4f9k9uz5y8tpf0996tw640dzu7chwp2wdrnz6a
    //   pool stake: 72,296,787,733,437 lovelace
    private static final long BLOCK_9999999_SLOT = 117736127L;
    private static final String BLOCK_9999999_VRF_VKEY =
            "7e7c72b092eda07fc40f400c2bc3db9be26955616af82fc59efb31ef372334f6";
    private static final String BLOCK_9999999_VRF_OUTPUT =
            "4c53f925442c209ce845aab32eec2aefede507c1289604dd4c3df8a86ea1571e"
                    + "41b2762c0923e1d84c6d957f0ad7cbf415f2fdefc31d69c8c044362ed4772eb8";
    private static final String BLOCK_9999999_VRF_PROOF =
            "a230cc32ffc4b8ea02631f036192651093b3d3d6e79c04df4fbe589baae1151c"
                    + "72582a2af27253d2a63491d4a7ae486fae7d294064cffa61f96ef3d67911e3c3"
                    + "ad4a0f782489ed3107ce76817c4f6d05";
    private static final BigDecimal BLOCK_9999999_SIGMA = new BigDecimal("72296787733437")
            .divide(new BigDecimal("22861134936826292"), 40, RoundingMode.HALF_EVEN);

    // --- Block #10500000 (epoch 493, slot 127930168) ---
    // Pool: pool1lev7ygxcqd4aw5em7wf7y64au4hv04yl5cew27lkjmgngj46vqk
    //   pool stake: 70,092,474,920,174 lovelace
    private static final long BLOCK_10500K_SLOT = 127930168L;
    private static final String BLOCK_10500K_VRF_VKEY =
            "10398bd8b2158543f15d2fac143c26ec3e9df9537ec657f397507a38962de9ef";
    private static final String BLOCK_10500K_VRF_OUTPUT =
            "b0530b13345ada87203b3455e6873f51ffe60cdcbd7d5005a6a4038173d30341"
                    + "a67b5fa237824c719a27b14b8c6cb19b32755514db8e5b32b4e82f795dfb5c1b";
    private static final String BLOCK_10500K_VRF_PROOF =
            "11c04a33d751b997a5c5e2f59175103c4b2b61678ad4586b08bddc63d3261c8e"
                    + "f2aa74db86ab328dd7ac6cfcd08a3346272d0ff5f00dac215b263ed3223c86a4"
                    + "2be54018f6f7bf66dc1aa169530e8e02";
    private static final BigDecimal BLOCK_10500K_SIGMA = new BigDecimal("70092474920174")
            .divide(new BigDecimal("22744116768308459"), 40, RoundingMode.HALF_EVEN);

    // --- Epoch nonces ---
    private static final String EPOCH_470_NONCE =
            "aa022d10f8a29863795ff14c4e82570d1db8906f1b3fd8a90fe69b699a4398d9";
    private static final String EPOCH_493_NONCE =
            "156e3efa3ac557cbc420fca54683b01627cf83e26dea37065057097322d03e62";

    // ========================================================================
    // Positive: VRF verification with real mainnet blocks
    // ========================================================================

    @Test
    void mainnetBlock10M_vrfVerificationSucceeds() {
        byte[] alpha = CardanoVrfInput.mkInputVrf(BLOCK_10M_SLOT, hexToBytes(EPOCH_470_NONCE));
        EcVrfVerifier verifier = new EcVrfVerifier();
        VrfResult result = verifier.verify(
                hexToBytes(BLOCK_10M_VRF_VKEY), hexToBytes(BLOCK_10M_VRF_PROOF), alpha);

        assertTrue(result.isValid(), "VRF proof for mainnet block #10000000 must verify");
        assertEquals(64, result.getOutput().length);
    }

    @Test
    void mainnetBlock10M_vrfOutputMatchesBlockHeader() {
        byte[] alpha = CardanoVrfInput.mkInputVrf(BLOCK_10M_SLOT, hexToBytes(EPOCH_470_NONCE));
        EcVrfVerifier verifier = new EcVrfVerifier();
        VrfResult result = verifier.verify(
                hexToBytes(BLOCK_10M_VRF_VKEY), hexToBytes(BLOCK_10M_VRF_PROOF), alpha);

        assertArrayEquals(hexToBytes(BLOCK_10M_VRF_OUTPUT), result.getOutput(),
                "VRF output must match block header's vrf_result.output");
    }

    @Test
    void mainnetBlock10M_leaderEligibilityCheck() {
        byte[] leaderValue = CardanoLeaderCheck.vrfLeaderValue(hexToBytes(BLOCK_10M_VRF_OUTPUT));
        assertTrue(CardanoLeaderCheck.checkLeaderValue(leaderValue, BLOCK_10M_SIGMA, MAINNET_F),
                "Block #10000000 pool must be eligible");
    }

    @Test
    void mainnetBlock10M_endToEndVerifyAndCheck() {
        assertTrue(CardanoLeaderCheck.verifyAndCheckLeader(
                hexToBytes(BLOCK_10M_VRF_VKEY), hexToBytes(BLOCK_10M_VRF_PROOF),
                BLOCK_10M_SLOT, hexToBytes(EPOCH_470_NONCE),
                BLOCK_10M_SIGMA, MAINNET_F),
                "End-to-end for block #10000000 must pass");
    }

    @Test
    void mainnetBlock9999999_endToEndVerifyAndCheck() {
        // Second block from same epoch — independent validation
        assertTrue(CardanoLeaderCheck.verifyAndCheckLeader(
                hexToBytes(BLOCK_9999999_VRF_VKEY), hexToBytes(BLOCK_9999999_VRF_PROOF),
                BLOCK_9999999_SLOT, hexToBytes(EPOCH_470_NONCE),
                BLOCK_9999999_SIGMA, MAINNET_F),
                "End-to-end for block #9999999 must pass");
    }

    @Test
    void mainnetBlock9999999_vrfOutputMatchesBlockHeader() {
        byte[] alpha = CardanoVrfInput.mkInputVrf(BLOCK_9999999_SLOT, hexToBytes(EPOCH_470_NONCE));
        EcVrfVerifier verifier = new EcVrfVerifier();
        VrfResult result = verifier.verify(
                hexToBytes(BLOCK_9999999_VRF_VKEY), hexToBytes(BLOCK_9999999_VRF_PROOF), alpha);

        assertTrue(result.isValid());
        assertArrayEquals(hexToBytes(BLOCK_9999999_VRF_OUTPUT), result.getOutput(),
                "VRF output must match block #9999999 header");
    }

    @Test
    void mainnetBlock10500K_endToEndVerifyAndCheck() {
        // Third block from different epoch (493) — validates epoch nonce handling
        assertTrue(CardanoLeaderCheck.verifyAndCheckLeader(
                hexToBytes(BLOCK_10500K_VRF_VKEY), hexToBytes(BLOCK_10500K_VRF_PROOF),
                BLOCK_10500K_SLOT, hexToBytes(EPOCH_493_NONCE),
                BLOCK_10500K_SIGMA, MAINNET_F),
                "End-to-end for block #10500000 (epoch 493) must pass");
    }

    @Test
    void mainnetBlock10500K_vrfOutputMatchesBlockHeader() {
        byte[] alpha = CardanoVrfInput.mkInputVrf(BLOCK_10500K_SLOT, hexToBytes(EPOCH_493_NONCE));
        EcVrfVerifier verifier = new EcVrfVerifier();
        VrfResult result = verifier.verify(
                hexToBytes(BLOCK_10500K_VRF_VKEY), hexToBytes(BLOCK_10500K_VRF_PROOF), alpha);

        assertTrue(result.isValid());
        assertArrayEquals(hexToBytes(BLOCK_10500K_VRF_OUTPUT), result.getOutput(),
                "VRF output must match block #10500000 header");
    }

    // ========================================================================
    // Negative: Wrong VRF key (cross-pool key swap)
    // ========================================================================

    @Test
    void negative_wrongVrfKey_block10M_keyFromBlock9999999() {
        // Use block #10000000's proof but block #9999999's VRF key
        byte[] alpha = CardanoVrfInput.mkInputVrf(BLOCK_10M_SLOT, hexToBytes(EPOCH_470_NONCE));
        EcVrfVerifier verifier = new EcVrfVerifier();
        VrfResult result = verifier.verify(
                hexToBytes(BLOCK_9999999_VRF_VKEY), hexToBytes(BLOCK_10M_VRF_PROOF), alpha);

        assertFalse(result.isValid(),
                "VRF proof must fail when verified against a different pool's key");
    }

    @Test
    void negative_wrongVrfKey_endToEnd() {
        // End-to-end: block #9999999's key + block #10000000's proof → must fail
        assertFalse(CardanoLeaderCheck.verifyAndCheckLeader(
                hexToBytes(BLOCK_9999999_VRF_VKEY), hexToBytes(BLOCK_10M_VRF_PROOF),
                BLOCK_10M_SLOT, hexToBytes(EPOCH_470_NONCE),
                BLOCK_10M_SIGMA, MAINNET_F),
                "Cross-pool key swap must fail end-to-end");
    }

    // ========================================================================
    // Negative: Wrong slot (off-by-one and cross-block)
    // ========================================================================

    @Test
    void negative_wrongSlot_offByOne() {
        // Block #10000000's proof verified at slot+1 → wrong alpha → VRF fails
        byte[] alpha = CardanoVrfInput.mkInputVrf(BLOCK_10M_SLOT + 1, hexToBytes(EPOCH_470_NONCE));
        EcVrfVerifier verifier = new EcVrfVerifier();
        VrfResult result = verifier.verify(
                hexToBytes(BLOCK_10M_VRF_VKEY), hexToBytes(BLOCK_10M_VRF_PROOF), alpha);

        assertFalse(result.isValid(), "Off-by-one slot must invalidate VRF proof");
    }

    @Test
    void negative_wrongSlot_offByMinusOne() {
        byte[] alpha = CardanoVrfInput.mkInputVrf(BLOCK_10M_SLOT - 1, hexToBytes(EPOCH_470_NONCE));
        EcVrfVerifier verifier = new EcVrfVerifier();
        VrfResult result = verifier.verify(
                hexToBytes(BLOCK_10M_VRF_VKEY), hexToBytes(BLOCK_10M_VRF_PROOF), alpha);

        assertFalse(result.isValid(), "Off-by-minus-one slot must invalidate VRF proof");
    }

    @Test
    void negative_wrongSlot_anotherBlocksSlot() {
        // Block #10000000's key/proof verified at block #9999999's slot
        byte[] alpha = CardanoVrfInput.mkInputVrf(BLOCK_9999999_SLOT, hexToBytes(EPOCH_470_NONCE));
        EcVrfVerifier verifier = new EcVrfVerifier();
        VrfResult result = verifier.verify(
                hexToBytes(BLOCK_10M_VRF_VKEY), hexToBytes(BLOCK_10M_VRF_PROOF), alpha);

        assertFalse(result.isValid(),
                "Proof from one slot must not verify at another slot");
    }

    // ========================================================================
    // Negative: Wrong epoch nonce
    // ========================================================================

    @Test
    void negative_wrongEpochNonce_crossEpoch() {
        // Block #10000000 (epoch 470) proof with epoch 493's nonce
        byte[] alpha = CardanoVrfInput.mkInputVrf(BLOCK_10M_SLOT, hexToBytes(EPOCH_493_NONCE));
        EcVrfVerifier verifier = new EcVrfVerifier();
        VrfResult result = verifier.verify(
                hexToBytes(BLOCK_10M_VRF_VKEY), hexToBytes(BLOCK_10M_VRF_PROOF), alpha);

        assertFalse(result.isValid(),
                "Proof from epoch 470 must fail with epoch 493's nonce");
    }

    @Test
    void negative_wrongEpochNonce_endToEnd() {
        // End-to-end: block #10500000 with epoch 470 nonce (should use 493)
        assertFalse(CardanoLeaderCheck.verifyAndCheckLeader(
                hexToBytes(BLOCK_10500K_VRF_VKEY), hexToBytes(BLOCK_10500K_VRF_PROOF),
                BLOCK_10500K_SLOT, hexToBytes(EPOCH_470_NONCE),
                BLOCK_10500K_SIGMA, MAINNET_F),
                "Block from epoch 493 must fail with epoch 470 nonce");
    }

    @Test
    void negative_wrongEpochNonce_zeroNonce() {
        byte[] zeroNonce = new byte[32];
        byte[] alpha = CardanoVrfInput.mkInputVrf(BLOCK_10M_SLOT, zeroNonce);
        EcVrfVerifier verifier = new EcVrfVerifier();
        VrfResult result = verifier.verify(
                hexToBytes(BLOCK_10M_VRF_VKEY), hexToBytes(BLOCK_10M_VRF_PROOF), alpha);

        assertFalse(result.isValid(), "Zero nonce must invalidate VRF proof");
    }

    // ========================================================================
    // Negative: Bit-flipped proof and key
    // ========================================================================

    @Test
    void negative_bitFlippedProof_firstByte() {
        byte[] proof = hexToBytes(BLOCK_10M_VRF_PROOF);
        proof[0] ^= 0x01; // flip LSB of first byte

        byte[] alpha = CardanoVrfInput.mkInputVrf(BLOCK_10M_SLOT, hexToBytes(EPOCH_470_NONCE));
        EcVrfVerifier verifier = new EcVrfVerifier();
        VrfResult result = verifier.verify(hexToBytes(BLOCK_10M_VRF_VKEY), proof, alpha);

        assertFalse(result.isValid(), "Flipping first byte of proof must invalidate it");
    }

    @Test
    void negative_bitFlippedProof_lastByte() {
        byte[] proof = hexToBytes(BLOCK_10M_VRF_PROOF);
        proof[proof.length - 1] ^= 0x01; // flip LSB of last byte

        byte[] alpha = CardanoVrfInput.mkInputVrf(BLOCK_10M_SLOT, hexToBytes(EPOCH_470_NONCE));
        EcVrfVerifier verifier = new EcVrfVerifier();
        VrfResult result = verifier.verify(hexToBytes(BLOCK_10M_VRF_VKEY), proof, alpha);

        assertFalse(result.isValid(), "Flipping last byte of proof must invalidate it");
    }

    @Test
    void negative_bitFlippedProof_middleByte() {
        byte[] proof = hexToBytes(BLOCK_10M_VRF_PROOF);
        proof[40] ^= 0x80; // flip MSB of middle byte

        byte[] alpha = CardanoVrfInput.mkInputVrf(BLOCK_10M_SLOT, hexToBytes(EPOCH_470_NONCE));
        EcVrfVerifier verifier = new EcVrfVerifier();
        VrfResult result = verifier.verify(hexToBytes(BLOCK_10M_VRF_VKEY), proof, alpha);

        assertFalse(result.isValid(), "Flipping middle byte of proof must invalidate it");
    }

    @Test
    void negative_bitFlippedVrfKey() {
        byte[] vkey = hexToBytes(BLOCK_10M_VRF_VKEY);
        vkey[0] ^= 0x01; // flip one bit

        byte[] alpha = CardanoVrfInput.mkInputVrf(BLOCK_10M_SLOT, hexToBytes(EPOCH_470_NONCE));
        EcVrfVerifier verifier = new EcVrfVerifier();
        VrfResult result = verifier.verify(vkey, hexToBytes(BLOCK_10M_VRF_PROOF), alpha);

        assertFalse(result.isValid(), "Flipping one bit in VRF key must invalidate proof");
    }

    // ========================================================================
    // Negative: Cross-pool sigma (wrong stake)
    // ========================================================================

    @Test
    void negative_tinyPoolStake_leaderCheckFails() {
        // Use real block #10000000 VRF output, but pretend pool has only 500k lovelace
        // sigma = 500000 / 22861134936826292 ≈ 2.19e-8
        BigDecimal tinySigma = new BigDecimal("500000")
                .divide(new BigDecimal("22861134936826292"), 40, RoundingMode.HALF_EVEN);

        byte[] leaderValue = CardanoLeaderCheck.vrfLeaderValue(hexToBytes(BLOCK_10M_VRF_OUTPUT));
        assertFalse(CardanoLeaderCheck.checkLeaderValue(leaderValue, tinySigma, MAINNET_F),
                "A pool with ~0.000002% stake should not be eligible for this block");
    }

    @Test
    void negative_tinyPoolStake_endToEnd() {
        BigDecimal tinySigma = new BigDecimal("500000")
                .divide(new BigDecimal("22861134936826292"), 40, RoundingMode.HALF_EVEN);

        assertFalse(CardanoLeaderCheck.verifyAndCheckLeader(
                hexToBytes(BLOCK_10M_VRF_VKEY), hexToBytes(BLOCK_10M_VRF_PROOF),
                BLOCK_10M_SLOT, hexToBytes(EPOCH_470_NONCE),
                tinySigma, MAINNET_F),
                "End-to-end with tiny sigma must fail the leader check");
    }

    @Test
    void negative_sigmaMonotonicity_largerSigmaKeepsEligibility() {
        // checkLeaderValue is monotonically increasing with sigma:
        // if eligible at sigma_small, must also be eligible at sigma_large.
        // Verify this property with block #10000000's real leader value.
        byte[] leaderValue = CardanoLeaderCheck.vrfLeaderValue(hexToBytes(BLOCK_10M_VRF_OUTPUT));

        assertTrue(CardanoLeaderCheck.checkLeaderValue(leaderValue, BLOCK_10M_SIGMA, MAINNET_F),
                "Block #10000000 must be eligible with its own sigma");
        // 4x larger sigma must also be eligible
        BigDecimal largerSigma = BLOCK_10M_SIGMA.multiply(BigDecimal.valueOf(4));
        assertTrue(CardanoLeaderCheck.checkLeaderValue(leaderValue, largerSigma, MAINNET_F),
                "4x larger sigma must also be eligible");
    }

    @Test
    void negative_sigmaMonotonicity_smallerSigmaFlipsEligibility() {
        // Reduce sigma until the pool is no longer eligible.
        // Block #10000000's pool has sigma ≈ 0.000786. Dividing by 1000 gives ≈ 7.86e-7.
        // With such a tiny sigma, the threshold becomes negligible and the block should fail.
        byte[] leaderValue = CardanoLeaderCheck.vrfLeaderValue(hexToBytes(BLOCK_10M_VRF_OUTPUT));

        assertTrue(CardanoLeaderCheck.checkLeaderValue(leaderValue, BLOCK_10M_SIGMA, MAINNET_F),
                "Must be eligible with real sigma");

        BigDecimal muchSmallerSigma = BLOCK_10M_SIGMA.divide(BigDecimal.valueOf(1000), 40, RoundingMode.HALF_EVEN);
        assertFalse(CardanoLeaderCheck.checkLeaderValue(leaderValue, muchSmallerSigma, MAINNET_F),
                "1/1000th of real sigma must flip eligibility to false");
    }

    // ========================================================================
    // Negative: Swapped proof between blocks (same epoch, different slots)
    // ========================================================================

    @Test
    void negative_swappedProof_block10M_proofAtBlock9999999Slot() {
        // Block #10000000's key+proof used at block #9999999's slot
        assertFalse(CardanoLeaderCheck.verifyAndCheckLeader(
                hexToBytes(BLOCK_10M_VRF_VKEY), hexToBytes(BLOCK_10M_VRF_PROOF),
                BLOCK_9999999_SLOT, hexToBytes(EPOCH_470_NONCE),
                BLOCK_10M_SIGMA, MAINNET_F),
                "Proof from slot 117736136 must not verify at slot 117736127");
    }

    @Test
    void negative_swappedProof_block9999999_proofAtBlock10MSlot() {
        // Block #9999999's key+proof used at block #10000000's slot
        assertFalse(CardanoLeaderCheck.verifyAndCheckLeader(
                hexToBytes(BLOCK_9999999_VRF_VKEY), hexToBytes(BLOCK_9999999_VRF_PROOF),
                BLOCK_10M_SLOT, hexToBytes(EPOCH_470_NONCE),
                BLOCK_9999999_SIGMA, MAINNET_F),
                "Proof from slot 117736127 must not verify at slot 117736136");
    }

    // ========================================================================
    // Negative: Cross-epoch proof swap (different epoch, different slot)
    // ========================================================================

    @Test
    void negative_crossEpochSwap_block10M_proofWithEpoch493Params() {
        // Block #10000000 (epoch 470) proof verified with epoch 493's nonce and slot
        assertFalse(CardanoLeaderCheck.verifyAndCheckLeader(
                hexToBytes(BLOCK_10M_VRF_VKEY), hexToBytes(BLOCK_10M_VRF_PROOF),
                BLOCK_10500K_SLOT, hexToBytes(EPOCH_493_NONCE),
                BLOCK_10M_SIGMA, MAINNET_F),
                "Epoch 470 proof must fail with epoch 493 slot+nonce");
    }

    @Test
    void negative_crossEpochSwap_block10500K_proofWithEpoch470Params() {
        // Block #10500000 (epoch 493) proof verified with epoch 470's nonce at block 10M's slot
        assertFalse(CardanoLeaderCheck.verifyAndCheckLeader(
                hexToBytes(BLOCK_10500K_VRF_VKEY), hexToBytes(BLOCK_10500K_VRF_PROOF),
                BLOCK_10M_SLOT, hexToBytes(EPOCH_470_NONCE),
                BLOCK_10500K_SIGMA, MAINNET_F),
                "Epoch 493 proof must fail with epoch 470 slot+nonce");
    }

    // ========================================================================
    // Edge cases: nonce value extraction
    // ========================================================================

    @Test
    void mainnetBlock10M_nonceValueExtraction() {
        byte[] vrfOutput = hexToBytes(BLOCK_10M_VRF_OUTPUT);
        byte[] nonceValue = CardanoLeaderCheck.vrfNonceValue(vrfOutput);
        byte[] leaderValue = CardanoLeaderCheck.vrfLeaderValue(vrfOutput);

        assertEquals(32, nonceValue.length);
        assertFalse(java.util.Arrays.equals(leaderValue, nonceValue),
                "Leader and nonce values must differ for same VRF output");
    }

    @Test
    void differentBlocks_produceDifferentLeaderAndNonceValues() {
        // Each block's VRF output should produce unique leader/nonce values
        byte[] leader10M = CardanoLeaderCheck.vrfLeaderValue(hexToBytes(BLOCK_10M_VRF_OUTPUT));
        byte[] leader9999999 = CardanoLeaderCheck.vrfLeaderValue(hexToBytes(BLOCK_9999999_VRF_OUTPUT));
        byte[] leader10500K = CardanoLeaderCheck.vrfLeaderValue(hexToBytes(BLOCK_10500K_VRF_OUTPUT));

        assertFalse(java.util.Arrays.equals(leader10M, leader9999999),
                "Block #10000000 and #9999999 must have different leader values");
        assertFalse(java.util.Arrays.equals(leader10M, leader10500K),
                "Block #10000000 and #10500000 must have different leader values");
        assertFalse(java.util.Arrays.equals(leader9999999, leader10500K),
                "Block #9999999 and #10500000 must have different leader values");
    }

    // ========================================================================
    // Edge case: slot 0 and max slot
    // ========================================================================

    @Test
    void negative_slotZero_realProof() {
        // Real proof from block #10000000 verified at slot 0
        byte[] alpha = CardanoVrfInput.mkInputVrf(0, hexToBytes(EPOCH_470_NONCE));
        EcVrfVerifier verifier = new EcVrfVerifier();
        VrfResult result = verifier.verify(
                hexToBytes(BLOCK_10M_VRF_VKEY), hexToBytes(BLOCK_10M_VRF_PROOF), alpha);

        assertFalse(result.isValid(), "Real proof must not verify at slot 0");
    }

    @Test
    void negative_maxSlot_realProof() {
        byte[] alpha = CardanoVrfInput.mkInputVrf(Long.MAX_VALUE, hexToBytes(EPOCH_470_NONCE));
        EcVrfVerifier verifier = new EcVrfVerifier();
        VrfResult result = verifier.verify(
                hexToBytes(BLOCK_10M_VRF_VKEY), hexToBytes(BLOCK_10M_VRF_PROOF), alpha);

        assertFalse(result.isValid(), "Real proof must not verify at Long.MAX_VALUE slot");
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
