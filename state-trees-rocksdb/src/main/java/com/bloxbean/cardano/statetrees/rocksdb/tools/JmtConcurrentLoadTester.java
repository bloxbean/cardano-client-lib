package com.bloxbean.cardano.statetrees.rocksdb.tools;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTreeV2;
import com.bloxbean.cardano.statetrees.jmt.JmtProof;
import com.bloxbean.cardano.statetrees.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.rocksdb.jmt.RocksDbConfig;
import com.bloxbean.cardano.statetrees.rocksdb.jmt.RocksDbJmtStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Concurrent read/write load tester for JMT V2.
 *
 * <p>Tests concurrent access patterns:
 * <ul>
 *   <li>Concurrent writes (commits)</li>
 *   <li>Concurrent reads (proof generation)</li>
 *   <li>Mixed read/write workload</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>
 * # 1-hour sustained write test with concurrent reads
 * ./gradlew :state-trees-rocksdb:run --args="com.bloxbean.cardano.statetrees.rocksdb.tools.JmtConcurrentLoadTester \
 *     --duration=3600 --write-threads=4 --read-threads=8 --batch=1000 --rocksdb=/tmp/jmt-concurrent"
 *
 * # Write-heavy workload
 * ./gradlew :state-trees-rocksdb:run --args="com.bloxbean.cardano.statetrees.rocksdb.tools.JmtConcurrentLoadTester \
 *     --duration=1800 --write-threads=8 --read-threads=2 --batch=500 --rocksdb=/tmp/jmt-write-heavy"
 *
 * # Read-heavy workload
 * ./gradlew :state-trees-rocksdb:run --args="com.bloxbean.cardano.statetrees.rocksdb.tools.JmtConcurrentLoadTester \
 *     --duration=1800 --write-threads=2 --read-threads=16 --batch=1000 --rocksdb=/tmp/jmt-read-heavy"
 * </pre>
 */
public final class JmtConcurrentLoadTester {

    private JmtConcurrentLoadTester() {}

    public static void main(String[] args) throws Exception {
        ConcurrentLoadOptions options = ConcurrentLoadOptions.parse(args);
        HashFunction hashFn = Blake2b256::digest;
        CommitmentScheme commitments = new ClassicJmtCommitmentScheme(hashFn);

        if (Files.notExists(options.rocksDbPath)) {
            Files.createDirectories(options.rocksDbPath);
        }

        try {
            RocksDbJmtStore.Options storeOpts = RocksDbJmtStore.Options.builder()
                    .rocksDbConfig(RocksDbConfig.highThroughput())
                    .build();

            try (RocksDbJmtStore store = RocksDbJmtStore.open(
                    options.rocksDbPath.toString(), storeOpts)) {
                runConcurrentLoad(store, hashFn, commitments, options);
            }
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            System.err.println("RocksDB JNI not available or failed to open: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void runConcurrentLoad(RocksDbJmtStore store, HashFunction hashFn,
                                          CommitmentScheme commitments, ConcurrentLoadOptions options) throws Exception {
        JellyfishMerkleTreeV2 tree = new JellyfishMerkleTreeV2(store, commitments, hashFn);

        // Shared state
        AtomicLong version = new AtomicLong(0);
        AtomicBoolean running = new AtomicBoolean(true);
        ConcurrentHashMap<Long, Set<byte[]>> versionedKeys = new ConcurrentHashMap<>();
        BlockingQueue<byte[]> keyPool = new LinkedBlockingQueue<>();

        // Metrics
        AtomicLong totalWrites = new AtomicLong(0);
        AtomicLong totalReads = new AtomicLong(0);
        AtomicLong totalWriteTimeMs = new AtomicLong(0);
        AtomicLong totalReadTimeMs = new AtomicLong(0);
        ConcurrentLinkedQueue<Long> writeLatencies = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Long> readLatencies = new ConcurrentLinkedQueue<>();

        ExecutorService writeExecutor = Executors.newFixedThreadPool(options.writeThreads);
        ExecutorService readExecutor = Executors.newFixedThreadPool(options.readThreads);

        System.out.println("==== JMT V2 Concurrent Load Test ====");
        System.out.printf("Duration: %d seconds%n", options.durationSeconds);
        System.out.printf("Write threads: %d%n", options.writeThreads);
        System.out.printf("Read threads: %d%n", options.readThreads);
        System.out.printf("Batch size: %,d%n", options.batchSize);
        System.out.printf("Value size: %d bytes%n", options.valueSize);
        System.out.println("======================================\n");

        Instant start = Instant.now();

        // Start write threads
        for (int i = 0; i < options.writeThreads; i++) {
            final int threadId = i;
            writeExecutor.submit(() -> {
                Random random = new SecureRandom();
                while (running.get()) {
                    try {
                        Map<byte[], byte[]> updates = new HashMap<>(options.batchSize);
                        Set<byte[]> batchKeys = new HashSet<>();

                        for (int j = 0; j < options.batchSize; j++) {
                            byte[] key = new byte[32];
                            random.nextBytes(key);
                            byte[] value = new byte[options.valueSize];
                            random.nextBytes(value);
                            updates.put(key, value);
                            batchKeys.add(key);
                        }

                        long writeStart = System.currentTimeMillis();
                        long ver = version.incrementAndGet();

                        // Serialize writes to avoid version conflicts
                        synchronized (tree) {
                            tree.put(ver, updates);
                        }

                        long writeElapsed = System.currentTimeMillis() - writeStart;
                        totalWriteTimeMs.addAndGet(writeElapsed);
                        totalWrites.incrementAndGet();
                        writeLatencies.add(writeElapsed);

                        // Track keys for reads
                        versionedKeys.put(ver, batchKeys);
                        keyPool.addAll(batchKeys);

                        // Keep key pool bounded
                        while (keyPool.size() > 100_000) {
                            keyPool.poll();
                        }

                    } catch (Exception e) {
                        System.err.printf("[Writer-%d] Error: %s%n", threadId, e.getMessage());
                    }
                }
            });
        }

        // Start read threads (after initial warmup)
        Thread.sleep(2000); // Let some data accumulate

        for (int i = 0; i < options.readThreads; i++) {
            final int threadId = i;
            readExecutor.submit(() -> {
                while (running.get()) {
                    try {
                        byte[] key = keyPool.poll();
                        if (key == null) {
                            Thread.sleep(10); // Wait for keys
                            continue;
                        }

                        byte[] keyHash = hashFn.digest(key);
                        long currentVersion = version.get();

                        if (currentVersion > 0) {
                            long readStart = System.currentTimeMillis();
                            Optional<JmtProof> proof = tree.getProof(keyHash, currentVersion);
                            long readElapsed = System.currentTimeMillis() - readStart;

                            if (proof.isPresent()) {
                                totalReadTimeMs.addAndGet(readElapsed);
                                totalReads.incrementAndGet();
                                readLatencies.add(readElapsed);
                            }
                        }

                        // Return key to pool
                        keyPool.offer(key);

                    } catch (Exception e) {
                        System.err.printf("[Reader-%d] Error: %s%n", threadId, e.getMessage());
                    }
                }
            });
        }

        // Monitor progress
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
        monitor.scheduleAtFixedRate(() -> {
            Duration elapsed = Duration.between(start, Instant.now());
            long writes = totalWrites.get();
            long reads = totalReads.get();
            double writeThroughput = writes / Math.max(1, elapsed.toSeconds());
            double readThroughput = reads / Math.max(1, elapsed.toSeconds());
            double avgWriteLatency = writes > 0 ? totalWriteTimeMs.get() / (double) writes : 0;
            double avgReadLatency = reads > 0 ? totalReadTimeMs.get() / (double) reads : 0;

            System.out.printf(
                    "[%03ds] Writes: %,d (%.0f/s, %.2fms avg) | Reads: %,d (%.0f/s, %.2fms avg) | Version: %d%n",
                    elapsed.toSeconds(),
                    writes,
                    writeThroughput,
                    avgWriteLatency,
                    reads,
                    readThroughput,
                    avgReadLatency,
                    version.get());
        }, 10, 10, TimeUnit.SECONDS);

        // Run for specified duration
        Thread.sleep(options.durationSeconds * 1000L);

        // Shutdown
        running.set(false);
        writeExecutor.shutdown();
        readExecutor.shutdown();
        monitor.shutdown();

        writeExecutor.awaitTermination(30, TimeUnit.SECONDS);
        readExecutor.awaitTermination(30, TimeUnit.SECONDS);
        monitor.awaitTermination(5, TimeUnit.SECONDS);

        // Final statistics
        Duration elapsed = Duration.between(start, Instant.now());
        long writes = totalWrites.get();
        long reads = totalReads.get();

        System.out.println("\n==== Concurrent Load Test Summary ====");
        System.out.printf("Duration: %.2f seconds%n", elapsed.toMillis() / 1000.0);
        System.out.printf("Final version: %d%n", version.get());
        System.out.printf("Total operations: %,d (%,d writes + %,d reads)%n",
                writes + reads, writes, reads);
        System.out.println();

        // Write statistics
        System.out.println("==== Write Performance ====");
        System.out.printf("Total writes: %,d%n", writes);
        System.out.printf("Write throughput: %.2f commits/s%n", writes / (elapsed.toMillis() / 1000.0));
        System.out.printf("Avg write latency: %.2f ms%n", writes > 0 ? totalWriteTimeMs.get() / (double) writes : 0);

        List<Long> sortedWriteLatencies = new ArrayList<>(writeLatencies);
        sortedWriteLatencies.sort(Long::compareTo);
        if (!sortedWriteLatencies.isEmpty()) {
            System.out.printf("Write p50: %d ms%n", getPercentile(sortedWriteLatencies, 50));
            System.out.printf("Write p95: %d ms%n", getPercentile(sortedWriteLatencies, 95));
            System.out.printf("Write p99: %d ms%n", getPercentile(sortedWriteLatencies, 99));
        }
        System.out.println();

        // Read statistics
        System.out.println("==== Read Performance ====");
        System.out.printf("Total reads: %,d%n", reads);
        System.out.printf("Read throughput: %.2f proofs/s%n", reads / (elapsed.toMillis() / 1000.0));
        System.out.printf("Avg read latency: %.2f ms%n", reads > 0 ? totalReadTimeMs.get() / (double) reads : 0);

        List<Long> sortedReadLatencies = new ArrayList<>(readLatencies);
        sortedReadLatencies.sort(Long::compareTo);
        if (!sortedReadLatencies.isEmpty()) {
            System.out.printf("Read p50: %d ms%n", getPercentile(sortedReadLatencies, 50));
            System.out.printf("Read p95: %d ms%n", getPercentile(sortedReadLatencies, 95));
            System.out.printf("Read p99: %d ms%n", getPercentile(sortedReadLatencies, 99));
        }
        System.out.println();

        // Memory usage
        Runtime runtime = Runtime.getRuntime();
        long usedHeap = runtime.totalMemory() - runtime.freeMemory();
        System.out.println("==== Memory Usage ====");
        System.out.printf("Heap used: %.2f MB%n", usedHeap / 1024.0 / 1024.0);
        System.out.printf("Key pool size: %,d%n", keyPool.size());
        System.out.println();

        // RocksDB statistics
        System.out.println("==== RocksDB Statistics ====");
        try {
            RocksDbJmtStore.DbProperties props = store.sampleDbProperties();
            System.out.printf("Pending compaction: %.2f MB%n",
                    props.pendingCompactionBytes() / 1024.0 / 1024.0);
            System.out.printf("Running compactions: %d%n", props.runningCompactions());
            System.out.printf("Active memtable: %.2f MB%n",
                    props.curSizeActiveMemTable() / 1024.0 / 1024.0);
            System.out.printf("Immutable memtables: %d%n", props.numImmutableMemTables());
        } catch (Exception e) {
            System.err.println("Failed to retrieve RocksDB statistics: " + e.getMessage());
        }

        System.out.println("\n==== Test Complete ====");
    }

    private static long getPercentile(List<Long> sorted, int percentile) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private static final class ConcurrentLoadOptions {
        final int durationSeconds;
        final int writeThreads;
        final int readThreads;
        final int batchSize;
        final int valueSize;
        final Path rocksDbPath;

        private ConcurrentLoadOptions(int durationSeconds, int writeThreads, int readThreads,
                                       int batchSize, int valueSize, Path rocksDbPath) {
            this.durationSeconds = durationSeconds;
            this.writeThreads = writeThreads;
            this.readThreads = readThreads;
            this.batchSize = batchSize;
            this.valueSize = valueSize;
            this.rocksDbPath = rocksDbPath;
        }

        static ConcurrentLoadOptions parse(String[] args) {
            int duration = 3600; // 1 hour default
            int writeThreads = 4;
            int readThreads = 8;
            int batchSize = 1000;
            int valueSize = 128;
            Path rocksPath = Path.of("./jmt-concurrent-db");

            for (String arg : args) {
                if (arg.startsWith("--duration=")) {
                    duration = Integer.parseInt(arg.substring("--duration=".length()));
                } else if (arg.startsWith("--write-threads=")) {
                    writeThreads = Integer.parseInt(arg.substring("--write-threads=".length()));
                } else if (arg.startsWith("--read-threads=")) {
                    readThreads = Integer.parseInt(arg.substring("--read-threads=".length()));
                } else if (arg.startsWith("--batch=")) {
                    batchSize = Integer.parseInt(arg.substring("--batch=".length()));
                } else if (arg.startsWith("--value-size=")) {
                    valueSize = Integer.parseInt(arg.substring("--value-size=".length()));
                } else if (arg.startsWith("--rocksdb=")) {
                    rocksPath = Path.of(arg.substring("--rocksdb=".length()));
                } else if (arg.equals("--help") || arg.equals("-h")) {
                    printUsageAndExit();
                }
            }

            return new ConcurrentLoadOptions(duration, writeThreads, readThreads, batchSize, valueSize, rocksPath);
        }

        private static void printUsageAndExit() {
            System.out.println("JMT V2 Concurrent Load Tester - Tests concurrent read/write patterns\n");
            System.out.println("Usage: JmtConcurrentLoadTester [options]\n");
            System.out.println("Options:");
            System.out.println("  --duration=N          Duration in seconds (default: 3600 = 1 hour)");
            System.out.println("  --write-threads=N     Number of concurrent write threads (default: 4)");
            System.out.println("  --read-threads=N      Number of concurrent read threads (default: 8)");
            System.out.println("  --batch=N             Updates per batch commit (default: 1000)");
            System.out.println("  --value-size=N        Value size in bytes (default: 128)");
            System.out.println("  --rocksdb=PATH        RocksDB directory (default: ./jmt-concurrent-db)");
            System.out.println();
            System.out.println("Examples:");
            System.out.println();
            System.out.println("  # 1-hour balanced workload");
            System.out.println("  JmtConcurrentLoadTester --duration=3600 --write-threads=4 --read-threads=8");
            System.out.println();
            System.out.println("  # Write-heavy workload");
            System.out.println("  JmtConcurrentLoadTester --duration=1800 --write-threads=8 --read-threads=2");
            System.out.println();
            System.out.println("  # Read-heavy workload");
            System.out.println("  JmtConcurrentLoadTester --duration=1800 --write-threads=2 --read-threads=16");
            System.exit(0);
        }
    }
}