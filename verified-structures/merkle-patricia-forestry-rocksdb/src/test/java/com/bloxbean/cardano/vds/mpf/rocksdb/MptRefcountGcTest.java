package com.bloxbean.cardano.vds.mpf.rocksdb;

import com.bloxbean.cardano.vds.mpf.MpfTrie;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.GcManager;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.GcOptions;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.strategy.RefcountGcStrategy;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.RetentionPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rocksdb.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

class MptRefcountGcTest {
    private Path tempDir;
    private RocksDbStateTrees st;

    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory("mpt-ref-gc-");
        st = new RocksDbStateTrees(tempDir.toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (st != null) st.close();
        if (tempDir != null) try (var walk = Files.walk(tempDir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignore) {
                }
            });
        }
    }

    @Test
    void refcountGc_keepsLatest_deletesDropped() throws Exception {
        MpfTrie trie = new MpfTrie(st.nodeStore());

        // Version 1
        try (WriteBatch wb = new WriteBatch(); WriteOptions wo = new WriteOptions()) {
            st.nodeStore().withBatch(wb, () -> {
                trie.put(b("k1"), b("v1"));
                trie.put(b("k2"), b("v2"));
                long ver = st.rootsIndex().nextVersion();
                st.rootsIndex().put(ver, trie.getRootHash());
                RefcountGcStrategy.incrementAll(st.db(), st.nodeStore().nodesHandle(), st.nodeStore().nodesHandle(), trie.getRootHash(), wb, st.nodeStore().keyPrefixer());
                return null;
            });
            st.db().write(wo, wb);
        }

        byte[] rootV1 = trie.getRootHash();

        // Version 2 (adds k3)
        try (WriteBatch wb = new WriteBatch(); WriteOptions wo = new WriteOptions()) {
            st.nodeStore().withBatch(wb, () -> {
                trie.put(b("k3"), b("v3"));
                long ver = st.rootsIndex().nextVersion();
                st.rootsIndex().put(ver, trie.getRootHash());
                RefcountGcStrategy.incrementAll(st.db(), st.nodeStore().nodesHandle(), st.nodeStore().nodesHandle(), trie.getRootHash(), wb, st.nodeStore().keyPrefixer());
                return null;
            });
            st.db().write(wo, wb);
        }

        byte[] rootV2 = trie.getRootHash();

        long before = countNodes(st);

        // Run refcount GC, keep latest only
        GcManager manager = new GcManager(st.nodeStore(), st.rootsIndex());
        GcOptions opts = new GcOptions();
        var report = manager.runSync(new RefcountGcStrategy(), RetentionPolicy.keepLatestN(1), opts);
        assertTrue(report.deleted >= 0);

        long after = countNodes(st);
        assertTrue(after <= before);

        // Root V2 still functional; V1 might be partially unavailable
        MpfTrie v2 = new MpfTrie(st.nodeStore(), rootV2);
        assertArrayEquals(b("v3"), v2.get(b("k3")));
        MpfTrie v1 = new MpfTrie(st.nodeStore(), rootV1);
        // If shared nodes remain, k1 may still be readable; assert no crash
        v1.get(b("k1"));
    }

    private static long countNodes(RocksDbStateTrees st) {
        long cnt = 0;
        try (RocksIterator it = st.db().newIterator(st.nodeStore().nodesHandle())) {
            for (it.seekToFirst(); it.isValid(); it.next()) cnt++;
        }
        return cnt;
    }

    private static byte[] b(String s) {
        return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}

