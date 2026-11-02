package com.bloxbean.cardano.vds.mpf.rocksdb;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.core.util.Bytes;
import com.bloxbean.cardano.vds.mpf.MpfTrie;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.GcManager;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.GcOptions;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.RetentionPolicy;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.strategy.OnDiskMarkSweepStrategy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tries written by CCL 0.8.0-pre6 (fixture shared with the MPF module's LegacyStoreCompatibilityTest) open from a
 * RocksDB store with the same roots and values, including after a mark-sweep GC that keeps their version.
 */
class LegacyStoreRocksDbCompatibilityTest {

    private static final Path FIXTURE = Path.of("..", "merkle-patricia-forestry", "src", "test", "resources",
            "mpf-compat", "legacy-store-v0.8.0-pre6.json");

    @TempDir
    Path tmp;

    @Test
    void legacyTriesOpenFromRocksDb() throws Exception {
        JsonNode doc = new ObjectMapper().readTree(FIXTURE.toFile());
        int scenarios = 0;
        for (JsonNode s : doc.get("scenarios")) {
            scenarios++;
            String name = s.get("name").asText();
            HashFunction hf = "sha-256".equals(s.get("hash").asText()) ? LegacyStoreRocksDbCompatibilityTest::sha256 : Blake2b256::digest;
            byte[] root = Bytes.fromHex(s.get("root").asText());
            try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve(name).toString())) {
                for (Iterator<Map.Entry<String, JsonNode>> it = s.get("nodes").fields(); it.hasNext(); ) {
                    Map.Entry<String, JsonNode> n = it.next();
                    st.nodeStore().put(Bytes.fromHex(n.getKey()), Bytes.fromHex(n.getValue().asText()));
                }
                st.rootsIndex().put(1L, root);
                assertEntries(name, MpfTrie.open(st.nodeStore(), hf, root), s);

                new GcManager(st.nodeStore(), st.rootsIndex())
                        .runSync(new OnDiskMarkSweepStrategy(), RetentionPolicy.keepLatestN(1), new GcOptions());
                assertEntries(name + " after GC", MpfTrie.open(st.nodeStore(), hf, root), s);
            }
        }
        assertEquals(4, scenarios);
    }

    private static void assertEntries(String name, MpfTrie trie, JsonNode s) {
        assertEquals(s.get("root").asText(), Bytes.toHex(trie.getRootHash()), name);
        int count = 0;
        for (Iterator<Map.Entry<String, JsonNode>> it = s.get("entries").fields(); it.hasNext(); count++) {
            Map.Entry<String, JsonNode> e = it.next();
            assertArrayEquals(Bytes.fromHex(e.getValue().asText()), trie.get(Bytes.fromHex(e.getKey())), name + " " + e.getKey());
        }
        assertEquals(count, trie.getAllEntries().size(), name);
    }

    private static byte[] sha256(byte[] in) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(in);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
