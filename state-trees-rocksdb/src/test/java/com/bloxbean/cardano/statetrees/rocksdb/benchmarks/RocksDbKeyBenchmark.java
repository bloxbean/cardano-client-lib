package com.bloxbean.cardano.statetrees.rocksdb.benchmarks;

import com.bloxbean.cardano.statetrees.rocksdb.keys.NodeHashKey;
import com.bloxbean.cardano.statetrees.rocksdb.keys.RefcountKey;
import com.bloxbean.cardano.statetrees.rocksdb.keys.SpecialKey;
import com.bloxbean.cardano.statetrees.rocksdb.keys.VersionKey;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for type-safe key operations.
 *
 * <p>This benchmark class establishes performance baselines for the type-safe
 * key system to ensure that the modernization doesn't introduce performance
 * regressions. It measures key creation, conversion, and comparison operations.</p>
 *
 * <p><b>Benchmark Categories:</b></p>
 * <ul>
 *   <li>Key Creation - Factory method performance</li>
 *   <li>Key Conversion - toBytes() operation overhead</li>
 *   <li>Key Comparison - equals() and hashCode() performance</li>
 *   <li>Raw vs Type-Safe - Comparison with raw byte arrays</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * // Run with JMH runner when available
 * // For now, can be run as a simple performance test
 * RocksDbKeyBenchmark benchmark = new RocksDbKeyBenchmark();
 * benchmark.setup();
 *
 * long start = System.nanoTime();
 * for (int i = 0; i < 100000; i++) {
 *     benchmark.benchmarkNodeHashKeyCreation();
 * }
 * long duration = System.nanoTime() - start;
 * System.out.println("NodeHashKey creation: " + (duration / 100000) + " ns/op");
 * }</pre>
 *
 * @author Bloxbean Project
 * @since 0.6.0
 */
// @BenchmarkMode(Mode.AverageTime)  // Uncomment when JMH is available
// @OutputTimeUnit(TimeUnit.NANOSECONDS)
// @State(Scope.Benchmark)
public class RocksDbKeyBenchmark {

    private SecureRandom random;
    private byte[][] testHashes;
    private long[] testVersions;
    private NodeHashKey[] nodeKeys;
    private VersionKey[] versionKeys;
    private String[] hashHexStrings;

    // @Setup  // Uncomment when JMH is available
    public void setup() {
        random = new SecureRandom();

        // Pre-generate test data to avoid measurement noise
        testHashes = new byte[1000][];
        testVersions = new long[1000];
        nodeKeys = new NodeHashKey[1000];
        versionKeys = new VersionKey[1000];
        hashHexStrings = new String[1000];

        for (int i = 0; i < 1000; i++) {
            // Generate 32-byte hashes
            testHashes[i] = new byte[32];
            random.nextBytes(testHashes[i]);

            // Generate version numbers
            testVersions[i] = random.nextLong();

            // Pre-create keys for comparison benchmarks
            nodeKeys[i] = NodeHashKey.of(testHashes[i]);
            versionKeys[i] = VersionKey.of(testVersions[i]);

            // Pre-create hex strings for comparison
            hashHexStrings[i] = bytesToHex(testHashes[i]);
        }
    }

    // @Benchmark  // Uncomment when JMH is available
    public NodeHashKey benchmarkNodeHashKeyCreation() {
        byte[] hash = testHashes[random.nextInt(testHashes.length)];
        return NodeHashKey.of(hash);
    }

    // @Benchmark  // Uncomment when JMH is available
    public byte[] benchmarkNodeHashKeyToBytes() {
        NodeHashKey key = nodeKeys[random.nextInt(nodeKeys.length)];
        return key.toBytes();
    }

    // @Benchmark  // Uncomment when JMH is available
    public VersionKey benchmarkVersionKeyCreation() {
        long version = testVersions[random.nextInt(testVersions.length)];
        return VersionKey.of(version);
    }

    // @Benchmark  // Uncomment when JMH is available
    public byte[] benchmarkVersionKeyToBytes() {
        VersionKey key = versionKeys[random.nextInt(versionKeys.length)];
        return key.toBytes();
    }

    // @Benchmark  // Uncomment when JMH is available
    public RefcountKey benchmarkRefcountKeyCreation() {
        NodeHashKey nodeKey = nodeKeys[random.nextInt(nodeKeys.length)];
        return RefcountKey.forNode(nodeKey);
    }

    // @Benchmark  // Uncomment when JMH is available
    public SpecialKey benchmarkSpecialKeyCreation() {
        return SpecialKey.of("BENCHMARK_" + random.nextInt(100));
    }

    // @Benchmark  // Uncomment when JMH is available
    public boolean benchmarkNodeHashKeyEquals() {
        int index1 = random.nextInt(nodeKeys.length);
        int index2 = random.nextInt(nodeKeys.length);
        return nodeKeys[index1].equals(nodeKeys[index2]);
    }

    // @Benchmark  // Uncomment when JMH is available
    public int benchmarkNodeHashKeyHashCode() {
        NodeHashKey key = nodeKeys[random.nextInt(nodeKeys.length)];
        return key.hashCode();
    }

    /**
     * Baseline benchmark: Raw byte array operations for comparison.
     * This helps measure the overhead of the type-safe key system.
     */
    // @Benchmark  // Uncomment when JMH is available
    public byte[] benchmarkRawByteArrayClone() {
        byte[] hash = testHashes[random.nextInt(testHashes.length)];
        return hash.clone();
    }

    /**
     * Simple performance test runner for when JMH is not available.
     *
     * <p>This method provides basic performance measurements that can be
     * run without the full JMH framework. It's useful for quick performance
     * checks during development.</p>
     */
    public void runSimplePerformanceTest() {
        setup();

        System.out.println("=== RocksDB Key Performance Baseline ===");

        // Warmup
        System.out.println("Warming up...");
        for (int i = 0; i < 10000; i++) {
            benchmarkNodeHashKeyCreation();
            benchmarkVersionKeyCreation();
            benchmarkRefcountKeyCreation();
        }

        // Measure NodeHashKey creation
        int iterations = 100000;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            benchmarkNodeHashKeyCreation();
        }
        long duration = System.nanoTime() - start;
        System.out.printf("NodeHashKey creation: %.2f ns/op%n", (double) duration / iterations);

        // Measure NodeHashKey toBytes()
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            benchmarkNodeHashKeyToBytes();
        }
        duration = System.nanoTime() - start;
        System.out.printf("NodeHashKey toBytes(): %.2f ns/op%n", (double) duration / iterations);

        // Measure VersionKey creation
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            benchmarkVersionKeyCreation();
        }
        duration = System.nanoTime() - start;
        System.out.printf("VersionKey creation: %.2f ns/op%n", (double) duration / iterations);

        // Measure RefcountKey creation
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            benchmarkRefcountKeyCreation();
        }
        duration = System.nanoTime() - start;
        System.out.printf("RefcountKey creation: %.2f ns/op%n", (double) duration / iterations);

        // Measure key equality
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            benchmarkNodeHashKeyEquals();
        }
        duration = System.nanoTime() - start;
        System.out.printf("NodeHashKey equals(): %.2f ns/op%n", (double) duration / iterations);

        // Measure raw byte array baseline
        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            benchmarkRawByteArrayClone();
        }
        duration = System.nanoTime() - start;
        System.out.printf("Raw byte array clone: %.2f ns/op%n", (double) duration / iterations);

        System.out.println("=== Performance test completed ===");
    }

    /**
     * Main method for running the performance test standalone.
     */
    public static void main(String[] args) {
        RocksDbKeyBenchmark benchmark = new RocksDbKeyBenchmark();
        benchmark.runSimplePerformanceTest();
    }

    /**
     * Utility method to convert bytes to hex string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b & 0xff));
        }
        return hex.toString();
    }
}
