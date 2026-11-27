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

import static com.bloxbean.cardano.vds.mpf.rocksdb.GcTestSupport.b;
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
    void refcountGc_rejectsMissingRootsIndex_beforeDeletingAnything() throws Exception {
        MpfTrie trie = new MpfTrie(st.nodeStore());
        RocksDbMptSession.of(st).write(() -> {
            trie.put(b("k1"), b("v1"));
            st.putRootWithRefcount(trie.getRootHash());
        });
        byte[] root = trie.getRootHash();

        NullPointerException e = assertThrows(NullPointerException.class, () -> new RefcountGcStrategy().run(
                st.nodeStore(), null, ignored -> java.util.List.of(), new GcOptions()));
        assertEquals("RefcountGcStrategy needs the roots index", e.getMessage());
        assertArrayEquals(b("v1"), new MpfTrie(st.nodeStore(), root).get(b("k1")));
    }

    @Test
    void refcountGc_keepsLatest_deletesDropped() throws Exception {
        MpfTrie trie = new MpfTrie(st.nodeStore());
        RocksDbMptSession session = RocksDbMptSession.of(st);

        // Version 0: nodes and root+refcounts commit in one session batch
        session.write(() -> {
            trie.put(b("k1"), b("v1"));
            trie.put(b("k2"), b("v2"));
            st.putRootWithRefcount(trie.getRootHash());
        });
        byte[] rootV1 = trie.getRootHash();

        // Version 1 (adds k3, updates k1)
        session.write(() -> {
            trie.put(b("k3"), b("v3"));
            trie.put(b("k1"), b("v1b"));
            st.putRootWithRefcount(trie.getRootHash());
        });
        byte[] rootV2 = trie.getRootHash();
        assertEquals(java.util.List.of(0L, 1L), new java.util.ArrayList<>(st.rootsIndex().listAll().keySet()));

        long before = GcTestSupport.countNodeKeys(st.nodeStore());
        long reachableV2 = GcTestSupport.reach(st.nodeStore(), rootV2)[0];

        // Run refcount GC, keep latest only
        GcManager manager = new GcManager(st.nodeStore(), st.rootsIndex());
        var report = manager.runSync(new RefcountGcStrategy(), RetentionPolicy.keepLatestN(1), new GcOptions());
        assertTrue(report.deleted > 0, "v0-only nodes must be deleted");

        // Only the latest version's nodes stay counted (intermediate nodes written inside a batch but never part
        // of a recorded root are not counted, so refcount GC leaves them), and the latest version is intact.
        assertEquals(reachableV2, GcTestSupport.countRefcountKeys(st.nodeStore()));
        assertTrue(GcTestSupport.countNodeKeys(st.nodeStore()) < before);
        java.util.Map<String, byte[]> model = new java.util.LinkedHashMap<>();
        model.put(GcTestSupport.hex("k1"), b("v1b"));
        model.put(GcTestSupport.hex("k2"), b("v2"));
        model.put(GcTestSupport.hex("k3"), b("v3"));
        GcTestSupport.assertIntact(st.nodeStore(), rootV2, model, "latest after refcount GC");
        // The dropped version entry is gone, so a second run is a no-op (no double decrement)
        assertEquals(java.util.List.of(1L), new java.util.ArrayList<>(st.rootsIndex().listAll().keySet()));
        var again = manager.runSync(new RefcountGcStrategy(), RetentionPolicy.keepLatestN(1), new GcOptions());
        assertEquals(0, again.deleted);
        GcTestSupport.assertIntact(st.nodeStore(), rootV2, model, "latest after second refcount GC");
        assertTrue(GcTestSupport.reach(st.nodeStore(), rootV1)[1] > 0, "v0 should no longer be complete");
    }

    @Test
    void incrementAllInTheSameBatchAsTheNodesCountsTheWholeTrie() throws Exception {
        MpfTrie trie = new MpfTrie(st.nodeStore());
        // The pre-0.8.0 GcTool pattern: nodes staged in the batch and incrementAll into the same batch. It used to
        // count only the root (and later free live nodes); the helper now replays the batch and counts every node.
        try (WriteBatch wb = new WriteBatch(); WriteOptions wo = new WriteOptions()) {
            st.nodeStore().withBatch(wb, () -> {
                for (int i = 0; i < 10; i++) trie.put(b("k" + i), b("v" + i));
                st.rootsIndex().withBatch(wb, () -> {
                    st.rootsIndex().put(st.rootsIndex().nextVersion(), trie.getRootHash());
                    return null;
                });
                RefcountGcStrategy.incrementAll(st.db(), st.nodeStore().nodesHandle(), st.nodeStore().nodesHandle(),
                        trie.getRootHash(), wb, st.nodeStore().keyPrefixer());
                return null;
            });
            st.db().write(wo, wb);
        }
        assertEquals(GcTestSupport.reach(st.nodeStore(), trie.getRootHash())[0], GcTestSupport.countRefcountKeys(st.nodeStore()));
    }

    @Test
    void incrementAllFailsFastWhenTheRootIsNeitherCommittedNorStaged() {
        MpfTrie trie = new MpfTrie(new TestNodeStore());
        trie.put(b("k1"), b("v1")); // nodes live in another store
        try (WriteBatch wb = new WriteBatch()) {
            assertThrows(IllegalStateException.class, () -> RefcountGcStrategy.incrementAll(st.db(),
                    st.nodeStore().nodesHandle(), st.nodeStore().nodesHandle(), trie.getRootHash(), wb,
                    st.nodeStore().keyPrefixer()));
        }
    }


}

