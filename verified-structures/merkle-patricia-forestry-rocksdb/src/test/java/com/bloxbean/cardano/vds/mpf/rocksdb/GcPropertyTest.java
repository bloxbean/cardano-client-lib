package com.bloxbean.cardano.vds.mpf.rocksdb;

import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.vds.mpf.MpfTrie;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.GcManager;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.GcOptions;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.GcStrategy;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.RetentionPolicy;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.strategy.OnDiskMarkSweepStrategy;
import com.bloxbean.cardano.vds.mpf.rocksdb.gc.strategy.RefcountGcStrategy;
import com.bloxbean.cardano.vds.rocksdb.namespace.NamespaceOptions;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.bloxbean.cardano.vds.mpf.rocksdb.GcTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * GC safety over random histories in a shared RocksDB with several key-prefix namespaces.
 *
 * <p>For every namespace a random number of versions is written (random puts and deletes over a small key space,
 * so versions share nodes). Then random namespaces are collected, with a random strategy and retention, one or
 * more times. Afterwards every retained version of a collected namespace, and every version of every other
 * namespace, must be fully readable with the exact expected content; mark-sweep must have removed every node no
 * retained version reaches, and refcount GC every counted node no retained version reaches. Strategies are not
 * mixed on a namespace (unsupported); mark-sweep does run on namespaces that also hold refcounts.</p>
 */
class GcPropertyTest {

    private static final class Ns {
        final byte id;
        final boolean refcount;       // versions recorded with putRootWithRefcount
        final boolean refcountGc;     // collected with RefcountGcStrategy (else mark-sweep); never mixed
        final List<Map<String, byte[]>> models = new ArrayList<>();
        int keepLatest = -1; // -1: never collected

        Ns(byte id, boolean refcount, boolean refcountGc) {
            this.id = id;
            this.refcount = refcount;
            this.refcountGc = refcount && refcountGc;
        }
    }

    @Property(tries = 25)
    void gcNeverLosesRetainedData(@ForAll long seed,
                                  @ForAll @IntRange(min = 1, max = 3) int namespaceCount,
                                  @ForAll @IntRange(min = 1, max = 3) int gcRounds) throws Exception {
        Random rnd = new Random(seed);
        Path dir = Files.createTempDirectory("mpf-gc-prop-");
        String path = dir.toString();
        try {
            List<Ns> namespaces = new ArrayList<>();
            List<Integer> ids = new ArrayList<>(List.of(0, 1, 2, 3, 4));
            Collections.shuffle(ids, rnd);
            for (int n = 0; n < namespaceCount; n++) namespaces.add(new Ns((byte) (int) ids.get(n), rnd.nextBoolean(), rnd.nextBoolean()));

            for (Ns ns : namespaces) writeHistory(path, ns, rnd);

            for (int round = 0; round < gcRounds; round++) {
                for (Ns ns : namespaces) {
                    if (rnd.nextInt(3) == 0) continue; // leave some namespaces alone
                    int keep = 1 + rnd.nextInt(3);
                    ns.keepLatest = ns.keepLatest < 0 ? keep : Math.min(ns.keepLatest, keep);
                    try (RocksDbStateTrees st = open(path, ns)) {
                        GcStrategy strategy = ns.refcountGc ? new RefcountGcStrategy() : new OnDiskMarkSweepStrategy();
                        new GcManager(st.nodeStore(), st.rootsIndex())
                                .runSync(strategy, RetentionPolicy.keepLatestN(keep), new GcOptions());
                    }
                }
            }

            for (Ns ns : namespaces) verify(path, ns, seed);
        } finally {
            deleteRecursively(dir);
        }
    }

    private static RocksDbStateTrees open(String path, Ns ns) {
        return new RocksDbStateTrees(path, NamespaceOptions.keyPrefix(ns.id));
    }

    private static void writeHistory(String path, Ns ns, Random rnd) {
        try (RocksDbStateTrees st = open(path, ns)) {
            MpfTrie trie = new MpfTrie(st.nodeStore());
            RocksDbMptSession session = RocksDbMptSession.of(st);
            Map<String, byte[]> model = new LinkedHashMap<>();
            int versions = 1 + rnd.nextInt(6);
            for (int v = 0; v < versions; v++) {
                int ops = 1 + rnd.nextInt(8);
                List<Object[]> plan = new ArrayList<>();
                for (int i = 0; i < ops; i++) {
                    byte[] key = b("ns" + ns.id + "-k" + rnd.nextInt(25));
                    byte[] value = rnd.nextInt(4) == 0 ? null : b("v" + rnd.nextInt(1000));
                    plan.add(new Object[]{key, value});
                }
                Runnable apply = () -> {
                    for (Object[] op : plan) {
                        byte[] k = (byte[]) op[0], val = (byte[]) op[1];
                        if (val == null) {
                            trie.delete(k);
                            model.remove(HexUtil.encodeHexString(k));
                        } else {
                            trie.put(k, val);
                            model.put(HexUtil.encodeHexString(k), val);
                        }
                    }
                };
                if (ns.refcount) {
                    session.write(() -> {
                        apply.run();
                        if (trie.getRootHash() != null) st.putRootWithRefcount(trie.getRootHash());
                    });
                    if (trie.getRootHash() == null) continue;
                } else {
                    session.write(apply);
                    if (trie.getRootHash() == null) continue;
                    st.rootsIndex().put(st.rootsIndex().nextVersion(), trie.getRootHash());
                }
                ns.models.add(new LinkedHashMap<>(model));
            }
        }
    }

    private static void verify(String path, Ns ns, long seed) {
        try (RocksDbStateTrees st = open(path, ns)) {
            NavigableMap<Long, byte[]> listed = st.rootsIndex().listAll();
            int versions = ns.models.size();
            if (versions == 0) return;
            int firstRetained = ns.keepLatest < 0 ? 0 : Math.max(0, versions - ns.keepLatest);
            Set<String> retainedNodes = new HashSet<>();
            for (int v = firstRetained; v < versions; v++) {
                byte[] root = st.rootsIndex().get(v);
                assertNotNull(root, "seed " + seed + " ns " + ns.id + ": missing index entry v" + v);
                assertIntact(st.nodeStore(), root, ns.models.get(v), "seed " + seed + " ns " + ns.id + " v" + v);
                collect(st.nodeStore(), root, retainedNodes);
            }
            for (Long v : listed.keySet()) {
                assertTrue(v < versions, "seed " + seed + " ns " + ns.id + ": unexpected version " + v);
            }
            if (ns.keepLatest >= 0) {
                long nodes = countNodeKeys(st.nodeStore());
                long counted = countRefcountKeys(st.nodeStore());
                assertTrue(nodes >= retainedNodes.size());
                // Counted nodes always belong to a recorded version; after GC only retained ones may stay counted.
                assertTrue(counted <= retainedNodes.size(),
                        "seed " + seed + " ns " + ns.id + ": counted garbage survived GC (" + counted + " > " + retainedNodes.size() + ")");
                if (!ns.refcountGc) {
                    assertEquals(retainedNodes.size(), nodes, "seed " + seed + " ns " + ns.id + ": mark-sweep left garbage");
                }
            }
        }
    }

    private static void collect(RocksDbNodeStore store, byte[] root, Set<String> out) {
        Deque<byte[]> q = new ArrayDeque<>(List.of(root));
        while (!q.isEmpty()) {
            byte[] h = q.removeFirst();
            if (!out.add(HexUtil.encodeHexString(h))) continue;
            byte[] enc = store.get(h);
            if (enc != null) q.addAll(com.bloxbean.cardano.vds.mpf.internal.NodeRefParser.childRefs(enc));
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }
}
