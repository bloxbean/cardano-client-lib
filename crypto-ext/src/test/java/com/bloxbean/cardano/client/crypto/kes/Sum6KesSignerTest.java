package com.bloxbean.cardano.client.crypto.kes;

import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Sum6KesSigner using the same test vectors as Sum6KesVerifierTest.
 * <p>
 * Test data generated from Haskell cardano-base with:
 * - Seed: "test string of 32 byte of lenght" (ASCII, 32 bytes)
 * - Message: "test message" (ASCII)
 */
class Sum6KesSignerTest {

    private static final Sum6KesSigner signer = new Sum6KesSigner();
    private static final Sum6KesVerifier verifier = new Sum6KesVerifier();
    private static final byte[] MESSAGE = "test message".getBytes();

    private static byte[] keyData;
    private static byte[] publicKey;
    private static byte[] expectedSigPeriod0;
    private static byte[] expectedSigPeriod5;

    @BeforeAll
    static void setUp() throws IOException {
        keyData = loadResource("/kes/key6.bin");
        expectedSigPeriod0 = loadResource("/kes/key6Sig.bin");
        expectedSigPeriod5 = loadResource("/kes/key6Sig5.bin");

        assertEquals(608, keyData.length, "Sum6Kes key size should be 608 bytes");

        // Derive public key
        publicKey = signer.deriveVerificationKey(keyData);
    }

    @Test
    void signPeriod0_matchesExpected() {
        byte[] sig = signer.sign(keyData, MESSAGE, 0);

        assertEquals(448, sig.length, "Signature should be 448 bytes");
        assertArrayEquals(expectedSigPeriod0, sig, "Signature at period 0 should match expected");
    }

    @Test
    void signPeriod5_matchesExpected() {
        byte[] sig = signer.sign(keyData, MESSAGE, 5);

        assertEquals(448, sig.length, "Signature should be 448 bytes");
        assertArrayEquals(expectedSigPeriod5, sig, "Signature at period 5 should match expected");
    }

    @Test
    void signThenVerify_period0() {
        byte[] sig = signer.sign(keyData, MESSAGE, 0);
        assertTrue(verifier.verify(sig, MESSAGE, publicKey, 0),
                "Signed message at period 0 should verify");
    }

    @Test
    void signThenVerify_period5() {
        byte[] sig = signer.sign(keyData, MESSAGE, 5);
        assertTrue(verifier.verify(sig, MESSAGE, publicKey, 5),
                "Signed message at period 5 should verify");
    }

    @Test
    void signThenVerify_allPeriods() {
        for (int period = 0; period < 64; period++) {
            byte[] sig = signer.sign(keyData, MESSAGE, period);
            assertTrue(verifier.verify(sig, MESSAGE, publicKey, period),
                    "Signature at period " + period + " should verify");
        }
    }

    @Test
    void deriveVerificationKey_matchesExpected() {
        String expectedPkHex = "9b527f5907bd9ba20956d5b1db91d679b666b0c7e4c3a336eb6165ac58f99501";
        assertEquals(expectedPkHex, bytesToHex(publicKey),
                "Derived public key should match expected");
    }

    @Test
    void invalidSecretKeySize() {
        assertThrows(KesException.class,
                () -> signer.sign(new byte[100], MESSAGE, 0));
    }

    @Test
    void invalidPeriodRange() {
        assertThrows(KesException.class,
                () -> signer.sign(keyData, MESSAGE, 64));
        assertThrows(KesException.class,
                () -> signer.sign(keyData, MESSAGE, -1));
    }

    private static byte[] loadResource(String path) throws IOException {
        try (InputStream is = Sum6KesSignerTest.class.getResourceAsStream(path)) {
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
