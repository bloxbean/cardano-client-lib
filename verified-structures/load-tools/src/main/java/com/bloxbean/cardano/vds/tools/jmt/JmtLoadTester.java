package com.bloxbean.cardano.vds.tools.jmt;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.vds.jmt.JmtProof;
import com.bloxbean.cardano.vds.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.vds.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.vds.jmt.store.InMemoryJmtStore;
import com.bloxbean.cardano.vds.jmt.store.JmtStore;
import com.bloxbean.cardano.vds.jmt.rocksdb.RocksDbConfig;
import com.bloxbean.cardano.vds.jmt.rocksdb.RocksDbJmtStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * RocksDB-focused load generator for Jellyfish Merkle Tree (Diem-style implementation).
 *
 * <p>Tests sustained write performance, proof generation, and pruning under realistic workloads.
 *
 * <p>Example usage:</p>
 * <pre>
 * # Basic load test - 1M operations
 * ./gradlew :verified-structures:load-tools:run --args="JmtLoadTester \
 *     --records=1000000 --batch=1000 --value-size=128 --rocksdb=/tmp/jmt-load"
 *
 * # With frequent RocksDB metrics monitoring (every 100k records)
 * ./gradlew :verified-structures:load-tools:run --args="JmtLoadTester \
 *     --records=50000000 --batch=2000 --progress=100000 --rocksdb=/tmp/jmt-load"
 *
 * # With pruning enabled
 * ./gradlew :verified-structures:load-tools:run --args="JmtLoadTester \
 *     --records=1000000 --batch=1000 --prune-every=100 --keep-latest=1000 --rocksdb=/tmp/jmt-load"
 *
 * # High throughput test (disable WAL, use large batches)
 * ./gradlew :verified-structures:load-tools:run --args="JmtLoadTester \
 *     --records=10000000 --batch=5000 --no-wal --rocksdb=/tmp/jmt-load-fast"
 *
 * # In-memory performance baseline
 * ./gradlew :verified-structures:load-tools:run --args="JmtLoadTester \
 *     --records=100000 --batch=1000 --memory"
 * </pre>
 *
 * <p><b>Note:</b> JMT does not support delete operations (Diem-compatible design).
 * All operations are inserts or updates to existing keys.
 */
public final class JmtLoadTester {

    private JmtLoadTester() {}

    public static void main(String[] args) throws Exception {
        LoadOptions options = LoadOptions.parse(args);
        HashFunction hashFn = Blake2b256::digest;
        CommitmentScheme commitments = new ClassicJmtCommitmentScheme(hashFn);

        if (options.inMemory) {
            // In-memory mode
            InMemoryJmtStore store = new InMemoryJmtStore();
            runLoad(store, hashFn, commitments, options);
        } else {
            // RocksDB mode
            if (Files.notExists(options.rocksDbPath)) {
                Files.createDirectories(options.rocksDbPath);
            }

            try {
                RocksDbJmtStore.Options storeOpts = RocksDbJmtStore.Options.builder()
                        .enableRollbackIndex(options.enableRollbackIndex)
                        .prunePolicy(options.prunePolicy)
                        .rocksDbConfig(RocksDbConfig.highThroughput())
                        .build();

                try (RocksDbJmtStore store = RocksDbJmtStore.open(
                        options.rocksDbPath.toString(), storeOpts)) {
                    runLoad(store, hashFn, commitments, options);
                }
            } catch (UnsatisfiedLinkError | RuntimeException e) {
                System.err.println("RocksDB JNI not available or failed to open: " + e.getMessage());
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    private static final int MAX_UPDATE_POOL = 100_000;  // Cap update key pool to 100K keys

    private static void runLoad(JmtStore store, HashFunction hashFn,
                                 CommitmentScheme commitments, LoadOptions options) {
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store, commitments, hashFn);
        Random random = new SecureRandom();

        long version = 0;
        long remaining = options.totalRecords;
        Instant start = Instant.now();
        long proofChecks = 0;
        long pruneOperations = 0;
        long totalPruned = 0;
        long totalCommits = 0;
        long totalInserts = 0;
        long totalUpdates = 0;

        // Track live keys to support updates (only if updateRatio > 0)
        List<byte[]> liveKeys = options.updateRatio > 0.0 ? new ArrayList<>(MAX_UPDATE_POOL) : null;
        Map<ByteArrayWrapper, Integer> liveIndex = options.updateRatio > 0.0 ? new HashMap<>(MAX_UPDATE_POOL) : null;

        // Statistics accumulators
        long totalCommitTimeMs = 0;
        long totalProofTimeMs = 0;
        long totalPruneTimeMs = 0;

        System.out.println("==== JMT Load Test ====");
        System.out.printf("Backend: %s%n", options.inMemory ? "InMemory" : "RocksDB (HIGH_THROUGHPUT profile)");
        System.out.printf("Total operations: %,d%n", options.totalRecords);
        System.out.printf("Batch size: %,d%n", options.batchSize);
        System.out.printf("Value size: %d bytes%n", options.valueSize);
        System.out.printf("Update ratio: %.2f%n", options.updateRatio);
        if (options.updateRatio > 0.0) {
            System.out.printf("Update pool size: %,d keys (bounded)%n", MAX_UPDATE_POOL);
        }
        if (options.pruneEvery > 0) {
            System.out.printf("Pruning: every %,d batches, keep latest %,d versions%n",
                    options.pruneEvery, options.keepLatest);
        }
        System.out.println();
        System.out.println("Expected Performance (ADR-0015 Phase 2):");
        System.out.println("  - Throughput: 10-13k ops/s sustained (vs 2.6k baseline)");
        System.out.println("  - Write amplification: ~15x (vs 50x baseline)");
        System.out.println("  - Write stalls: <5% (vs 40-60% baseline)");
        System.out.println("==========================\n");

        while (remaining > 0) {
            int batchSize = (int) Math.min(options.batchSize, remaining);
            Map<byte[], byte[]> updates = new LinkedHashMap<>(batchSize);

            // Decide how many updates vs inserts
            int numUpdates = 0;
            if (liveKeys != null && !liveKeys.isEmpty() && options.updateRatio > 0.0) {
                numUpdates = (int) Math.round(batchSize * options.updateRatio);
                numUpdates = Math.min(numUpdates, liveKeys.size());
            }

            // Generate updates to existing keys
            if (liveKeys != null) {
                for (int i = 0; i < numUpdates; i++) {
                    int idx = ThreadLocalRandom.current().nextInt(liveKeys.size());
                    byte[] existingKey = liveKeys.get(idx);
                    byte[] newValue = new byte[options.valueSize];
                    random.nextBytes(newValue);
                    updates.put(existingKey, newValue);
                    totalUpdates++;
                }
            }

            // Generate inserts for new keys
            while (updates.size() < batchSize) {
                byte[] key = new byte[32];
                random.nextBytes(key);
                byte[] value = new byte[options.valueSize];
                random.nextBytes(value);
                updates.put(key, value);
                totalInserts++;
            }

            // Commit batch
            long commitStart = System.currentTimeMillis();
            version++;
            JellyfishMerkleTree.CommitResult result = tree.put(version, updates);
            long commitElapsed = System.currentTimeMillis() - commitStart;
            totalCommitTimeMs += commitElapsed;
            totalCommits++;

            // Maintain live keys index (bounded pool)
            if (liveKeys != null && liveIndex != null) {
                for (Map.Entry<byte[], byte[]> entry : updates.entrySet()) {
                    ByteArrayWrapper keyWrapper = new ByteArrayWrapper(entry.getKey());
                    Integer existingIdx = liveIndex.get(keyWrapper);
                    if (existingIdx == null) {
                        // New key - enforce pool limit
                        if (liveKeys.size() >= MAX_UPDATE_POOL) {
                            // Remove oldest 10% when limit reached
                            int removeCount = MAX_UPDATE_POOL / 10;
                            for (int i = 0; i < removeCount; i++) {
                                byte[] removedKey = liveKeys.remove(0);
                                liveIndex.remove(new ByteArrayWrapper(removedKey));
                            }
                            // Rebuild index to fix indices after removals
                            liveIndex.clear();
                            for (int i = 0; i < liveKeys.size(); i++) {
                                liveIndex.put(new ByteArrayWrapper(liveKeys.get(i)), i);
                            }
                        }
                        liveKeys.add(entry.getKey());
                        liveIndex.put(keyWrapper, liveKeys.size() - 1);
                    } else {
                        // Update existing key (already in index)
                        liveKeys.set(existingIdx, entry.getKey());
                    }
                }
            }

            // Optional proof generation exercise
            if (options.proofEvery > 0 && (totalCommits % options.proofEvery) == 0 && liveKeys != null && !liveKeys.isEmpty()) {
                byte[] sampleKey = liveKeys.get(ThreadLocalRandom.current().nextInt(liveKeys.size()));
                byte[] keyHash = hashFn.digest(sampleKey);

                long proofStart = System.currentTimeMillis();
                Optional<JmtProof> proofOpt = tree.getProof(keyHash, version);
                long proofElapsed = System.currentTimeMillis() - proofStart;
                if (proofOpt.isPresent()) {
                    totalProofTimeMs += proofElapsed;
                    proofChecks++;
                }
            }

            // Optional pruning
            if (options.pruneEvery > 0 && (totalCommits % options.pruneEvery) == 0
                    && version > options.keepLatest && store instanceof RocksDbJmtStore) {
                long pruneUpTo = version - options.keepLatest;

                long pruneStart = System.currentTimeMillis();
                int pruned = ((RocksDbJmtStore) store).pruneUpTo(pruneUpTo);
                long pruneElapsed = System.currentTimeMillis() - pruneStart;

                totalPruneTimeMs += pruneElapsed;
                totalPruned += pruned;
                pruneOperations++;
            }

            remaining -= batchSize;

            // Progress reporting
            if (options.progressPeriod > 0 && (options.totalRecords - remaining) % options.progressPeriod == 0) {
                Duration elapsed = Duration.between(start, Instant.now());
                double throughput = (options.totalRecords - remaining) / Math.max(1, elapsed.toMillis() / 1000.0);
                double avgCommitMs = totalCommitTimeMs / (double) totalCommits;

                System.out.printf(
                        "Progress: %,d / %,d (%.2f%%) | throughput=%.0f ops/s | avg_commit=%.2fms | inserts=%,d updates=%,d%n",
                        options.totalRecords - remaining,
                        options.totalRecords,
                        (options.totalRecords - remaining) * 100.0 / options.totalRecords,
                        throughput,
                        avgCommitMs,
                        totalInserts,
                        totalUpdates);

                // RocksDB monitoring (ADR-0015 Phase 5 - diagnose performance degradation)
                if (store instanceof RocksDbJmtStore) {
                    try {
                        RocksDbJmtStore rocksStore = (RocksDbJmtStore) store;
                        RocksDbJmtStore.DbProperties props = rocksStore.sampleDbProperties();
                        System.out.printf(
                                "  RocksDB: pending_compact=%.1fMB | running_compact=%d | running_flush=%d | " +
                                "active_mem=%.1fMB | all_mem=%.1fMB | immutable_mem=%d%n",
                                props.pendingCompactionBytes() / 1024.0 / 1024.0,
                                props.runningCompactions(),
                                props.runningFlushes(),
                                props.curSizeActiveMemTable() / 1024.0 / 1024.0,
                                props.curSizeAllMemTables() / 1024.0 / 1024.0,
                                props.numImmutableMemTables());
                    } catch (Exception e) {
                        // Ignore monitoring errors
                    }
                }
            }
        }

        Duration elapsed = Duration.between(start, Instant.now());
        double seconds = Math.max(1, elapsed.toMillis() / 1000.0);
        Runtime runtime = Runtime.getRuntime();
        long usedHeap = runtime.totalMemory() - runtime.freeMemory();

        // Final statistics
        System.out.println("\n==== Load Test Summary ====");
        System.out.printf("Total operations: %,d%n", options.totalRecords);
        System.out.printf("  Inserts: %,d%n", totalInserts);
        System.out.printf("  Updates: %,d%n", totalUpdates);
        System.out.printf("Duration: %.2f s (%.0f ops/s)%n", seconds, options.totalRecords / seconds);
        System.out.printf("Total commits: %,d%n", totalCommits);
        System.out.printf("Final version: %d%n", version);
        System.out.println();

        // Performance metrics
        System.out.println("==== Performance Metrics ====");
        System.out.printf("Commit latency (avg): %.2f ms%n", totalCommitTimeMs / (double) totalCommits);
        System.out.printf("Commit throughput: %.0f commits/s%n", totalCommits / seconds);
        if (proofChecks > 0) {
            System.out.printf("Proof generation (avg): %.2f ms (%,d proofs)%n",
                    totalProofTimeMs / (double) proofChecks, proofChecks);
        }
        if (pruneOperations > 0) {
            System.out.printf("Pruning (avg): %.2f ms per operation (%,d operations, %,d entries pruned)%n",
                    totalPruneTimeMs / (double) pruneOperations, pruneOperations, totalPruned);
        }
        System.out.println();

        // Memory usage
        System.out.println("==== Memory Usage ====");
        System.out.printf("Heap used: %.2f MB%n", usedHeap / 1024.0 / 1024.0);
        if (liveKeys != null) {
            System.out.printf("Live keys tracked: %,d%n", liveKeys.size());
        } else {
            System.out.println("Live keys tracked: 0 (tracking disabled, insert-only mode)");
        }

        // Storage statistics (RocksDB only)
        if (store instanceof RocksDbJmtStore) {
            RocksDbJmtStore rocksStore = (RocksDbJmtStore) store;
            System.out.println("\n==== RocksDB Statistics ====");
            try {
                RocksDbJmtStore.DbProperties props = rocksStore.sampleDbProperties();
                System.out.printf("Pending compaction: %.2f MB%n",
                        props.pendingCompactionBytes() / 1024.0 / 1024.0);
                System.out.printf("Running compactions: %d%n", props.runningCompactions());
                System.out.printf("Running flushes: %d%n", props.runningFlushes());
                System.out.printf("Active memtable: %.2f MB%n",
                        props.curSizeActiveMemTable() / 1024.0 / 1024.0);
                System.out.printf("All memtables: %.2f MB%n",
                        props.curSizeAllMemTables() / 1024.0 / 1024.0);
                System.out.printf("Immutable memtables: %d%n", props.numImmutableMemTables());
            } catch (Exception e) {
                System.err.println("Failed to retrieve RocksDB statistics: " + e.getMessage());
            }
        }

        System.out.println("\n==== Test Complete ====");
    }

    private static final class LoadOptions {
        final long totalRecords;
        final int batchSize;
        final int valueSize;
        final boolean inMemory;
        final Path rocksDbPath;
        final long progressPeriod;
        final long proofEvery;
        final double updateRatio;
        final long pruneEvery;
        final int keepLatest;
        final boolean enableRollbackIndex;
        final RocksDbJmtStore.ValuePrunePolicy prunePolicy;
        final boolean noWal;

        private LoadOptions(long totalRecords,
                            int batchSize,
                            int valueSize,
                            boolean inMemory,
                            Path rocksDbPath,
                            long progressPeriod,
                            long proofEvery,
                            double updateRatio,
                            long pruneEvery,
                            int keepLatest,
                            boolean enableRollbackIndex,
                            RocksDbJmtStore.ValuePrunePolicy prunePolicy,
                            boolean noWal) {
            this.totalRecords = totalRecords;
            this.batchSize = batchSize;
            this.valueSize = valueSize;
            this.inMemory = inMemory;
            this.rocksDbPath = rocksDbPath;
            this.progressPeriod = progressPeriod;
            this.proofEvery = proofEvery;
            this.updateRatio = updateRatio;
            this.pruneEvery = pruneEvery;
            this.keepLatest = keepLatest;
            this.enableRollbackIndex = enableRollbackIndex;
            this.prunePolicy = prunePolicy;
            this.noWal = noWal;
        }

        static LoadOptions parse(String[] args) {
            long records = 1_000_000L;
            int batch = 1_000;
            int valueSize = 128;
            boolean inMemory = false;
            Path rocksPath = Path.of("./jmt-load-db");
            long progress = 100_000L;
            long proofEvery = 0L;
            double updateRatio = 0.2; // 20% updates by default
            long pruneEvery = 0L;     // No pruning by default
            int keepLatest = 1000;    // Keep 1000 versions when pruning
            boolean enableRollbackIndex = false;
            RocksDbJmtStore.ValuePrunePolicy prunePolicy = RocksDbJmtStore.ValuePrunePolicy.SAFE;
            boolean noWal = false;

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
                } else if (arg.startsWith("--progress=")) {
                    progress = Long.parseLong(arg.substring("--progress=".length()));
                } else if (arg.startsWith("--proof-every=")) {
                    proofEvery = Long.parseLong(arg.substring("--proof-every=".length()));
                } else if (arg.startsWith("--update-ratio=")) {
                    updateRatio = Double.parseDouble(arg.substring("--update-ratio=".length()));
                } else if (arg.startsWith("--prune-every=")) {
                    pruneEvery = Long.parseLong(arg.substring("--prune-every=".length()));
                } else if (arg.startsWith("--keep-latest=")) {
                    keepLatest = Integer.parseInt(arg.substring("--keep-latest=".length()));
                } else if (arg.equals("--enable-rollback-index")) {
                    enableRollbackIndex = true;
                } else if (arg.startsWith("--prune-policy=")) {
                    String policy = arg.substring("--prune-policy=".length());
                    prunePolicy = policy.equalsIgnoreCase("aggressive")
                            ? RocksDbJmtStore.ValuePrunePolicy.AGGRESSIVE
                            : RocksDbJmtStore.ValuePrunePolicy.SAFE;
                } else if (arg.equals("--no-wal")) {
                    noWal = true;
                } else if (arg.equals("--help") || arg.equals("-h")) {
                    printUsageAndExit();
                }
            }

            if (!inMemory) Objects.requireNonNull(rocksPath, "rocksDbPath");
            if (updateRatio < 0.0 || updateRatio > 1.0) {
                throw new IllegalArgumentException("--update-ratio must be between 0.0 and 1.0");
            }

            return new LoadOptions(records, batch, valueSize, inMemory, rocksPath, progress,
                    proofEvery, updateRatio, pruneEvery, keepLatest, enableRollbackIndex,
                    prunePolicy, noWal);
        }

        private static void printUsageAndExit() {
            System.out.println("JMT Load Tester - Performance and stress testing for Jellyfish Merkle Tree\n");
            System.out.println("Usage: JmtLoadTester [options]\n");
            System.out.println("Basic Options:");
            System.out.println("  --records=N           Total operations to perform (default: 1,000,000)");
            System.out.println("  --batch=N             Updates per batch commit (default: 1000)");
            System.out.println("  --value-size=N        Value size in bytes (default: 128)");
            System.out.println("  --update-ratio=F      Fraction of updates vs inserts, 0-1 (default: 0.2)");
            System.out.println();
            System.out.println("Storage Options:");
            System.out.println("  --memory              Use in-memory store (default: RocksDB)");
            System.out.println("  --rocksdb=PATH        RocksDB directory (default: ./jmt-load-db)");
            System.out.println("  --no-wal              Disable WAL for faster ingest (unsafe for durability)");
            System.out.println();
            System.out.println("Pruning Options:");
            System.out.println("  --prune-every=N       Run pruning every N batches (default: 0 = disabled)");
            System.out.println("  --keep-latest=N       Retention window: keep N latest versions (default: 1000)");
            System.out.println("  --prune-policy=MODE   'safe' (keep sentinel) or 'aggressive' (delete all) (default: safe)");
            System.out.println();
            System.out.println("Rollback Options:");
            System.out.println("  --enable-rollback-index  Enable rollback indices (~15-20% storage overhead)");
            System.out.println();
            System.out.println("Testing & Monitoring Options:");
            System.out.println("  --progress=N          Progress & RocksDB metrics reporting interval in operations");
            System.out.println("                        (default: 100,000; use 100000 for every 100k records)");
            System.out.println("  --proof-every=N       Generate proof every N batches (default: 0 = disabled)");
            System.out.println();
            System.out.println("Examples:");
            System.out.println();
            System.out.println("  # Basic load test");
            System.out.println("  JmtLoadTester --records=1000000 --batch=1000");
            System.out.println();
            System.out.println("  # With pruning enabled");
            System.out.println("  JmtLoadTester --records=1000000 --prune-every=100 --keep-latest=1000");
            System.out.println();
            System.out.println("  # High throughput test");
            System.out.println("  JmtLoadTester --records=10000000 --batch=5000 --no-wal");
            System.out.println();
            System.out.println("  # In-memory baseline");
            System.out.println("  JmtLoadTester --records=100000 --batch=1000 --memory");
            System.out.println();
            System.out.println("Note: JMT does not support delete operations (Diem-compatible design).");
            System.out.println("      All operations are inserts or updates to existing keys.");
            System.exit(0);
        }
    }

    /**
     * Wrapper for byte arrays to use as HashMap keys.
     */
    private static final class ByteArrayWrapper {
        private final byte[] bytes;
        private final int hash;

        private ByteArrayWrapper(byte[] bytes) {
            this.bytes = Arrays.copyOf(bytes, bytes.length);
            this.hash = Arrays.hashCode(this.bytes);
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof ByteArrayWrapper) && Arrays.equals(bytes, ((ByteArrayWrapper) o).bytes);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
