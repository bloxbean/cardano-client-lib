package com.bloxbean.cardano.client.crypto.kes;

import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Sum6KesVerifier using test vectors from IOG's kes-summed-ed25519 crate.
 * <p>
 * Test data generated from Haskell cardano-base with:
 * - Seed: "test string of 32 byte of lenght" (ASCII, 32 bytes)
 * - Message: "test message" (ASCII)
 */
class Sum6KesVerifierTest {

    private static final Sum6KesVerifier verifier = new Sum6KesVerifier();
    private static final byte[] MESSAGE = "test message".getBytes();

    private static byte[] publicKey;
    private static byte[] sigPeriod0;
    private static byte[] sigPeriod5;

    @BeforeAll
    static void setUp() throws IOException {
        // Load binary test data from resources
        byte[] keyData = loadResource("/kes/key6.bin");
        sigPeriod0 = loadResource("/kes/key6Sig.bin");
        sigPeriod5 = loadResource("/kes/key6Sig5.bin");

        // Extract public key from key file:
        // The top-level (lhs_pk, rhs_pk) are the last 64 bytes of the key data.
        // Public key = blake2b_256(lhs_pk || rhs_pk)
        assertEquals(608, keyData.length, "Sum6Kes key size should be 608 bytes");
        byte[] topPks = new byte[64];
        System.arraycopy(keyData, 544, topPks, 0, 64);
        publicKey = Blake2bUtil.blake2bHash256(topPks);

        assertEquals(448, sigPeriod0.length, "Sum6KesSig should be 448 bytes");
        assertEquals(448, sigPeriod5.length, "Sum6KesSig should be 448 bytes");
    }

    @Test
    void verifyPeriod0() {
        assertTrue(verifier.verify(sigPeriod0, MESSAGE, publicKey, 0),
                "Signature at period 0 should verify");
    }

    @Test
    void verifyPeriod5() {
        assertTrue(verifier.verify(sigPeriod5, MESSAGE, publicKey, 5),
                "Signature at period 5 should verify");
    }

    @Test
    void verifyWrongPeriod() {
        // Signature at period 0 should NOT verify at period 1
        assertFalse(verifier.verify(sigPeriod0, MESSAGE, publicKey, 1),
                "Signature at period 0 should not verify at period 1");
    }

    @Test
    void verifyWrongMessage() {
        byte[] wrongMessage = "wrong message".getBytes();
        assertFalse(verifier.verify(sigPeriod0, wrongMessage, publicKey, 0),
                "Signature should not verify with wrong message");
    }

    @Test
    void verifyWrongPublicKey() {
        byte[] wrongPk = new byte[32];
        assertFalse(verifier.verify(sigPeriod0, MESSAGE, wrongPk, 0),
                "Signature should not verify with wrong public key");
    }

    @Test
    void invalidSignatureSize() {
        assertThrows(KesException.class,
                () -> verifier.verify(new byte[100], MESSAGE, publicKey, 0));
    }

    @Test
    void invalidPublicKeySize() {
        assertThrows(KesException.class,
                () -> verifier.verify(sigPeriod0, MESSAGE, new byte[16], 0));
    }

    @Test
    void invalidPeriodRange() {
        assertThrows(KesException.class,
                () -> verifier.verify(sigPeriod0, MESSAGE, publicKey, 64));
        assertThrows(KesException.class,
                () -> verifier.verify(sigPeriod0, MESSAGE, publicKey, -1));
    }

    @Test
    void verifyKnownPublicKey() {
        // Pre-computed public key from the test seed
        String expectedPkHex = "9b527f5907bd9ba20956d5b1db91d679b666b0c7e4c3a336eb6165ac58f99501";
        assertEquals(expectedPkHex, bytesToHex(publicKey),
                "Public key should match pre-computed value");
    }

    private static byte[] loadResource(String path) throws IOException {
        try (InputStream is = Sum6KesVerifierTest.class.getResourceAsStream(path)) {
            assertNotNull(is, "Resource not found: " + path);
            return is.readAllBytes();
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
