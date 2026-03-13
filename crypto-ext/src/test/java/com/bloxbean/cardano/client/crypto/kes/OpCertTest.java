package com.bloxbean.cardano.client.crypto.kes;

import com.bloxbean.cardano.client.crypto.api.impl.EdDSASigningProvider;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OpCert parsing using a real devnet operational certificate.
 */
class OpCertTest {

    @Test
    void parseFromTextEnvelope() throws IOException {
        String json = loadResourceAsString("/opcert/opcert1.cert");

        OpCert opCert = OpCert.fromTextEnvelope(json);

        assertNotNull(opCert);
        assertEquals(32, opCert.getKesVkey().length, "KES vkey should be 32 bytes");
        assertEquals(0, opCert.getCounter(), "Counter should be 0");
        assertEquals(0, opCert.getKesPeriod(), "KES period should be 0");
        assertEquals(64, opCert.getColdSignature().length, "Cold signature should be 64 bytes");
        assertEquals(32, opCert.getColdVkey().length, "Cold vkey should be 32 bytes");
    }

    @Test
    void parseKnownValues() throws IOException {
        String json = loadResourceAsString("/opcert/opcert1.cert");

        OpCert opCert = OpCert.fromTextEnvelope(json);

        // Known values from the devnet opcert
        assertEquals("19a8369c1ccabc36c4c79e4a58bf270d9225ce7ba725c7adc44cdf457c061bfd",
                bytesToHex(opCert.getKesVkey()), "KES vkey should match");
        assertEquals("6364b1c08ac04dad3cd01ad88ddd3c42d6d6a6c66064cff8ef12962f9c4515e4",
                bytesToHex(opCert.getColdVkey()), "Cold vkey should match");
    }

    @Test
    void verifyColdSignature() throws IOException {
        String json = loadResourceAsString("/opcert/opcert1.cert");
        OpCert opCert = OpCert.fromTextEnvelope(json);

        // The cold signature signs the opcert body CBOR:
        // array(4): [kes_vkey, counter, kes_period, <sig_placeholder_not_included>]
        // Actually in Cardano, the cold key signs: kes_vkey(32) || counter(8) || kes_period(8)
        // as a simple concatenation of the raw bytes

        // Build the signed message: kes_vkey(32) || counter(8, big-endian) || kes_period(8, big-endian)
        byte[] signedData = new byte[32 + 8 + 8];
        System.arraycopy(opCert.getKesVkey(), 0, signedData, 0, 32);
        // counter as 8-byte big-endian
        long counter = opCert.getCounter();
        for (int i = 7; i >= 0; i--) {
            signedData[32 + (7 - i)] = (byte) (counter >>> (i * 8));
        }
        // kes_period as 8-byte big-endian
        long period = opCert.getKesPeriod();
        for (int i = 7; i >= 0; i--) {
            signedData[40 + (7 - i)] = (byte) (period >>> (i * 8));
        }

        EdDSASigningProvider ed25519 = new EdDSASigningProvider();
        assertTrue(ed25519.verify(opCert.getColdSignature(), signedData, opCert.getColdVkey()),
                "Cold key signature should verify over opcert body");
    }

    @Test
    void fromCbor_directBytes() {
        byte[] cbor = HexUtil.decodeHexString(
                "8284582019a8369c1ccabc36c4c79e4a58bf270d9225ce7ba725c7adc44cdf457c061bfd"
                        + "000058409cc0ff01ae87c948a367d818aa06098f3d14acf7c89dc8cd3f20274367386c70"
                        + "54cd8988eff4f09a0eab45de1ae2364dced2f1d74259b64821e8b7d5b0e6c105"
                        + "58206364b1c08ac04dad3cd01ad88ddd3c42d6d6a6c66064cff8ef12962f9c4515e4");

        OpCert opCert = OpCert.fromCbor(cbor);

        assertNotNull(opCert);
        assertEquals(0, opCert.getCounter());
        assertEquals(32, opCert.getKesVkey().length);
    }

    @Test
    void invalidCbor() {
        assertThrows(KesException.class, () -> OpCert.fromCbor(new byte[]{0x00}));
    }

    private static String loadResourceAsString(String path) throws IOException {
        try (InputStream is = OpCertTest.class.getResourceAsStream(path)) {
            assertNotNull(is, "Resource not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
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
