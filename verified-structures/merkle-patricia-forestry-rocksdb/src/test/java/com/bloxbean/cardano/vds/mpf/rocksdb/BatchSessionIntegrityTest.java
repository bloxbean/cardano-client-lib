package com.bloxbean.cardano.vds.mpf.rocksdb;

import com.bloxbean.cardano.vds.mpf.MpfTrie;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.internal.RefcountLedger;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.strategy.RefcountGcStrategy;
import com.bloxbean.cardano.vds.mpf.internal.NodeRefParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

import java.nio.ByteBuffer;
import java.nio.file.Path;

import static com.bloxbean.cardano.vds.mpf.rocksdb.GcTestSupport.b;
import static org.junit.jupiter.api.Assertions.*;

/**
 * What a {@code withBatch} session sees must follow the batch's actual contents, also after {@code clear()},
 * {@code rollbackToSavePoint()} and rewrites of the same size (PR #716 review of {@code 1f98fd0e}, F3 and F4).
 */
class BatchSessionIntegrityTest {

    @TempDir
    Path tmp;

    // ---------------------------------------------------------------- F3: nodes discarded from the batch

    @ParameterizedTest
    @ValueSource(strings = {"clear", "rollback"})
    void rootWhoseNodesWereDiscardedFromTheBatchIsRejected(String how) throws Exception {
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve(how).toString());
             WriteBatch wb = new WriteBatch(); WriteOptions wo = new WriteOptions()) {
            byte[][] saved = new byte[1][];
            st.nodeStore().withBatch(wb, () -> {
                if (how.equals("rollback")) wb.setSavePoint();
                MpfTrie trie = new MpfTrie(st.nodeStore());
                trie.put(b("alice"), b("100"));
                saved[0] = trie.getRootHash();
                assertNotNull(st.nodeStore().get(saved[0]));
                if (how.equals("clear")) wb.clear();
                else wb.rollbackToSavePoint();

                assertNull(st.nodeStore().get(saved[0]), "node visibility follows the batch");
                int records = wb.count();
                assertThrows(IllegalStateException.class, () -> st.putRootWithRefcount(saved[0]));
                assertEquals(records, wb.count(), "nothing staged for the rejected root");
                return null;
            });
            st.db().write(wo, wb);
            assertEquals(-1L, st.rootsIndex().lastVersion());
            assertNull(st.rootsIndex().latest());
            assertNull(st.nodeStore().get(saved[0]));
            assertEquals(0L, refcount(st.nodeStore(), saved[0]));
        }
    }

    @Test
    void rootWithAMissingChildIsRejectedWithoutStagingAnything() throws Exception {
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve("missing-child").toString());
             WriteBatch wb = new WriteBatch()) {
            MpfTrie trie = new MpfTrie(st.nodeStore());
            for (int i = 0; i < 20; i++) trie.put(b("k" + i), b("v" + i));
            byte[] root = trie.getRootHash();
            byte[] child = NodeRefParser.childRefs(st.nodeStore().get(root)).get(0);
            st.db().delete(st.nodeStore().nodesHandle(), st.nodeStore().keyPrefixer().prefix(child)); // a damaged store

            assertThrows(IllegalStateException.class, () -> st.putRootWithRefcount(root));
            st.nodeStore().withBatch(wb, () -> {
                st.nodeStore().put(b("unrelated-hash-of-32-bytes-....."), b("x"));
                int records = wb.count();
                assertThrows(IllegalStateException.class, () -> st.putRootWithRefcount(root));
                assertEquals(records, wb.count(), "partial counts are rolled back");
                return null;
            });
            assertEquals(-1L, st.rootsIndex().lastVersion());
            assertEquals(0L, refcount(st.nodeStore(), root));
        }
    }

    // ---------------------------------------------------------------- F4: same count and size, other contents

    @Test
    void rewriteOfTheSameCountAndSizeIsNotMistakenForTheCachedBatch() throws Exception {
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve("equal-size").toString());
             WriteBatch wb = new WriteBatch(); WriteOptions wo = new WriteOptions()) {
            byte[][] rootB = new byte[1][];
            long[] versions = new long[1];
            st.nodeStore().withBatch(wb, () -> {
                MpfTrie a = new MpfTrie(st.nodeStore());
                a.put(b("first"), b("value"));
                st.putRootWithRefcount(a.getRootHash());            // caches the view of this batch
                int count = wb.count();
                long size = wb.getDataSize();

                wb.clear();
                MpfTrie bTrie = new MpfTrie(st.nodeStore());
                bTrie.put(b("other"), b("value"));
                rootB[0] = bTrie.getRootHash();
                st.rootsIndex().withBatch(wb, () -> {
                    st.rootsIndex().put(0, rootB[0]);
                    return null;
                });
                RefcountGcStrategy.incrementAll(st.db(), st.nodeStore().nodesHandle(), st.nodeStore().nodesHandle(),
                        rootB[0], wb, st.nodeStore().keyPrefixer());
                assertEquals(count, wb.count(), "precondition: same record count");
                assertEquals(size, wb.getDataSize(), "precondition: same data size");

                versions[0] = st.putRootWithRefcount(rootB[0]);
                return null;
            });
            st.db().write(wo, wb);
            assertEquals(1L, versions[0], "version 0 is staged in the batch");
            assertArrayEquals(rootB[0], st.rootsIndex().get(0));
            assertArrayEquals(rootB[0], st.rootsIndex().get(1));
            assertEquals(2L, refcount(st.nodeStore(), rootB[0]), "two versions reference B");

            // One decrement (dropping version 0) must leave B alive for version 1.
            try (WriteBatch d = new WriteBatch()) {
                RefcountGcStrategy.decrementAll(st.db(), st.nodeStore().nodesHandle(), st.nodeStore().nodesHandle(),
                        rootB[0], d, st.nodeStore().keyPrefixer());
                st.db().write(wo, d);
            }
            assertNotNull(st.nodeStore().get(rootB[0]));
            assertEquals(1L, refcount(st.nodeStore(), rootB[0]));
        }
    }

    @Test
    void writesOfTheRootsIndexAndStaticHelpersAloneInvalidateTheView() throws Exception {
        // After clear(), only the roots index and the static refcount helper write (no node store call that would
        // notice the cleared batch); a log-data record pads the batch back to the cached count and size.
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve("helpers-only").toString());
             WriteBatch wb = new WriteBatch(); WriteOptions wo = new WriteOptions()) {
            MpfTrie committed = new MpfTrie(st.nodeStore());
            committed.put(b("other"), b("v"));
            byte[] rootB = committed.getRootHash();               // committed, never counted
            long[] version = new long[1];
            st.nodeStore().withBatch(wb, () -> {
                MpfTrie a = new MpfTrie(st.nodeStore());
                a.put(b("first"), b("a-longer-value-so-this-batch-is-larger-than-its-replacement"));
                st.putRootWithRefcount(a.getRootHash());
                int count = wb.count();
                long size = wb.getDataSize();

                wb.clear();
                st.rootsIndex().withBatch(wb, () -> {
                    st.rootsIndex().put(st.rootsIndex().nextVersion(), rootB);
                    return null;
                });
                RefcountGcStrategy.incrementAll(st.db(), st.nodeStore().nodesHandle(), st.nodeStore().nodesHandle(),
                        rootB, wb, st.nodeStore().keyPrefixer());
                long pad = size - wb.getDataSize() - 2;               // log-data record: tag, 1-byte length, blob
                assertTrue(pad >= 0 && pad < 128, "pad " + pad);
                wb.putLogData(new byte[(int) pad]);
                assertEquals(count, wb.count(), "precondition: same record count");
                assertEquals(size, wb.getDataSize(), "precondition: same data size");

                version[0] = st.putRootWithRefcount(rootB);
                return null;
            });
            st.db().write(wo, wb);
            assertEquals(1L, version[0]);
            assertEquals(2L, refcount(st.nodeStore(), rootB), "versions 0 and 1 reference B");
        }
    }

    @Test
    void discardedNodesStayInvisibleAfterLaterRecordings() throws Exception {
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve("read-after-rewrite").toString());
             WriteBatch wb = new WriteBatch(); WriteOptions wo = new WriteOptions()) {
            st.nodeStore().withBatch(wb, () -> {
                MpfTrie a = new MpfTrie(st.nodeStore());
                a.put(b("first"), b("value"));
                byte[] rootA = a.getRootHash();
                wb.clear();
                MpfTrie bTrie = new MpfTrie(st.nodeStore());
                bTrie.put(b("other"), b("value"));
                st.putRootWithRefcount(bTrie.getRootHash());
                assertNull(st.nodeStore().get(rootA), "discarded node is not visible after the exact check");
                assertThrows(IllegalStateException.class, () -> st.putRootWithRefcount(rootA));
                return null;
            });
            st.db().write(wo, wb);
            assertEquals(0L, st.rootsIndex().lastVersion());
        }
    }

    @Test
    void bulkRecordingInOneSessionReplaysTheBatchOnce() throws Exception {
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve("bulk").toString())) {
            MpfTrie trie = new MpfTrie(st.nodeStore());
            long before = RefcountLedger.fullReplayCount();
            RocksDbMptSession.of(st).write(() -> {
                for (int v = 0; v < 200; v++) {
                    for (int i = 0; i < 20; i++) trie.put(b("k" + ((v * 13 + i) % 500)), b("v" + v + "-" + i));
                    st.putRootWithRefcount(trie.getRootHash());
                }
            });
            assertTrue(RefcountLedger.fullReplayCount() - before <= 1,
                    "full replays: " + (RefcountLedger.fullReplayCount() - before));
            assertEquals(199L, st.rootsIndex().lastVersion());
        }
    }

    private static long refcount(RocksDbNodeStore ns, byte[] hash) throws RocksDBException {
        byte[] v = ns.db().get(ns.nodesHandle(), ns.keyPrefixer().prefix(RefcountLedger.refKey(hash)));
        return v == null ? 0 : ByteBuffer.wrap(v).getLong();
    }
}
