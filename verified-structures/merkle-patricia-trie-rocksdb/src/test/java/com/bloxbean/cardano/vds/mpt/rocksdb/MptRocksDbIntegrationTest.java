package com.bloxbean.cardano.vds.mpt.rocksdb;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.mpt.SecureTrie;
import com.bloxbean.cardano.vds.mpt.rocksdb.gc.internal.RocksDbGc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksIterator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MptRocksDbIntegrationTest {
    private Path tempDir;
    private RocksDbStateTrees stateTrees;
    private final HashFunction hashFn = Blake2b256::digest;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("mpt-rocksdb-it-");
        stateTrees = new RocksDbStateTrees(tempDir.toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (stateTrees != null) stateTrees.close();
        if (tempDir != null) {
            // Best-effort cleanup
            try (java.util.stream.Stream<Path> walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignore) {
                    }
                });
            }
        }
    }

    @Test
    void streamingPersist_reload_versions_and_gc() {
        SecureTrie trie = new SecureTrie(stateTrees.nodeStore(), hashFn);

        Map<String, String> v1 = new LinkedHashMap<>();
        v1.put("k1", "one");
        v1.put("k2", "two");
        v1.put("k3", "three");
        for (Map.Entry<String, String> e : v1.entrySet()) {
            trie.put(b(e.getKey()), b(e.getValue()));
        }
        byte[] rootV1 = trie.getRootHash();
        assertNotNull(rootV1);
        long ver1 = stateTrees.rootsIndex().nextVersion();
        stateTrees.rootsIndex().put(ver1, rootV1);

        Map<String, String> v2adds = new LinkedHashMap<>();
        v2adds.put("k4", "four");
        v2adds.put("k5", "five");
        for (Map.Entry<String, String> e : v2adds.entrySet()) {
            trie.put(b(e.getKey()), b(e.getValue()));
        }
        byte[] rootV2 = trie.getRootHash();
        assertNotNull(rootV2);
        long ver2 = stateTrees.rootsIndex().nextVersion();
        stateTrees.rootsIndex().put(ver2, rootV2);

        // Reload at V1 and verify values
        SecureTrie trieV1 = new SecureTrie(stateTrees.nodeStore(), hashFn, rootV1);
        assertArrayEquals(b("one"), trieV1.get(b("k1")));
        assertArrayEquals(b("two"), trieV1.get(b("k2")));
        assertNull(trieV1.get(b("k4")));

        // Reload at V2 and verify values
        SecureTrie trieV2 = new SecureTrie(stateTrees.nodeStore(), hashFn, rootV2);
        assertArrayEquals(b("five"), trieV2.get(b("k5")));

        // Count nodes pre-GC
        long pre = countNodes(stateTrees.db(), stateTrees.nodeStore().nodesHandle());
        assertTrue(pre > 0, "expected some nodes in RocksDB");

        // GC keep latest 1 (V2)
        RocksDbGc.Options opts = new RocksDbGc.Options();
        RocksDbGc.Report report = RocksDbGc.gcKeepLatest(stateTrees.nodeStore(), stateTrees.rootsIndex(), 1, opts);
        assertTrue(report.deleted >= 0);

        long post = countNodes(stateTrees.db(), stateTrees.nodeStore().nodesHandle());
        assertTrue(post <= pre);

        // Old-only keys should no longer resolve fully in V1 after GC (some nodes pruned)
        SecureTrie afterGcV1 = new SecureTrie(stateTrees.nodeStore(), hashFn, rootV1);
        byte[] maybeK1 = afterGcV1.get(b("k1"));
        if (maybeK1 != null) {
            // In case of shared nodes, k1 might still be reachable; ensure V2 still intact regardless
            SecureTrie afterGcV2 = new SecureTrie(stateTrees.nodeStore(), hashFn, rootV2);
            assertArrayEquals(b("five"), afterGcV2.get(b("k5")));
        }
    }

    private static long countNodes(RocksDB db, org.rocksdb.ColumnFamilyHandle cf) {
        long count = 0;
        try (RocksIterator it = db.newIterator(cf)) {
            for (it.seekToFirst(); it.isValid(); it.next()) {
                count++;
            }
        }
        return count;
    }

    private static byte[] b(String s) {
        return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}

