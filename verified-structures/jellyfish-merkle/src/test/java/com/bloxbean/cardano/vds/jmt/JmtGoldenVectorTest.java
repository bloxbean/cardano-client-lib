package com.bloxbean.cardano.vds.jmt;

import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.jmt.store.InMemoryJmtStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JmtGoldenVectorTest {

    @Test
    void javaImplementationMatchesFrozenV1Vectors() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream input = getClass().getResourceAsStream("/jmt/golden-vectors-v1.json")) {
            assertNotNull(input, "golden vector resource");
            JsonNode document = mapper.readTree(input);
            assertEquals("jmt-golden-vectors-v1", document.path("schema").asText());
            assertEquals(JmtProfile.classicBlake2b256V1().format().profileId(),
                    document.path("profile").asText());

            try (InMemoryJmtStore store = new InMemoryJmtStore()) {
                JellyfishMerkleTree tree = new JellyfishMerkleTree(store);
                for (JsonNode operation : document.path("operations")) {
                    Map<byte[], byte[]> updates = new LinkedHashMap<>();
                    for (JsonNode update : operation.path("updates")) {
                        byte[] key = decodeHex(update.path("key").asText());
                        byte[] value = decodeHex(update.path("value").asText());
                        assertArrayEquals(decodeHex(update.path("keyHash").asText()),
                                Blake2b256.digest(key));
                        assertArrayEquals(decodeHex(update.path("valueHash").asText()),
                                Blake2b256.digest(value));
                        updates.put(key, value);
                    }
                    JellyfishMerkleTree.CommitResult result = tree.put(
                            operation.path("version").asLong(), updates);
                    assertArrayEquals(decodeHex(operation.path("root").asText()), result.rootHash());
                    for (JsonNode encoded : operation.path("encodedNodes")) {
                        byte[] cbor = decodeHex(encoded.path("cbor").asText());
                        assertArrayEquals(cbor, JmtEncoding.decode(cbor).encode());
                    }
                }

                for (JsonNode proof : document.path("proofs")) {
                    byte[] value = proof.path("value").isNull()
                            ? null : decodeHex(proof.path("value").asText());
                    boolean actual = tree.verifyProofWire(
                            decodeHex(proof.path("root").asText()),
                            decodeHex(proof.path("key").asText()),
                            value,
                            proof.path("including").asBoolean(),
                            decodeHex(proof.path("wire").asText()));
                    if (proof.path("expectedValid").asBoolean()) {
                        assertTrue(actual, proof.path("id").asText());
                    } else {
                        assertFalse(actual, proof.path("id").asText());
                    }
                }
            }
        }
    }

    private static byte[] decodeHex(String value) {
        if ((value.length() & 1) != 0) {
            throw new IllegalArgumentException("Odd-length hex string");
        }
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int high = Character.digit(value.charAt(i * 2), 16);
            int low = Character.digit(value.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Non-hex character");
            }
            result[i] = (byte) ((high << 4) | low);
        }
        return result;
    }
}
