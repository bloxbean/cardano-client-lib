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
import com.bloxbean.cardano.vds.mpf.internal.NodeRefParser;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.internal.DiskMarkSet;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.internal.RefcountLedger;
import com.bloxbean.cardano.vds.rocksdb.namespace.KeyPrefixer;
import com.bloxbean.cardano.vds.rocksdb.namespace.NamespaceOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

import java.nio.ByteBuffer;
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
    // ---------------------------------------------------------------- stores with legacy (pre-0.8.0) refcounts

    /** Pre-0.8.0 RefcountGcStrategy.incrementAll: counts read from the DB, children found through the DB only. */
    private static void legacyIncrementAll(RocksDB db, ColumnFamilyHandle cfNodes, byte[] root, WriteBatch wb, KeyPrefixer kp)
            throws RocksDBException {
        Deque<byte[]> q = new ArrayDeque<>(List.of(root));
        Set<String> seen = new HashSet<>();
        while (!q.isEmpty()) {
            byte[] h = q.removeFirst();
            if (!seen.add(Arrays.toString(h))) continue;
            byte[] v = db.get(cfNodes, kp.prefix(RefcountLedger.refKey(h)));
            long cnt = v == null || v.length != 8 ? 0 : ByteBuffer.wrap(v).getLong();
            wb.put(cfNodes, kp.prefix(RefcountLedger.refKey(h)), ByteBuffer.allocate(8).putLong(cnt + 1).array());
            byte[] enc = db.get(cfNodes, kp.prefix(h));
            if (enc == null) continue;
            for (byte[] ch : NodeRefParser.childRefs(enc)) q.addLast(ch);
        }
    }

    /** Pre-0.8.0 GcTool / test pattern: nodes, version and legacy increment in one batch (counts only the root). */
    private static void legacySameBatchVersion(RocksDbStateTrees st, MpfTrie trie, Map<String, byte[]> model, String tag) {
        try (WriteBatch wb = new WriteBatch(); WriteOptions wo = new WriteOptions()) {
            st.nodeStore().withBatch(wb, () -> {
                for (int i = 0; i < 6; i++) {
                    String k = "k" + Math.floorMod(tag.hashCode() + i, 30);
                    trie.put(b(k), b(tag + i));
                    model.put(hex(k), b(tag + i));
                }
                st.rootsIndex().withBatch(wb, () -> {
                    st.rootsIndex().put(st.rootsIndex().nextVersion(), trie.getRootHash());
                    return null;
                });
                legacyIncrementAll(st.db(), st.nodeStore().nodesHandle(), trie.getRootHash(), wb, st.nodeStore().keyPrefixer());
                return null;
            });
            st.db().write(wo, wb);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    /** Pre-0.8.0 RocksDbStateTrees.putRootWithRefcount: its own batch, legacy increment reading the DB. */
    private static void legacyPutRootWithRefcount(RocksDbStateTrees st, byte[] root) {
        try (WriteBatch wb = new WriteBatch(); WriteOptions wo = new WriteOptions()) {
            st.rootsIndex().withBatch(wb, () -> {
                st.rootsIndex().put(st.rootsIndex().nextVersion(), root);
                return null;
            });
            legacyIncrementAll(st.db(), st.nodeStore().nodesHandle(), root, wb, st.nodeStore().keyPrefixer());
            st.db().write(wo, wb);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void upgradedStoreWithRootOnlyCountsKeepsLatestIntact() throws Exception {
        // Review E1d: the same user code `session.write(() -> { trie.put..; st.putRootWithRefcount(root); })`
        // ran on the old library for v0..v2 (counting only the roots) and on the new one for v3.
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve("e1d").toString())) {
            MpfTrie trie = new MpfTrie(st.nodeStore());
            Map<String, byte[]> model = new LinkedHashMap<>();
            RocksDbMptSession session = RocksDbMptSession.of(st);
            for (int v = 0; v < 3; v++) {
                final int ver = v;
                session.write(() -> {
                    for (int i = 0; i < 8; i++) {
                        trie.put(b("k" + ((ver * 3 + i) % 25)), b("v" + ver + i));
                        model.put(hex("k" + ((ver * 3 + i) % 25)), b("v" + ver + i));
                    }
                    legacyPutRootWithRefcount(st, trie.getRootHash());
                });
            }
            session.write(() -> {
                trie.put(b("k1"), b("upgraded"));
                model.put(hex("k1"), b("upgraded"));
                st.putRootWithRefcount(trie.getRootHash());
            });
            byte[] latest = trie.getRootHash();
            GcReport report = new GcManager(st.nodeStore(), st.rootsIndex())
                    .runSync(new RefcountGcStrategy(), RetentionPolicy.keepLatestN(1), new GcOptions());
            assertIntact(st.nodeStore(), latest, model, "latest");
            assertTrue(report.protectedNodes > 0, "legacy counts must be detected as protected nodes");
            assertTrue(report.marked > 0);
            // Mark-sweep reclaims what refcount GC had to leave.
            new GcManager(st.nodeStore(), st.rootsIndex())
                    .runSync(new OnDiskMarkSweepStrategy(), RetentionPolicy.keepLatestN(1), new GcOptions());
            assertEquals(reach(st.nodeStore(), latest)[0], countNodeKeys(st.nodeStore()));
            assertIntact(st.nodeStore(), latest, model, "latest after mark-sweep");
        }
    }

    @Test
    void legacySameBatchVersionsThenNewVersionKeepLatestIntact() throws Exception {
        // Review E1: legacy same-batch versions, then the upgraded putRootWithRefcount, then refcount GC.
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve("e1").toString())) {
            MpfTrie trie = new MpfTrie(st.nodeStore());
            Map<String, byte[]> model = new LinkedHashMap<>();
            for (int i = 0; i < 30; i++) {
                trie.put(b("k" + i), b("base" + i));
                model.put(hex("k" + i), b("base" + i));
            }
            legacySameBatchVersion(st, trie, model, "a");
            legacySameBatchVersion(st, trie, model, "b");
            RocksDbMptSession.of(st).write(() -> {
                trie.put(b("k1"), b("new"));
                model.put(hex("k1"), b("new"));
                st.putRootWithRefcount(trie.getRootHash());
            });
            byte[] latest = trie.getRootHash();
            new GcManager(st.nodeStore(), st.rootsIndex())
                    .runSync(new RefcountGcStrategy(), RetentionPolicy.keepLatestN(1), new GcOptions());
            assertIntact(st.nodeStore(), latest, model, "latest");
        }
    }

    @Test
    void retainedLegacyVersionStaysIntact() throws Exception {
        // Review E1b: the legacy version is retained too (keepLatestN(2)); its root has a count, so the
        // untracked-root guard passes and only marking protects its children.
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve("e1b").toString())) {
            MpfTrie trie = new MpfTrie(st.nodeStore());
            Map<String, byte[]> model = new LinkedHashMap<>();
            legacySameBatchVersion(st, trie, model, "a");
            legacySameBatchVersion(st, trie, model, "b");
            Map<String, byte[]> modelB = new LinkedHashMap<>(model);
            byte[] rootB = trie.getRootHash();
            RocksDbMptSession.of(st).write(() -> {
                trie.put(b("zz"), b("new"));
                model.put(hex("zz"), b("new"));
                st.putRootWithRefcount(trie.getRootHash());
            });
            byte[] latest = trie.getRootHash();
            new GcManager(st.nodeStore(), st.rootsIndex())
                    .runSync(new RefcountGcStrategy(), RetentionPolicy.keepLatestN(2), new GcOptions());
            assertIntact(st.nodeStore(), rootB, modelB, "retained legacy version");
            assertIntact(st.nodeStore(), latest, model, "latest");
        }
    }

    @Test
    void pureLegacyRootOnlyStoreKeepsLatestIntact() throws Exception {
        // Review E1c: every version written with the legacy same-batch pattern, collected by the new refcount GC.
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve("e1c").toString())) {
            MpfTrie trie = new MpfTrie(st.nodeStore());
            Map<String, byte[]> model = new LinkedHashMap<>();
            for (String t : List.of("a", "b", "c", "d")) legacySameBatchVersion(st, trie, model, t);
            byte[] latest = trie.getRootHash();
            new GcManager(st.nodeStore(), st.rootsIndex())
                    .runSync(new RefcountGcStrategy(), RetentionPolicy.keepLatestN(1), new GcOptions());
            assertIntact(st.nodeStore(), latest, model, "latest");
        }
    }

    // ---------------------------------------------------------------- static helpers sharing one batch

    /** R0 = {a:1, b:2} counted and committed; R1 = {a:3, b:2} committed but not counted. The b leaf is shared. */
    private static byte[][] twoRootsSharingLeafB(RocksDbStateTrees st, Map<String, byte[]> modelR1) {
        MpfTrie trie = new MpfTrie(st.nodeStore());
        trie.put(b("a"), b("1"));
        trie.put(b("b"), b("2"));
        byte[] r0 = trie.getRootHash();
        st.putRootWithRefcount(r0);
        trie.put(b("a"), b("3"));
        byte[] r1 = trie.getRootHash();
        modelR1.put(hex("a"), b("3"));
        modelR1.put(hex("b"), b("2"));
        return new byte[][]{r0, r1};
    }

    @Test
    void staticIncrementThenDecrementInOneBatchKeepsSharedNode() throws Exception {
        // Codex review: incrementAll(R1, wb) then decrementAll(R0, wb) used fresh counts per call, so the decrement
        // read b's committed count 1 and deleted b although R1 still uses it.
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve("static-id").toString())) {
            Map<String, byte[]> modelR1 = new LinkedHashMap<>();
            byte[][] roots = twoRootsSharingLeafB(st, modelR1);
            RocksDbNodeStore ns = st.nodeStore();
            try (WriteBatch wb = new WriteBatch(); WriteOptions wo = new WriteOptions()) {
                RefcountGcStrategy.incrementAll(st.db(), ns.nodesHandle(), ns.nodesHandle(), roots[1], wb, ns.keyPrefixer());
                RefcountGcStrategy.decrementAll(st.db(), ns.nodesHandle(), ns.nodesHandle(), roots[0], wb, ns.keyPrefixer());
                st.db().write(wo, wb);
            }
            assertIntact(ns, roots[1], modelR1, "R1");
            assertEquals(reach(ns, roots[1])[0], countRefcountKeys(ns));
        }
    }

    @Test
    void staticDecrementThenIncrementInOneBatchRestoresDeletedNode() throws Exception {
        // Reverse order: the decrement stages b's deletion, the increment must write b back.
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve("static-di").toString())) {
            Map<String, byte[]> modelR1 = new LinkedHashMap<>();
            byte[][] roots = twoRootsSharingLeafB(st, modelR1);
            RocksDbNodeStore ns = st.nodeStore();
            try (WriteBatch wb = new WriteBatch(); WriteOptions wo = new WriteOptions()) {
                RefcountGcStrategy.decrementAll(st.db(), ns.nodesHandle(), ns.nodesHandle(), roots[0], wb, ns.keyPrefixer());
                RefcountGcStrategy.incrementAll(st.db(), ns.nodesHandle(), ns.nodesHandle(), roots[1], wb, ns.keyPrefixer());
                st.db().write(wo, wb);
            }
            assertIntact(ns, roots[1], modelR1, "R1");
            assertEquals(reach(ns, roots[1])[0], countRefcountKeys(ns));
        }
    }

    // ---------------------------------------------------------------- batches of other store instances

    @Test
    void putRootWithRefcountJoinsBatchOfAnotherStoreInstanceOfTheSameNamespace() throws Exception {
        // Review E6: the session ran on a second RocksDbNodeStore over the same DB/CF/namespace. The root used to be
        // committed on its own, so a failure later in the session left LATEST pointing at nodes never written.
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve("e6").toString())) {
            RocksDbNodeStore other = new RocksDbNodeStore(st.db(), st.nodeStore().nodesHandle(), st.nodeStore().keyPrefixer());
            MpfTrie trie = new MpfTrie(other);
            trie.put(b("a"), b("1"));
            st.putRootWithRefcount(trie.getRootHash());
            byte[] committed = trie.getRootHash();
            assertThrows(RuntimeException.class, () -> RocksDbMptSession.of(other).write(() -> {
                trie.put(b("b"), b("2"));
                st.putRootWithRefcount(trie.getRootHash());
                throw new IllegalStateException("app failure after recording the root");
            }));
            assertArrayEquals(committed, st.rootsIndex().latest(), "the failed batch must not move LATEST");
            assertEquals(0L, st.rootsIndex().lastVersion());
            assertNotNull(st.nodeStore().get(st.rootsIndex().latest()));

            // A successful session through the other instance commits root and nodes together.
            trie.setRootHash(committed); // the in-memory root still points at the discarded batch
            long v = RocksDbMptSession.of(other).write(() -> {
                trie.put(b("b"), b("2"));
                return st.putRootWithRefcount(trie.getRootHash());
            });
            assertEquals(1L, v);
            Map<String, byte[]> model = new LinkedHashMap<>();
            model.put(hex("a"), b("1"));
            model.put(hex("b"), b("2"));
            assertIntact(st.nodeStore(), st.rootsIndex().latest(), model, "latest");
            new GcManager(st.nodeStore(), st.rootsIndex())
                    .runSync(new RefcountGcStrategy(), RetentionPolicy.keepLatestN(1), new GcOptions());
            assertEquals(reach(st.nodeStore(), st.rootsIndex().latest())[0], countRefcountKeys(st.nodeStore()));
            assertIntact(st.nodeStore(), st.rootsIndex().latest(), model, "latest after GC");
        }
    }

    @Test
    void putRootWithRefcountRefusesABatchOfAnotherNamespace() {
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve("xns").toString())) {
            RocksDbNodeStore otherNs = new RocksDbNodeStore(st.db(), st.nodeStore().nodesHandle(), new KeyPrefixer((byte) 0x07));
            MpfTrie trie = new MpfTrie(st.nodeStore());
            RuntimeException ex = assertThrows(RuntimeException.class, () -> RocksDbMptSession.of(otherNs).write(() -> {
                trie.put(b("a"), b("1"));
                st.putRootWithRefcount(trie.getRootHash());
            }));
            Throwable cause = ex instanceof IllegalStateException ? ex : ex.getCause();
            assertInstanceOf(IllegalStateException.class, cause);
            assertNull(st.rootsIndex().latest());
        }
    }

    // ---------------------------------------------------------------- mark-sweep snapshot limits, leftovers

    @Test
    void markSweepSnapshotDoesNotProtectRePutsOfExistingKeys() throws Exception {
        // Review E4, documented limitation: nodes are content-addressed, so re-putting an existing node key during
        // GC is invisible to the snapshot and the node can still be swept. Writers must be paused during GC.
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve("e4").toString())) {
            MpfTrie trie = new MpfTrie(st.nodeStore());
            trie.put(b("a"), b("1"));
            byte[] oldRoot = trie.getRootHash();
            byte[] oldNode = st.nodeStore().get(oldRoot);
            st.rootsIndex().put(0, oldRoot);
            trie.put(b("a"), b("2"));
            st.rootsIndex().put(1, trie.getRootHash());
            RetentionPolicy rePutsDuringGc = index -> {
                st.nodeStore().put(oldRoot, oldNode);
                return RetentionPolicy.keepLatestN(1).resolveRoots(index);
            };
            new GcManager(st.nodeStore(), st.rootsIndex()).runSync(new OnDiskMarkSweepStrategy(), rePutsDuringGc, new GcOptions());
            assertNull(st.nodeStore().get(oldRoot));
        }
    }

    @Test
    void leftoverMarkColumnFamiliesAreDroppedOnOpen() throws Exception {
        String path = tmp.resolve("stale").toString();
        try (RocksDbStateTrees st = new RocksDbStateTrees(path)) {
            ColumnFamilyHandle stale = st.db().createColumnFamily(new ColumnFamilyDescriptor(
                    (DiskMarkSet.CF_PREFIX + "12345").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            st.db().put(stale, b("x"), b("y"));
            stale.close(); // simulates a crash before the GC run dropped it
        }
        try (RocksDbStateTrees st = new RocksDbStateTrees(path)) {
            assertNotNull(st.nodeStore());
        }
        try (org.rocksdb.Options o = new org.rocksdb.Options()) {
            for (byte[] cf : RocksDB.listColumnFamilies(o, path)) {
                assertFalse(DiskMarkSet.isMarkSetColumnFamily(cf), "stale " + new String(cf));
            }
        }
    }

    @Test
    void gcRunsLeaveNoMarkColumnFamilies() throws Exception {
        String path = tmp.resolve("no-leftover").toString();
        try (RocksDbStateTrees st = new RocksDbStateTrees(path)) {
            writeVersions(st, "k", 3, true);
            GcManager gc = new GcManager(st.nodeStore(), st.rootsIndex());
            gc.runSync(new RefcountGcStrategy(), RetentionPolicy.keepLatestN(1), new GcOptions());
            gc.runSync(new OnDiskMarkSweepStrategy(), RetentionPolicy.keepLatestN(1), new GcOptions());
        }
        try (org.rocksdb.Options o = new org.rocksdb.Options()) {
            for (byte[] cf : RocksDB.listColumnFamilies(o, path)) {
                assertFalse(DiskMarkSet.isMarkSetColumnFamily(cf), "leftover " + new String(cf));
            }
        }
    }
    // ---------------------------------------------------------------- round 2: column family names

    private static List<String> columnFamilies(String path) throws RocksDBException {
        List<String> names = new ArrayList<>();
        try (org.rocksdb.Options o = new org.rocksdb.Options()) {
            for (byte[] n : RocksDB.listColumnFamilies(o, path)) names.add(new String(n, java.nio.charset.StandardCharsets.UTF_8));
        }
        return names;
    }

    @Test
    void applicationColumnFamilyStartingWithMarksIsKeptOnOpen() throws Exception {
        // Review R1: a user CF "marks_audit" used to be dropped (with its data) by the leftover cleanup.
        RocksDB.loadLibrary();
        String path = tmp.resolve("r1").toString();
        List<ColumnFamilyDescriptor> d = new ArrayList<>(List.of(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY),
                new ColumnFamilyDescriptor("marks_audit".getBytes())));
        List<ColumnFamilyHandle> hs = new ArrayList<>();
        try (org.rocksdb.DBOptions o = new org.rocksdb.DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true);
             RocksDB db = RocksDB.open(o, path, d, hs)) {
            db.put(hs.get(1), b("x"), b("app data"));
            for (ColumnFamilyHandle h : hs) h.close();
        }
        try (RocksDbStateTrees st = new RocksDbStateTrees(path)) {
            assertNotNull(st.nodeStore());
        }
        assertTrue(columnFamilies(path).contains("marks_audit"));
        List<ColumnFamilyDescriptor> all = new ArrayList<>();
        for (String n : columnFamilies(path)) all.add(new ColumnFamilyDescriptor(n.getBytes()));
        hs.clear();
        try (org.rocksdb.DBOptions o = new org.rocksdb.DBOptions(); RocksDB db = RocksDB.open(o, path, all, hs)) {
            int idx = columnFamilies(path).indexOf("marks_audit");
            assertArrayEquals(b("app data"), db.get(hs.get(idx), b("x")));
            for (ColumnFamilyHandle h : hs) h.close();
        }
    }

    @Test
    void namespaceWithColumnFamilyPrefixMarksSurvivesReopen() throws Exception {
        // Review R1b: NamespaceOptions.columnFamily("marks") gives marks_nodes / marks_roots, which used to be dropped
        // right after open (crash in validateAndPersistStorageMode, data loss on reopen).
        String path = tmp.resolve("r1b").toString();
        NamespaceOptions ns = NamespaceOptions.columnFamily("marks");
        byte[] root;
        try (RocksDbStateTrees st = new RocksDbStateTrees(path, ns, StorageMode.MULTI_VERSION)) {
            MpfTrie trie = new MpfTrie(st.nodeStore());
            RocksDbMptSession.of(st).write(() -> {
                trie.put(b("a"), b("1"));
                st.putRootWithRefcount(trie.getRootHash());
            });
            root = trie.getRootHash();
        }
        try (RocksDbStateTrees st = new RocksDbStateTrees(path, ns, StorageMode.MULTI_VERSION)) {
            assertArrayEquals(root, st.rootsIndex().latest());
            Map<String, byte[]> model = new LinkedHashMap<>();
            model.put(hex("a"), b("1"));
            assertIntact(st.nodeStore(), root, model, "marks_ namespace after reopen");
        }
        assertTrue(columnFamilies(path).containsAll(List.of("marks_nodes", "marks_roots")));
    }

    @Test
    void legacyMarkColumnFamiliesFromPre6AreStillDropped() throws Exception {
        String path = tmp.resolve("legacy-marks").toString();
        try (RocksDbStateTrees st = new RocksDbStateTrees(path)) {
            st.db().createColumnFamily(new ColumnFamilyDescriptor("marks_98765".getBytes())).close();
        }
        try (RocksDbStateTrees st = new RocksDbStateTrees(path)) {
            assertNotNull(st.nodeStore());
        }
        assertFalse(columnFamilies(path).contains("marks_98765"));
    }

    @Test
    void reservedColumnFamilyPrefixIsRejected() {
        String path = tmp.resolve("reserved").toString();
        assertThrows(IllegalArgumentException.class,
                () -> new RocksDbStateTrees(path, NamespaceOptions.columnFamily("__vds_gc_marks_1")));
    }

    @Test
    void callerManagedDatabaseCanCleanUpLeftovers() throws Exception {
        // Review R4: a crashed refcount GC leaves a temporary CF; a caller that opens RocksDB itself must open all
        // column families (listColumnFamilies) and can then drop the leftovers.
        RocksDB.loadLibrary();
        String path = tmp.resolve("caller").toString();
        List<ColumnFamilyHandle> hs = new ArrayList<>();
        try (org.rocksdb.DBOptions o = new org.rocksdb.DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true);
             RocksDB db = RocksDB.open(o, path, List.of(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY),
                     new ColumnFamilyDescriptor("n".getBytes())), hs)) {
            db.createColumnFamily(new ColumnFamilyDescriptor((DiskMarkSet.CF_PREFIX + "42").getBytes())).close();
            for (ColumnFamilyHandle h : hs) h.close();
        }
        List<ColumnFamilyDescriptor> all = new ArrayList<>();
        for (String n : columnFamilies(path)) all.add(new ColumnFamilyDescriptor(n.getBytes()));
        hs.clear();
        try (org.rocksdb.DBOptions o = new org.rocksdb.DBOptions(); RocksDB db = RocksDB.open(o, path, all, hs)) {
            assertEquals(1, GcManager.dropLeftoverGcColumnFamilies(db, hs));
            assertEquals(2, hs.size());
            for (ColumnFamilyHandle h : hs) h.close();
        }
        assertEquals(List.of("default", "n"), columnFamilies(path));
    }

    // ---------------------------------------------------------------- round 2: static helpers and batch reuse

    @Test
    void staticHelpersWithABatchReusedAfterWriteAndClear() throws Exception {
        // Review R6: incrementAll(r1, wb); write; clear; r2 counted elsewhere; decrementAll(r1, wb) used stale counts.
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve("r6").toString())) {
            RocksDB db = st.db();
            ColumnFamilyHandle cf = st.nodeStore().nodesHandle();
            KeyPrefixer kp = st.nodeStore().keyPrefixer();
            MpfTrie trie = new MpfTrie(st.nodeStore());
            for (int i = 0; i < 20; i++) trie.put(b("k" + i), b("v" + i));
            byte[] r1 = trie.getRootHash();
            Map<String, byte[]> m2 = new LinkedHashMap<>();
            for (int i = 0; i < 20; i++) m2.put(hex("k" + i), b("v" + i));
            trie.put(b("k1"), b("changed"));
            m2.put(hex("k1"), b("changed"));
            byte[] r2 = trie.getRootHash();
            try (WriteBatch wb = new WriteBatch(); WriteOptions wo = new WriteOptions()) {
                RefcountGcStrategy.incrementAll(db, cf, cf, r1, wb, kp);
                db.write(wo, wb);
                wb.clear();
                try (WriteBatch other = new WriteBatch()) {
                    RefcountGcStrategy.incrementAll(db, cf, cf, r2, other, kp);
                    db.write(wo, other);
                }
                RefcountGcStrategy.decrementAll(db, cf, cf, r1, wb, kp);
                db.write(wo, wb);
            }
            assertIntact(st.nodeStore(), r2, m2, "r2");
            assertEquals(reach(st.nodeStore(), r2)[0], countRefcountKeys(st.nodeStore()));
        }
    }

    /** Root R recorded twice (count 2 on every node); returns R and fills its model. */
    private static byte[] rootCountedTwice(RocksDbStateTrees st, Map<String, byte[]> model) {
        MpfTrie trie = new MpfTrie(st.nodeStore());
        for (int i = 0; i < 12; i++) {
            trie.put(b("k" + i), b("v" + i));
            model.put(hex("k" + i), b("v" + i));
        }
        st.putRootWithRefcount(trie.getRootHash());
        st.putRootWithRefcount(trie.getRootHash());
        return trie.getRootHash();
    }

    @Test
    void staticDecrementAfterClearingTheBatchBeforeItsFirstCommit() throws Exception {
        // Codex round 2: decrementAll(R, wb); wb.clear(); decrementAll(R, wb) once; commit -> R must keep count 1.
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve("clear").toString())) {
            Map<String, byte[]> model = new LinkedHashMap<>();
            byte[] r = rootCountedTwice(st, model);
            RocksDbNodeStore ns = st.nodeStore();
            try (WriteBatch wb = new WriteBatch(); WriteOptions wo = new WriteOptions()) {
                RefcountGcStrategy.decrementAll(st.db(), ns.nodesHandle(), ns.nodesHandle(), r, wb, ns.keyPrefixer());
                wb.clear();
                RefcountGcStrategy.decrementAll(st.db(), ns.nodesHandle(), ns.nodesHandle(), r, wb, ns.keyPrefixer());
                st.db().write(wo, wb);
            }
            assertIntact(ns, r, model, "R after one effective decrement");
            assertEquals(reach(ns, r)[0], countRefcountKeys(ns));
        }
    }

    @Test
    void staticDecrementAfterRollingBackToASavePoint() throws Exception {
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve("savepoint").toString())) {
            Map<String, byte[]> model = new LinkedHashMap<>();
            byte[] r = rootCountedTwice(st, model);
            RocksDbNodeStore ns = st.nodeStore();
            try (WriteBatch wb = new WriteBatch(); WriteOptions wo = new WriteOptions()) {
                wb.setSavePoint();
                RefcountGcStrategy.decrementAll(st.db(), ns.nodesHandle(), ns.nodesHandle(), r, wb, ns.keyPrefixer());
                wb.rollbackToSavePoint();
                RefcountGcStrategy.decrementAll(st.db(), ns.nodesHandle(), ns.nodesHandle(), r, wb, ns.keyPrefixer());
                st.db().write(wo, wb);
            }
            assertIntact(ns, r, model, "R after one effective decrement");
            assertEquals(reach(ns, r)[0], countRefcountKeys(ns));
        }
    }

    // ---------------------------------------------------------------- round 2: larger store, repeated GC

    @Test
    void largerStoreRepeatedRefcountGcThenMarkSweepKeepsRetainedVersions() throws Exception {
        // Review R3 (scaled down): 20k keys over 20 versions, then updates with refcount GC each version, then
        // mark-sweep. The last 5 versions must stay intact and consistent counts never need protection.
        try (RocksDbStateTrees st = new RocksDbStateTrees(tmp.resolve("r3").toString())) {
            MpfTrie trie = new MpfTrie(st.nodeStore());
            Random rnd = new Random(1);
            Map<String, byte[]> model = new HashMap<>();
            Deque<Map<String, byte[]>> recent = new ArrayDeque<>();
            Deque<Long> recentVersions = new ArrayDeque<>();
            GcManager gm = new GcManager(st.nodeStore(), st.rootsIndex());
            for (int v = 0; v < 40; v++) {
                final int vv = v;
                long ver = RocksDbMptSession.of(st).write(() -> {
                    for (int i = 0; i < (vv < 20 ? 1000 : 100); i++) {
                        String k = vv < 20 ? "key-" + (vv * 1000 + i) : "key-" + rnd.nextInt(20_000);
                        byte[] val = b("val-" + vv + "-" + i);
                        trie.put(b(k), val);
                        model.put(hex(k), val);
                    }
                    return st.putRootWithRefcount(trie.getRootHash());
                });
                recent.addLast(new HashMap<>(model));
                recentVersions.addLast(ver);
                if (recent.size() > 5) {
                    recent.removeFirst();
                    recentVersions.removeFirst();
                }
                if (v >= 20) {
                    GcReport r = gm.runSync(new RefcountGcStrategy(), RetentionPolicy.keepLatestN(5), new GcOptions());
                    assertEquals(0, r.protectedNodes, "consistent counts must never need protection");
                }
            }
            gm.runSync(new OnDiskMarkSweepStrategy(), RetentionPolicy.keepLatestN(5), new GcOptions());
            Iterator<Long> vs = recentVersions.iterator();
            for (Map<String, byte[]> m : recent) {
                long v = vs.next();
                assertIntact(st.nodeStore(), st.rootsIndex().get(v), m, "v" + v);
            }
        }
    }
}
