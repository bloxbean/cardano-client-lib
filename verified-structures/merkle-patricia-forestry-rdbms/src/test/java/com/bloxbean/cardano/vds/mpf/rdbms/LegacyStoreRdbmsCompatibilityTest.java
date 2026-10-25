package com.bloxbean.cardano.vds.mpf.rdbms;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.core.util.Bytes;
import com.bloxbean.cardano.vds.mpf.MpfTrie;
import com.bloxbean.cardano.vds.rdbms.common.DbConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tries written by CCL 0.8.0-pre6 (fixture shared with the MPF module's LegacyStoreCompatibilityTest) open from an
 * RDBMS (H2) node store with the same roots and values.
 */
class LegacyStoreRdbmsCompatibilityTest {

    private static final Path FIXTURE = Path.of("..", "merkle-patricia-forestry", "src", "test", "resources",
            "mpf-compat", "legacy-store-v0.8.0-pre6.json");

    @Test
    void legacyTriesOpenFromH2() throws Exception {
        JsonNode doc = new ObjectMapper().readTree(FIXTURE.toFile());
        int scenarios = 0;
        for (JsonNode s : doc.get("scenarios")) {
            scenarios++;
            String name = s.get("name").asText();
            HashFunction hf = "sha-256".equals(s.get("hash").asText()) ? LegacyStoreRdbmsCompatibilityTest::sha256 : Blake2b256::digest;
            byte[] root = Bytes.fromHex(s.get("root").asText());
            String url = "jdbc:h2:mem:legacy_" + scenarios + "_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
            try (DbConfig config = DbConfig.builder().simpleJdbcUrl(url).build()) {
                RdbmsMpfSchema.createIfNotExists(config);
                RdbmsNodeStore store = new RdbmsNodeStore(config);
                for (Iterator<Map.Entry<String, JsonNode>> it = s.get("nodes").fields(); it.hasNext(); ) {
                    Map.Entry<String, JsonNode> n = it.next();
                    store.put(Bytes.fromHex(n.getKey()), Bytes.fromHex(n.getValue().asText()));
                }
                MpfTrie trie = MpfTrie.open(store, hf, root);
                assertEquals(s.get("root").asText(), Bytes.toHex(trie.getRootHash()), name);
                int count = 0;
                for (Iterator<Map.Entry<String, JsonNode>> it = s.get("entries").fields(); it.hasNext(); count++) {
                    Map.Entry<String, JsonNode> e = it.next();
                    assertArrayEquals(Bytes.fromHex(e.getValue().asText()), trie.get(Bytes.fromHex(e.getKey())), name);
                }
                assertEquals(count, trie.getAllEntries().size(), name);
            }
        }
        assertEquals(4, scenarios);
    }

    private static byte[] sha256(byte[] in) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(in);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
