package com.bloxbean.cardano.vds.mpf.rocksdb;

import com.bloxbean.cardano.vds.core.api.StorageMode;
import com.bloxbean.cardano.vds.mpf.MpfTrie;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.GcManager;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.GcOptions;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.GcReport;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.GcStrategy;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.RetentionPolicy;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.internal.RocksDbGc;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.strategy.OnDiskMarkSweepStrategy;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.strategy.RefcountGcStrategy;
import com.bloxbean.cardano.vds.rocksdb.namespace.NamespaceOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.*;

import static com.bloxbean.cardano.vds.mpf.rocksdb.GcTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the RocksDB GC data-loss paths reported in issue #715 (item 2) and found while fixing it.
 * Each test failed (deleted live data or double-counted) before the fix.
 */
class GcDataLossRegressionTest {

    @TempDir
    Path tmp;

    private static GcStrategy strategy(String name) {
        return "refcount".equals(name) ? new RefcountGcStrategy() : new OnDiskMarkSweepStrategy();
    }

    /** Writes {@code n} versions, each changing a few keys of a shared key space; returns per-version models. */
    private static List<Map<String, byte[]>> writeVersions(RocksDbStateTrees st, String tag, int n, boolean refcount) {
        MpfTrie trie = new MpfTrie(st.nodeStore());
        RocksDbMptSession session = RocksDbMptSession.of(st);
        Map<String, byte[]> model = new LinkedHashMap<>();
        List<Map<String, byte[]>> versions = new ArrayList<>();
        for (int v = 0; v < n; v++) {
            final int ver = v;
            session.write(() -> {
                for (int i = 0; i < 6; i++) {
                    String k = tag + "-" + ((ver * 3 + i) % 20);
                    byte[] val = b(tag + "-v" + ver + "-" + i);
                    trie.put(b(k), val);
                    model.put(hex(k), val);
                }
                if (refcount) st.putRootWithRefcount(trie.getRootHash());
            });
            if (!refcount) st.rootsIndex().put(st.rootsIndex().nextVersion(), trie.getRootHash());
            versions.add(new LinkedHashMap<>(model));
        }
        return versions;
    }

    // ---------------------------------------------------------------- 2a key-prefix namespaces

    @ParameterizedTest
    @ValueSource(strings = {"marksweep", "refcount"})
    void gcOnOneKeyPrefixNamespaceLeavesOtherNamespaceIntact(String strategyName) throws Exception {
        String path = tmp.resolve("shared").toString();
        boolean refcount = "refcount".equals(strategyName);
        List<Map<String, byte[]>> ns1Versions;
        List<byte[]> ns1Roots = new ArrayList<>();
        try (RocksDbStateTrees ns1 = new RocksDbStateTrees(path, NamespaceOptions.keyPrefix((byte) 0x01))) {
            ns1Versions = writeVersions(ns1, "a", 3, refcount);
            ns1.rootsIndex().listAll().values().forEach(ns1Roots::add);
        }
        try (RocksDbStateTrees ns2 = new RocksDbStateTrees(path, NamespaceOptions.keyPrefix((byte) 0x02))) {
            List<Map<String, byte[]>> ns2Versions = writeVersions(ns2, "b", 3, refcount);
            // listAll is scoped to the namespace
            assertEquals(List.of(0L, 1L, 2L), new ArrayList<>(ns2.rootsIndex().listAll().keySet()));
            for (byte[] r : ns2.rootsIndex().listAll().values()) {
                for (byte[] other : ns1Roots) assertFalse(Arrays.equals(r, other), "ns 0x02 listed a root of ns 0x01");
            }
            assertEquals(List.of(1L, 2L), new ArrayList<>(ns2.rootsIndex().listRange(1, 5).keySet()));

            GcReport r = new GcManager(ns2.nodeStore(), ns2.rootsIndex())
                    .runSync(strategy(strategyName), RetentionPolicy.keepLatestN(1), new GcOptions());
            assertTrue(r.deleted > 0, "ns 0x02 garbage must be collected");
            assertIntact(ns2.nodeStore(), ns2.rootsIndex().latest(), ns2Versions.get(2), "ns 0x02 latest");
        }
        try (RocksDbStateTrees ns1 = new RocksDbStateTrees(path, NamespaceOptions.keyPrefix((byte) 0x01))) {
            for (int v = 0; v < 3; v++) {
                assertIntact(ns1.nodeStore(), ns1.rootsIndex().get(v), ns1Versions.get(v), "ns 0x01 v" + v);
            }
        }
    }

    @Test
    void singleVersionCleanupOnKeyPrefixNamespaceLeavesOtherNamespaceIntact() throws Exception {
        String path = tmp.resolve("snap").toString();
        Map<String, byte[]> ns1Model = new LinkedHashMap<>();
        byte[] ns1Root;
        try (RocksDbStateTrees ns1 = new RocksDbStateTrees(path, NamespaceOptions.keyPrefix((byte) 0x01), StorageMode.SINGLE_VERSION)) {
            MpfTrie t = new MpfTrie(ns1.nodeStore());
            for (int i = 0; i < 10; i++) {
                t.put(b("a" + i), b("x" + i));
                ns1Model.put(hex("a" + i), b("x" + i));
            }
            ns1Root = t.getRootHash();
            ns1.putRootSnapshot(ns1Root);
        }
        try (RocksDbStateTrees ns2 = new RocksDbStateTrees(path, NamespaceOptions.keyPrefix((byte) 0x02), StorageMode.SINGLE_VERSION)) {
            MpfTrie t = new MpfTrie(ns2.nodeStore());
            for (int i = 0; i < 10; i++) {
                t.put(b("b" + i), b("y" + i));
                ns2.putRootSnapshot(t.getRootHash());
            }
            assertTrue(ns2.cleanupOrphanedNodes(new GcOptions()).deleted > 0);
        }
        try (RocksDbStateTrees ns1 = new RocksDbStateTrees(path, NamespaceOptions.keyPrefix((byte) 0x01), StorageMode.SINGLE_VERSION)) {
            assertIntact(ns1.nodeStore(), ns1Root, ns1Model, "ns 0x01 snapshot");
        }
    }

    @Test
    void rocksDbGcOnKeyPrefixNamespaceLeavesOtherNamespaceIntact() {
        String path = tmp.resolve("rgc-ns").toString();
        List<Map<String, byte[]>> ns1Versions;
        try (RocksDbStateTrees ns1 = new RocksDbStateTrees(path, NamespaceOptions.keyPrefix((byte) 0x01))) {
            ns1Versions = writeVersions(ns1, "a", 2, false);
        }
        try (RocksDbStateTrees ns2 = new RocksDbStateTrees(path, NamespaceOptions.keyPrefix((byte) 0x02))) {
            List<Map<String, byte[]>> ns2Versions = writeVersions(ns2, "b", 2, false);
            RocksDbGc.Report r = RocksDbGc.gcKeepLatest(ns2.nodeStore(), ns2.rootsIndex(), 1, new RocksDbGc.Options());
            assertTrue(r.deleted > 0);
            assertIntact(ns2.nodeStore(), ns2.rootsIndex().latest(), ns2Versions.get(1), "ns 0x02 latest");
        }
        try (RocksDbStateTrees ns1 = new RocksDbStateTrees(path, NamespaceOptions.keyPrefix((byte) 0x01))) {
            assertIntact(ns1.nodeStore(), ns1.rootsIndex().get(0), ns1Versions.get(0), "ns 0x01 v0");
            assertIntact(ns1.nodeStore(), ns1.rootsIndex().get(1), ns1Versions.get(1), "ns 0x01 v1");
        }
    }

    // ---------------------------------------------------------------- 2c RocksDbGc

    @Test
    void rocksDbGcKeepLatestKeepsTheLatestVersion() {
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve("rgc").toString())) {
            List<Map<String, byte[]>> versions = writeVersions(st, "k", 3, false);
            byte[] latest = st.rootsIndex().latest();
            long reachable = reach(st.nodeStore(), latest)[0];
            RocksDbGc.Report r = RocksDbGc.gcKeepLatest(st.nodeStore(), st.rootsIndex(), 1, new RocksDbGc.Options());
            assertTrue(r.deleted > 0);
            assertEquals(reachable, countNodeKeys(st.nodeStore()));
            assertIntact(st.nodeStore(), latest, versions.get(2), "latest");
        }
    }

    // ---------------------------------------------------------------- 2b + refcount bookkeeping

    @Test
    void gcToolPatternWithSessionKeepsLatestIntact() throws Exception {
        // tools/mpf/GcTool.generate shape: one batch per version holding nodes + root + refcounts.
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve("gctool").toString())) {
            MpfTrie trie = new MpfTrie(st.nodeStore());
            Random rnd = new Random(7);
            byte[][] keys = new byte[200][];
            for (int i = 0; i < keys.length; i++) {
                keys[i] = new byte[16];
                rnd.nextBytes(keys[i]);
            }
            Map<String, byte[]> model = new HashMap<>();
            RocksDbMptSession session = RocksDbMptSession.of(st);
            for (int v = 0; v < 5; v++) {
                session.write(() -> {
                    for (int i = 0; i < 20; i++) {
                        byte[] val = new byte[16];
                        rnd.nextBytes(val);
                        byte[] k = keys[rnd.nextInt(keys.length)];
                        trie.put(k, val);
                        model.put(com.bloxbean.cardano.client.util.HexUtil.encodeHexString(k), val);
                    }
                    st.putRootWithRefcount(trie.getRootHash());
                });
            }
            byte[] latest = st.rootsIndex().latest();
            GcReport r = new GcManager(st.nodeStore(), st.rootsIndex())
                    .runSync(new RefcountGcStrategy(), RetentionPolicy.keepLatestN(1), new GcOptions());
            assertTrue(r.deleted > 0);
            assertIntact(st.nodeStore(), latest, model, "latest after refcount GC");
        }
    }

    @Test
    void severalRootsInOneSessionBatchGetDistinctVersionsAndConsistentCounts() throws Exception {
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve("multi").toString())) {
            MpfTrie trie = new MpfTrie(st.nodeStore());
            Map<String, byte[]> model = new LinkedHashMap<>();
            long[] versions = new long[3];
            RocksDbMptSession.of(st).write(() -> {
                for (int v = 0; v < 3; v++) {
                    for (int i = 0; i < 5; i++) {
                        trie.put(b("k" + (v * 2 + i)), b("v" + v + i));
                        model.put(hex("k" + (v * 2 + i)), b("v" + v + i));
                    }
                    versions[v] = st.putRootWithRefcount(trie.getRootHash());
                }
            });
            assertArrayEquals(new long[]{0, 1, 2}, versions);
            assertEquals(2L, st.rootsIndex().lastVersion());
            byte[] latest = st.rootsIndex().latest();
            assertArrayEquals(latest, st.rootsIndex().get(2));

            new GcManager(st.nodeStore(), st.rootsIndex())
                    .runSync(new RefcountGcStrategy(), RetentionPolicy.keepLatestN(1), new GcOptions());
            assertIntact(st.nodeStore(), latest, model, "latest");
            // Exactly the latest version's nodes stay counted (no lost or duplicated increments in the batch)
            assertEquals(reach(st.nodeStore(), latest)[0], countRefcountKeys(st.nodeStore()));
        }
    }

    @Test
    void repeatedRefcountRunsNeverDeleteLiveNodes() throws Exception {
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve("repeat").toString())) {
            List<Map<String, byte[]>> versions = writeVersions(st, "k", 6, true);
            GcManager gc = new GcManager(st.nodeStore(), st.rootsIndex());
            for (int run = 0; run < 4; run++) {
                gc.runSync(new RefcountGcStrategy(), RetentionPolicy.keepLatestN(2), new GcOptions());
                assertIntact(st.nodeStore(), st.rootsIndex().get(5), versions.get(5), "v5 after run " + run);
                assertIntact(st.nodeStore(), st.rootsIndex().get(4), versions.get(4), "v4 after run " + run);
            }
            assertEquals(List.of(4L, 5L), new ArrayList<>(st.rootsIndex().listAll().keySet()));
        }
    }

    @Test
    void dropManyVersionsSharingNodesInOneRunCollectsAllOfThem() throws Exception {
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve("shared-drop").toString())) {
            List<Map<String, byte[]>> versions = writeVersions(st, "k", 6, true);
            byte[] latest = st.rootsIndex().latest();
            // Nodes reachable from several dropped versions must be decremented once per version, in one batch.
            new GcManager(st.nodeStore(), st.rootsIndex())
                    .runSync(new RefcountGcStrategy(), RetentionPolicy.keepLatestN(1), new GcOptions());
            assertIntact(st.nodeStore(), latest, versions.get(5), "latest");
            long reachable = reach(st.nodeStore(), latest)[0];
            // Refcount GC can only leak nodes that were never part of a recorded version (intermediate nodes
            // of a batch). Recorded-only garbage must all be gone: every remaining counted node is reachable.
            assertEquals(reachable, countRefcountKeys(st.nodeStore()));
        }
    }

    @Test
    void refcountGcRefusesToRunOnUntrackedVersions() throws Exception {
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve("untracked").toString())) {
            List<Map<String, byte[]>> versions = writeVersions(st, "k", 3, false); // plain rootsIndex.put
            long before = countNodeKeys(st.nodeStore());
            GcManager gc = new GcManager(st.nodeStore(), st.rootsIndex());
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> gc.runSync(new RefcountGcStrategy(), RetentionPolicy.keepLatestN(1), new GcOptions()));
            assertTrue(ex.getMessage().contains("OnDiskMarkSweepStrategy"));
            assertEquals(before, countNodeKeys(st.nodeStore()));
            for (int v = 0; v < 3; v++) assertIntact(st.nodeStore(), st.rootsIndex().get(v), versions.get(v), "v" + v);
        }
    }

    @Test
    void droppingAnUntrackedVersionDoesNotFreeNodesOfTrackedVersions() throws Exception {
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve("mixed").toString())) {
            MpfTrie trie = new MpfTrie(st.nodeStore());
            Map<String, byte[]> model = new LinkedHashMap<>();
            for (int i = 0; i < 20; i++) {
                trie.put(b("k" + i), b("v" + i));
                model.put(hex("k" + i), b("v" + i));
            }
            st.rootsIndex().put(st.rootsIndex().nextVersion(), trie.getRootHash()); // v0: not counted
            trie.put(b("k0"), b("changed"));
            model.put(hex("k0"), b("changed"));
            st.putRootWithRefcount(trie.getRootHash()); // v1: counted, shares most nodes with v0
            byte[] latest = trie.getRootHash();

            new GcManager(st.nodeStore(), st.rootsIndex())
                    .runSync(new RefcountGcStrategy(), RetentionPolicy.keepLatestN(1), new GcOptions());
            assertIntact(st.nodeStore(), latest, model, "latest");
        }
    }

    // ---------------------------------------------------------------- latest-root retention

    @ParameterizedTest
    @ValueSource(strings = {"marksweep", "refcount"})
    void policiesThatExcludeTheLatestVersionStillKeepIt(String strategyName) throws Exception {
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve("keep-" + strategyName).toString())) {
            List<Map<String, byte[]>> versions = writeVersions(st, "k", 4, "refcount".equals(strategyName));
            GcStrategy s = strategy(strategyName);
            // Policy keeps only v0; LATEST (v3) must survive anyway, also when the strategy is called directly.
            s.run(st.nodeStore(), st.rootsIndex(), RetentionPolicy.keepVersions(List.of(0L)), new GcOptions());
            assertIntact(st.nodeStore(), st.rootsIndex().latest(), versions.get(3), "latest");
            assertIntact(st.nodeStore(), st.rootsIndex().get(0), versions.get(0), "v0");
        }
    }

    // ---------------------------------------------------------------- mark-sweep details

    @Test
    void markSweepKeepsRefcountsOfLiveNodesAndRemovesThoseOfDeletedNodes() throws Exception {
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve("ms-ref").toString())) {
            List<Map<String, byte[]>> versions = writeVersions(st, "k", 3, true);
            byte[] latest = st.rootsIndex().latest();
            new GcManager(st.nodeStore(), st.rootsIndex())
                    .runSync(new OnDiskMarkSweepStrategy(), RetentionPolicy.keepLatestN(1), new GcOptions());
            assertIntact(st.nodeStore(), latest, versions.get(2), "latest");
            long nodes = countNodeKeys(st.nodeStore());
            assertEquals(reach(st.nodeStore(), latest)[0], nodes);
            assertEquals(nodes, countRefcountKeys(st.nodeStore()), "refcounts of kept nodes stay, others go");
        }
    }

    @Test
    void markSweepNeverSweepsNodesWrittenAfterItsSnapshot() throws Exception {
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve("ms-snap").toString())) {
            List<Map<String, byte[]>> versions = writeVersions(st, "k", 2, false);
            byte[] lateHash = new byte[32];
            Arrays.fill(lateHash, (byte) 0x5A);
            byte[] lateNode = st.nodeStore().get(st.rootsIndex().latest());
            // The policy runs after the snapshot is taken; a node written now must not be swept.
            RetentionPolicy writesDuringGc = index -> {
                st.nodeStore().put(lateHash, lateNode);
                return RetentionPolicy.keepLatestN(1).resolveRoots(index);
            };
            new GcManager(st.nodeStore(), st.rootsIndex())
                    .runSync(new OnDiskMarkSweepStrategy(), writesDuringGc, new GcOptions());
            assertNotNull(st.nodeStore().get(lateHash), "node committed after the snapshot was swept");
            assertIntact(st.nodeStore(), st.rootsIndex().latest(), versions.get(1), "latest");
        }
    }

    @Test
    void zeroRootIsRecordedWithoutRefcounts() throws Exception {
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve("zero").toString())) {
            long v = st.putRootWithRefcount(new byte[32]);
            assertEquals(0L, v);
            assertEquals(0, countRefcountKeys(st.nodeStore()));
            List<Map<String, byte[]>> versions = writeVersions(st, "k", 2, true);
            new GcManager(st.nodeStore(), st.rootsIndex())
                    .runSync(new RefcountGcStrategy(), RetentionPolicy.keepLatestN(1), new GcOptions());
            assertIntact(st.nodeStore(), st.rootsIndex().latest(), versions.get(1), "latest");
        }
    }
}
