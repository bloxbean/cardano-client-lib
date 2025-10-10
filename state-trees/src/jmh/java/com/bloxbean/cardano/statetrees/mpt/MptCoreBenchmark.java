package com.bloxbean.cardano.statetrees.mpt;

import com.bloxbean.cardano.statetrees.TestNodeStore;
import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.api.MerklePatriciaTrie;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Core MPT operation benchmarks for performance baseline measurement.
 *
 * <p>This benchmark suite measures the performance of fundamental MPT operations
 * to establish baseline metrics before refactoring. These benchmarks will be
 * used to ensure no performance regressions during the refactoring process.</p>
 *
 * <p><b>Measured Operations:</b></p>
 * <ul>
 *   <li>Sequential inserts (put operations)</li>
 *   <li>Random access reads (get operations)</li>
 *   <li>Node deletions with tree compression</li>
 *   <li>Prefix scanning with various result sizes</li>
 *   <li>Mixed workloads (put/get/delete combinations)</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * # Run all benchmarks
 * ./gradlew :state-trees:jmh
 *
 * # Run specific benchmark
 * ./gradlew :state-trees:jmh -Pjmh.include=".*insert.*"
 * }</pre>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class MptCoreBenchmark {

    private static final HashFunction HASH_FN = Blake2b256::digest;

    @Param({"100", "1000", "5000"})
    private int datasetSize;

    private TestNodeStore store;
    private MerklePatriciaTrie trie;
    private List<byte[]> keys;
    private List<byte[]> values;
    private Random random;

    @Setup(Level.Trial)
    public void setupTrial() {
        store = new TestNodeStore();
        trie = new MerklePatriciaTrie(store, HASH_FN);
        random = new Random(42); // Fixed seed for reproducibility

        // Pre-generate test data
        keys = new ArrayList<>(datasetSize);
        values = new ArrayList<>(datasetSize);

        for (int i = 0; i < datasetSize; i++) {
            byte[] key = new byte[8];
            byte[] value = new byte[32];

            random.nextBytes(key);
            random.nextBytes(value);

            keys.add(key);
            values.add(value);
        }
    }

    @Setup(Level.Iteration)
    public void setupIteration() {
        // Clear trie for each iteration to ensure consistent state
        store.clear();
        trie = new MerklePatriciaTrie(store, HASH_FN);
    }

    /**
     * Benchmarks sequential insert operations.
     *
     * <p>Measures throughput of putting key-value pairs into an empty trie.
     * This represents the common case of building a trie from scratch.</p>
     */
    @Benchmark
    public void sequentialInserts(Blackhole bh) {
        for (int i = 0; i < datasetSize; i++) {
            trie.put(keys.get(i), values.get(i));
        }
        // Consume root hash to ensure operation completion
        bh.consume(trie.getRootHash());
    }

    /**
     * Benchmarks random access read operations.
     *
     * <p>Pre-populates the trie and then measures throughput of random
     * get operations. This represents typical read-heavy workloads.</p>
     */
    @Benchmark
    public void randomReads(Blackhole bh) {
        // Pre-populate trie
        for (int i = 0; i < datasetSize; i++) {
            trie.put(keys.get(i), values.get(i));
        }

        // Perform random reads (5x dataset size for meaningful measurement)
        Random readRandom = new Random(123);
        int numReads = datasetSize * 5;
        for (int i = 0; i < numReads; i++) {
            int keyIndex = readRandom.nextInt(datasetSize);
            byte[] result = trie.get(keys.get(keyIndex));
            bh.consume(result);
        }
    }

    /**
     * Benchmarks delete operations with tree compression.
     *
     * <p>Pre-populates the trie and then measures throughput of delete
     * operations. This tests the tree compression logic that runs after
     * node removal.</p>
     */
    @Benchmark
    public void deletionsWithCompression(Blackhole bh) {
        // Pre-populate trie
        for (int i = 0; i < datasetSize; i++) {
            trie.put(keys.get(i), values.get(i));
        }

        // Delete half the entries
        int numDeletes = datasetSize / 2;
        for (int i = 0; i < numDeletes; i++) {
            trie.delete(keys.get(i));
        }

        bh.consume(trie.getRootHash());
    }

    /**
     * Benchmarks prefix scanning operations.
     *
     * <p>Creates data with predictable prefixes and measures throughput
     * of prefix scan operations with various result set sizes.</p>
     */
    @Benchmark
    public void prefixScanning(Blackhole bh) {
        // Create data with known prefixes
        int numPrefixes = 16; // Use hex digits 0-F as prefixes

        for (int i = 0; i < datasetSize; i++) {
            // Create key with predictable prefix
            byte[] key = new byte[8];
            key[0] = (byte) (i % numPrefixes); // First byte determines prefix

            // Fill rest with random data
            Random keyRandom = new Random(i);
            for (int j = 1; j < key.length; j++) {
                key[j] = (byte) keyRandom.nextInt(256);
            }

            trie.put(key, values.get(i % values.size()));
        }

        // Scan with different prefixes
        for (int prefix = 0; prefix < numPrefixes; prefix++) {
            byte[] prefixBytes = {(byte) prefix};
            var results = trie.scanByPrefix(prefixBytes, 1000);
            bh.consume(results);
        }
    }

    /**
     * Benchmarks mixed workload operations.
     *
     * <p>Simulates realistic usage with a mix of insert, read, and delete
     * operations. The workload is: 60% reads, 30% inserts, 10% deletes.</p>
     */
    @Benchmark
    public void mixedWorkload(Blackhole bh) {
        Random workloadRandom = new Random(456);
        int numOperations = datasetSize * 3; // 3x operations for meaningful measurement

        // Pre-populate with some data (25% of dataset)
        int initialSize = datasetSize / 4;
        for (int i = 0; i < initialSize; i++) {
            trie.put(keys.get(i), values.get(i));
        }

        int insertIndex = initialSize;

        for (int op = 0; op < numOperations; op++) {
            double operation = workloadRandom.nextDouble();

            if (operation < 0.6) {
                // Read operation (60%)
                if (insertIndex > 0) {
                    int readIndex = workloadRandom.nextInt(insertIndex);
                    byte[] result = trie.get(keys.get(readIndex));
                    bh.consume(result);
                }
            } else if (operation < 0.9) {
                // Insert operation (30%)
                if (insertIndex < datasetSize) {
                    trie.put(keys.get(insertIndex), values.get(insertIndex));
                    insertIndex++;
                }
            } else {
                // Delete operation (10%)
                if (insertIndex > initialSize) {
                    int deleteIndex = workloadRandom.nextInt(insertIndex);
                    trie.delete(keys.get(deleteIndex));
                }
            }
        }

        bh.consume(trie.getRootHash());
    }

    /**
     * Benchmarks key overwrite operations.
     *
     * <p>Measures performance when updating existing keys with new values.
     * This tests the path traversal and node update logic without tree
     * structure changes.</p>
     */
    @Benchmark
    public void keyOverwrites(Blackhole bh) {
        // Pre-populate trie
        for (int i = 0; i < datasetSize; i++) {
            trie.put(keys.get(i), values.get(i));
        }

        // Overwrite all keys with new values
        Random valueRandom = new Random(789);
        for (int i = 0; i < datasetSize; i++) {
            byte[] newValue = new byte[32];
            valueRandom.nextBytes(newValue);
            trie.put(keys.get(i), newValue);
        }

        bh.consume(trie.getRootHash());
    }

    /**
     * Benchmarks trie state persistence and reconstruction.
     *
     * <p>Measures the performance of creating a new trie instance from
     * an existing root hash. This tests the node loading and tree
     * reconstruction performance.</p>
     */
    @Benchmark
    public void stateReconstruction(Blackhole bh) {
        // Build initial trie
        for (int i = 0; i < datasetSize; i++) {
            trie.put(keys.get(i), values.get(i));
        }
        byte[] rootHash = trie.getRootHash();

        // Reconstruct from root hash and perform some operations
        MerklePatriciaTrie reconstructedTrie = new MerklePatriciaTrie(store, HASH_FN, rootHash);

        // Verify reconstruction by reading some values
        Random verifyRandom = new Random(999);
        int numVerifications = Math.min(100, datasetSize);
        for (int i = 0; i < numVerifications; i++) {
            int keyIndex = verifyRandom.nextInt(datasetSize);
            byte[] result = reconstructedTrie.get(keys.get(keyIndex));
            bh.consume(result);
        }
    }

    /**
     * Benchmarks large value storage and retrieval.
     *
     * <p>Tests performance with larger values (1KB each) to measure
     * the impact of value size on encoding and storage operations.</p>
     */
    @Benchmark
    public void largeValueOperations(Blackhole bh) {
        Random valueRandom = new Random(111);

        // Use smaller dataset for large values to keep benchmark reasonable
        int largeValueCount = Math.min(datasetSize / 10, 100);

        for (int i = 0; i < largeValueCount; i++) {
            byte[] largeValue = new byte[1024]; // 1KB values
            valueRandom.nextBytes(largeValue);
            trie.put(keys.get(i), largeValue);
        }

        // Read back the large values
        for (int i = 0; i < largeValueCount; i++) {
            byte[] result = trie.get(keys.get(i));
            bh.consume(result);
        }

        bh.consume(trie.getRootHash());
    }
}
