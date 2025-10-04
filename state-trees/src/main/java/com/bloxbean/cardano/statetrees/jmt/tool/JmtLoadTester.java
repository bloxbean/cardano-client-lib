package com.bloxbean.cardano.statetrees.jmt.tool;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTreeStore;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTreeStoreConfig;
import com.bloxbean.cardano.statetrees.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.store.InMemoryJmtStore;
import com.bloxbean.cardano.statetrees.jmt.store.JmtStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Simple CLI harness to stress-test the streaming JMT against large batches of updates.
 *
 * <p>Example:</p>
 * <pre>
 *   java com.bloxbean.cardano.statetrees.jmt.tool.JmtLoadTester \
 *       --records=1000000 --batch=1000 --value-size=128 --rocksdb=/tmp/jmt-load \
 *       --node-cache=4096 --value-cache=8192
 * </pre>
 */
public final class JmtLoadTester {

    private JmtLoadTester() {
    }

    public static void main(String[] args) throws Exception {
        LoadOptions options = LoadOptions.parse(args);
        HashFunction hash = Blake2b256::digest;
        CommitmentScheme commitments = new ClassicJmtCommitmentScheme(hash);

        JellyfishMerkleTreeStoreConfig.Builder cfg = JellyfishMerkleTreeStoreConfig.builder();
        if (options.nodeCacheSize > 0) {
            cfg.enableNodeCache(true).nodeCacheSize(options.nodeCacheSize);
        }
        if (options.valueCacheSize > 0) {
            cfg.enableValueCache(true).valueCacheSize(options.valueCacheSize);
        }
        cfg.resultNodeLimit(options.resultNodeLimit);
        JellyfishMerkleTreeStoreConfig config = cfg.build();

        try (JmtStore store = options.inMemory ? new InMemoryJmtStore() : createRocksStore(options.rocksDbPath)) {
            JellyfishMerkleTreeStore tree = new JellyfishMerkleTreeStore(store, commitments, hash,
                    JellyfishMerkleTreeStore.EngineMode.STREAMING, config);
            runLoad(tree, hash, options);
        }
    }

    private static JmtStore createRocksStore(Path path) throws Exception {
        if (Files.notExists(path)) {
            Files.createDirectories(path);
        }
        try {
            Class<?> clazz = Class.forName("com.bloxbean.cardano.statetrees.rocksdb.jmt.RocksDbJmtStore");
            return (JmtStore) clazz.getConstructor(String.class).newInstance(path.toString());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("RocksDbJmtStore not found on classpath. Add the state-trees-rocksdb module.", e);
        }
    }

    private static void runLoad(JellyfishMerkleTreeStore tree, HashFunction hash, LoadOptions options) {
        Random random = new SecureRandom();
        long remaining = options.totalRecords;
        long version = tree.latestVersion().orElse(0L);
        Instant start = Instant.now();
        long proofChecks = 0;

        while (remaining > 0) {
            int batchSize = (int) Math.min(options.batchSize, remaining);
            Map<byte[], byte[]> updates = new LinkedHashMap<>(batchSize);
            while (updates.size() < batchSize) {
                byte[] key = new byte[32];
                random.nextBytes(key);
                byte[] value = new byte[options.valueSize];
                random.nextBytes(value);
                updates.put(key, value);
            }

            version++;
            tree.commit(version, updates);

            // Spot check one key to exercise read/proof paths.
            if (options.proofEvery > 0 && (version % options.proofEvery) == 0) {
                Map.Entry<byte[], byte[]> sample = updates.entrySet().iterator().next();
                tree.get(sample.getKey());
                tree.getProof(sample.getKey(), version);
                proofChecks++;
            }

            remaining -= batchSize;
            if (options.progressPeriod > 0 && (options.totalRecords - remaining) % options.progressPeriod == 0) {
                Duration elapsed = Duration.between(start, Instant.now());
                double throughput = (options.totalRecords - remaining) / Math.max(1, elapsed.toMillis() / 1000.0);
                System.out.printf("Progress: %,d / %,d (%.2f%%) throughput=%.0f inserts/s%n",
                        options.totalRecords - remaining,
                        options.totalRecords,
                        (options.totalRecords - remaining) * 100.0 / options.totalRecords,
                        throughput);
            }
        }

        Duration elapsed = Duration.between(start, Instant.now());
        double seconds = Math.max(1, elapsed.toMillis() / 1000.0);
        Runtime runtime = Runtime.getRuntime();
        long usedHeap = runtime.totalMemory() - runtime.freeMemory();

        System.out.println("==== Load Summary ====");
        System.out.printf("Total inserts: %,d%n", options.totalRecords);
        System.out.printf("Duration: %.2f s (%.0f inserts/s)%n", seconds, options.totalRecords / seconds);
        System.out.printf("Proof checks: %,d%n", proofChecks);
        System.out.printf("Latest version: %d%n", tree.latestVersion().orElse(-1L));
        System.out.printf("Heap used: %.2f MB%n", usedHeap / 1024.0 / 1024.0);
    }

    private static final class LoadOptions {
        final long totalRecords;
        final int batchSize;
        final int valueSize;
        final boolean inMemory;
        final Path rocksDbPath;
        final int nodeCacheSize;
        final int valueCacheSize;
        final int resultNodeLimit;
        final long progressPeriod;
        final long proofEvery;

        private LoadOptions(long totalRecords,
                            int batchSize,
                            int valueSize,
                            boolean inMemory,
                            Path rocksDbPath,
                            int nodeCacheSize,
                            int valueCacheSize,
                            int resultNodeLimit,
                            long progressPeriod,
                            long proofEvery) {
            this.totalRecords = totalRecords;
            this.batchSize = batchSize;
            this.valueSize = valueSize;
            this.inMemory = inMemory;
            this.rocksDbPath = rocksDbPath;
            this.nodeCacheSize = nodeCacheSize;
            this.valueCacheSize = valueCacheSize;
            this.resultNodeLimit = resultNodeLimit;
            this.progressPeriod = progressPeriod;
            this.proofEvery = proofEvery;
        }

        static LoadOptions parse(String[] args) {
            long records = 1_000_000L;
            int batch = 1_000;
            int valueSize = 128;
            boolean inMemory = false;
            Path rocksPath = Path.of("./jmt-load-db");
            int nodeCache = 0;
            int valueCache = 0;
            int resultLimit = Integer.MAX_VALUE;
            long progress = 100_000L;
            long proofEvery = 0L;

            for (String arg : args) {
                if (arg.startsWith("--records=")) {
                    records = Long.parseLong(arg.substring("--records=".length()));
                } else if (arg.startsWith("--batch=")) {
                    batch = Integer.parseInt(arg.substring("--batch=".length()));
                } else if (arg.startsWith("--value-size=")) {
                    valueSize = Integer.parseInt(arg.substring("--value-size=".length()));
                } else if (arg.equals("--memory")) {
                    inMemory = true;
                } else if (arg.startsWith("--rocksdb=")) {
                    rocksPath = Path.of(arg.substring("--rocksdb=".length()));
                } else if (arg.startsWith("--node-cache=")) {
                    nodeCache = Integer.parseInt(arg.substring("--node-cache=".length()));
                } else if (arg.startsWith("--value-cache=")) {
                    valueCache = Integer.parseInt(arg.substring("--value-cache=".length()));
                } else if (arg.startsWith("--result-node-limit=")) {
                    resultLimit = Integer.parseInt(arg.substring("--result-node-limit=".length()));
                } else if (arg.startsWith("--progress=")) {
                    progress = Long.parseLong(arg.substring("--progress=".length()));
                } else if (arg.startsWith("--proof-every=")) {
                    proofEvery = Long.parseLong(arg.substring("--proof-every=".length()));
                } else if (arg.equals("--help") || arg.equals("-h")) {
                    printUsageAndExit();
                }
            }

            if (!inMemory) {
                Objects.requireNonNull(rocksPath, "rocksDbPath");
            }

            return new LoadOptions(records, batch, valueSize, inMemory, rocksPath, nodeCache, valueCache,
                    resultLimit, progress, proofEvery);
        }

        private static void printUsageAndExit() {
            System.out.println("Usage: JmtLoadTester [options]\n" +
                    "  --records=N           Total records to insert (default 1_000_000)\n" +
                    "  --batch=N             Batch size per commit (default 1000)\n" +
                    "  --value-size=N        Random value size in bytes (default 128)\n" +
                    "  --memory              Use in-memory store instead of RocksDB\n" +
                    "  --rocksdb=PATH        Path for RocksDB store (default ./jmt-load-db)\n" +
                    "  --node-cache=N        Enable node cache with given size\n" +
                    "  --value-cache=N       Enable value cache with given size\n" +
                    "  --result-node-limit=N Limit nodes returned in commit results (default unlimited)\n" +
                    "  --progress=N          Emit progress every N inserts (default 100_000)\n" +
                    "  --proof-every=N       Generate a proof every N commits (default disabled)\n" +
                    "  --help                Show this message");
            System.exit(0);
        }
    }
}
