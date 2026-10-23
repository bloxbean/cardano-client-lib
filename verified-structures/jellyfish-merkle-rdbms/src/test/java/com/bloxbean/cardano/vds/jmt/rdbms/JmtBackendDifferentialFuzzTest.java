package com.bloxbean.cardano.vds.jmt.rdbms;

import com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.vds.jmt.JmtProof;
import com.bloxbean.cardano.vds.jmt.rocksdb.RocksDbJmtStore;
import com.bloxbean.cardano.vds.jmt.store.InMemoryJmtStore;
import com.bloxbean.cardano.vds.jmt.store.JmtAccessCoordinator;
import com.bloxbean.cardano.vds.jmt.store.JmtStore;
import com.bloxbean.cardano.vds.rdbms.common.DbConfig;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import org.rocksdb.Env;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksMemEnv;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Coverage-guided differential traces: the same history (batches, version gaps, replays, prunes, truncations) is
 * applied to the in-memory, RocksDB and H2 stores, and every outcome must be identical: roots, thrown exception
 * types, the latest root, the prune watermark, {@code get(key, v)} answers or rejections, and proof bytes. The
 * in-memory store itself is checked against a model by {@code JmtFuzzTest} in the core module.
 */
class JmtBackendDifferentialFuzzTest {

    /** Inputs per shared database before it is replaced; each input gets a fresh namespace in it. */
    private static final int INPUTS_PER_DATABASE = 64;

    private static Path rocksDir;
    private static RocksDB rocksDb;
    private static RocksMemEnv rocksEnv;
    private static DbConfig h2;
    private static int used = INPUTS_PER_DATABASE;

    @FuzzTest(maxDuration = "60s")
    void historiesBehaveIdenticallyOnEveryBackend(FuzzedDataProvider data) throws Exception {
        // Opening RocksDB and H2 per input costs ~150 ms; a fresh namespace in shared databases costs ~1 ms.
        rotateDatabasesIfNeeded();
        int ns = ++used;
        try (InMemoryJmtStore memory = new InMemoryJmtStore();
             RocksDbJmtStore rocks = RocksDbJmtStore.attach(rocksDb, RocksDbJmtStore.Options.builder()
                     .namespace("fuzz" + ns).enableRollbackIndex(true)
                     .syncOnCommit(false).syncOnPrune(false).syncOnTruncate(false).build(), new JmtAccessCoordinator());
             RdbmsJmtStore rdbms = new RdbmsJmtStore(h2, (byte) ns)) {
            List<JmtStore> stores = List.of(memory, rocks, rdbms);
            List<JellyfishMerkleTree> trees = new ArrayList<>();
            for (JmtStore s : stores) trees.add(new JellyfishMerkleTree(s));
            run(data, stores, trees);
        }
    }

    private static synchronized void rotateDatabasesIfNeeded() throws Exception {
        if (used < INPUTS_PER_DATABASE) return;
        closeDatabases();
        rocksDir = Files.createTempDirectory("jmt-diff-fuzz-");
        // In-memory RocksDB environment: creating a namespace's column families then needs no disk syncs.
        rocksEnv = new RocksMemEnv(Env.getDefault());
        try (org.rocksdb.Options options = new org.rocksdb.Options().setCreateIfMissing(true).setEnv(rocksEnv)) {
            rocksDb = RocksDB.open(options, rocksDir.toString());
        }
        h2 = DbConfig.builder().simpleJdbcUrl("jdbc:h2:mem:jmt_diff_"
                + UUID.randomUUID().toString().replace('-', '_') + ";DB_CLOSE_DELAY=-1").build();
        RdbmsJmtSchemaInstaller.createIfNotExists(h2);
        used = 0;
        if (!hookInstalled) {
            hookInstalled = true;
            Runtime.getRuntime().addShutdownHook(new Thread(JmtBackendDifferentialFuzzTest::closeQuietly));
        }
    }

    private static boolean hookInstalled;

    private static synchronized void closeDatabases() throws IOException {
        if (h2 != null) h2.close();
        if (rocksDb != null) rocksDb.close();
        if (rocksEnv != null) rocksEnv.close();
        rocksEnv = null;
        if (rocksDir != null) deleteRecursively(rocksDir);
        h2 = null;
        rocksDb = null;
        rocksDir = null;
    }

    private static void closeQuietly() {
        try {
            closeDatabases();
        } catch (IOException ignored) {
            // Best effort cleanup of a temporary directory at JVM exit.
        }
    }

    private static void run(FuzzedDataProvider data, List<JmtStore> stores, List<JellyfishMerkleTree> trees) {
        int poolSize = data.consumeInt(1, 32);
        List<byte[]> pool = new ArrayList<>();
        for (int i = 0; i < poolSize; i++) pool.add(bytes("k" + i + ":" + data.consumeAsciiString(2)));
        int steps = data.consumeInt(1, 16);
        for (int step = 0; step < steps && data.remainingBytes() > 0; step++) {
            long latest = stores.get(0).latestRoot().map(JmtStore.VersionedRoot::version).orElse(-1L);
            int op = data.consumeInt(0, 9);
            if (op == 1 && latest >= 0) {
                long horizon = data.consumeLong(0, latest);
                same("pruneUpTo(" + horizon + ")", stores.size(), i -> String.valueOf(trees.get(i).pruneUpTo(horizon) > 0));
            } else if (op == 2 && latest >= 0) {
                long target = data.consumeLong(0, latest);
                same("truncateAfter(" + target + ")", stores.size(), i -> {
                    stores.get(i).truncateAfter(target);
                    return "ok";
                });
            } else {
                long version = op == 0 && latest >= 0 ? latest
                        : latest < 0 ? data.consumeLong(0, 2) : latest + data.consumeLong(1, 3);
                Map<byte[], byte[]> updates = new LinkedHashMap<>();
                Map<String, byte[]> dedup = new LinkedHashMap<>();
                int n = data.consumeInt(0, 6);
                for (int i = 0; i < n; i++) {
                    dedup.put(new String(pool.get(data.consumeInt(0, poolSize - 1)), StandardCharsets.UTF_8), data.consumeBytes(3));
                }
                dedup.forEach((k, v) -> updates.put(bytes(k), v));
                same("put(" + version + ")", stores.size(), i -> {
                    JellyfishMerkleTree.CommitResult r = trees.get(i).put(version, updates);
                    return hex(r.rootHash()) + " nodes=" + r.nodes().size() + " stale=" + r.staleNodes().size();
                });
            }

            same("latest", stores.size(), i -> stores.get(i).latestRoot()
                    .map(r -> r.version() + ":" + hex(r.rootHash())).orElse("none"));
            same("watermark", stores.size(), i -> stores.get(i).pruneWatermark().toString());
            long top = Math.max(latest, 0) + 2;
            for (int probe = 0; probe < 3; probe++) {
                byte[] key = probe == 2 ? bytes("never-written") : pool.get(data.consumeInt(0, poolSize - 1));
                long v = data.consumeLong(0, top);
                same("get(" + v + ")", stores.size(), i -> trees.get(i).get(key, v).map(JmtBackendDifferentialFuzzTest::hex).orElse("absent"));
                same("rootHash(" + v + ")", stores.size(), i -> stores.get(i).rootHash(v).map(JmtBackendDifferentialFuzzTest::hex).orElse("none"));
                same("proof(" + v + ")", stores.size(), i -> trees.get(i).getProofWire(key, v).map(JmtBackendDifferentialFuzzTest::hex).orElse("none"));
                same("proofType(" + v + ")", stores.size(), i -> trees.get(i).getProof(key, v).map(JmtProof::type).map(Enum::name).orElse("none"));
            }
        }
    }

    /** Runs the action on every backend and requires the same result or the same exception type. */
    private static void same(String what, int backends, IndexedAction action) {
        List<String> outcomes = new ArrayList<>(backends);
        for (int i = 0; i < backends; i++) {
            String outcome;
            try {
                outcome = action.apply(i);
            } catch (RuntimeException e) {
                outcome = "threw " + e.getClass().getSimpleName();
            }
            outcomes.add(outcome);
        }
        for (int i = 1; i < backends; i++) {
            assertEquals(outcomes.get(0), outcomes.get(i), what + " differs on backend " + i + " " + outcomes);
        }
    }

    @FunctionalInterface
    private interface IndexedAction {
        String apply(int backend);
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String hex(byte[] b) {
        return HexFormat.of().formatHex(b);
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) Files.deleteIfExists(p);
        }
    }
}
