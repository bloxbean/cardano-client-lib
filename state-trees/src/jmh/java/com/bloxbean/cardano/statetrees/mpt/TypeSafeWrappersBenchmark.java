package com.bloxbean.cardano.statetrees.mpt;

import com.bloxbean.cardano.statetrees.TestNodeStore;
import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.NodeHash;
import com.bloxbean.cardano.statetrees.common.NibblePath;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.common.nibbles.Nibbles;
import com.bloxbean.cardano.statetrees.mpt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.mpt.commitment.MpfCommitmentScheme;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks for type-safe wrapper classes to ensure zero performance overhead.
 *
 * <p>This benchmark suite specifically tests the performance impact of the new
 * type-safe wrapper classes ({@link NodeHash}, {@link NibblePath}, and
 * {@link NodePersistence}) compared to raw operations. The goal is to verify
 * that the abstraction layer introduces no measurable performance overhead.</p>
 *
 * <p><b>Compared Operations:</b></p>
 * <ul>
 *   <li>Raw byte array operations vs NodeHash operations</li>
 *   <li>Raw int array operations vs NibblePath operations</li>
 *   <li>Direct storage operations vs NodePersistence operations</li>
 *   <li>Object creation and garbage collection impact</li>
 * </ul>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class TypeSafeWrappersBenchmark {

    private static final HashFunction HASH_FN = Blake2b256::digest;

    @Param({"1000", "10000"})
    private int operationCount;

    private List<byte[]> hashes;
    private List<byte[]> keys;
    private List<int[]> nibblePaths;
    private TestNodeStore store;
    private NodePersistence persistence;
    private HashFunction hashFn;
    private CommitmentScheme commitments;
    private Random random;

    @Setup(Level.Trial)
    public void setupTrial() {
        random = new Random(42);

        // Pre-generate test data
        hashes = new ArrayList<>(operationCount);
        keys = new ArrayList<>(operationCount);
        nibblePaths = new ArrayList<>(operationCount);

        for (int i = 0; i < operationCount; i++) {
            // Generate 32-byte hashes
            byte[] hash = new byte[32];
            random.nextBytes(hash);
            hashes.add(hash);

            // Generate 8-byte keys
            byte[] key = new byte[8];
            random.nextBytes(key);
            keys.add(key);

            // Convert to nibbles for path operations
            nibblePaths.add(Nibbles.toNibbles(key));
        }

        store = new TestNodeStore();
        hashFn = Blake2b256::digest;
        commitments = new MpfCommitmentScheme(hashFn);
        persistence = new NodePersistence(store, commitments, hashFn);
    }

    @Setup(Level.Iteration)
    public void setupIteration() {
        store.clear();
    }

    /**
     * Baseline: Raw byte array hash operations.
     *
     * <p>Measures performance of working with raw byte arrays for hash values.</p>
     */
    @Benchmark
    public void rawHashOperations(Blackhole bh) {
        for (int i = 0; i < operationCount; i++) {
            byte[] hash = hashes.get(i);

            // Simulate typical hash operations
            byte[] copy = hash.clone();
            boolean equals = java.util.Arrays.equals(hash, copy);
            int hashCode = java.util.Arrays.hashCode(hash);

            bh.consume(copy);
            bh.consume(equals);
            bh.consume(hashCode);
        }
    }

    /**
     * Type-safe: NodeHash wrapper operations.
     *
     * <p>Measures performance of the same operations using NodeHash wrapper.</p>
     */
    @Benchmark
    public void nodeHashOperations(Blackhole bh) {
        for (int i = 0; i < operationCount; i++) {
            NodeHash nodeHash = NodeHash.of(hashes.get(i));

            // Simulate typical hash operations
            byte[] copy = nodeHash.getBytes();
            NodeHash other = NodeHash.of(hashes.get(i));
            boolean equals = nodeHash.equals(other);
            int hashCode = nodeHash.hashCode();

            bh.consume(copy);
            bh.consume(equals);
            bh.consume(hashCode);
        }
    }

    /**
     * Baseline: Raw int array nibble operations.
     *
     * <p>Measures performance of working with raw int arrays for nibble paths.</p>
     */
    @Benchmark
    public void rawNibbleOperations(Blackhole bh) {
        for (int i = 0; i < operationCount; i++) {
            int[] nibbles = nibblePaths.get(i);

            // Simulate typical nibble operations
            int[] copy = nibbles.clone();
            int length = nibbles.length;

            // Slice operation
            if (length > 4) {
                int[] slice = java.util.Arrays.copyOfRange(nibbles, 2, length - 2);
                bh.consume(slice);
            }

            // Common prefix calculation
            if (i > 0) {
                int[] other = nibblePaths.get(i - 1);
                int commonLength = 0;
                int minLength = Math.min(nibbles.length, other.length);
                for (int j = 0; j < minLength; j++) {
                    if (nibbles[j] == other[j]) {
                        commonLength++;
                    } else {
                        break;
                    }
                }
                bh.consume(commonLength);
            }

            bh.consume(copy);
            bh.consume(length);
        }
    }

    /**
     * Type-safe: NibblePath wrapper operations.
     *
     * <p>Measures performance of the same operations using NibblePath wrapper.</p>
     */
    @Benchmark
    public void nibblePathOperations(Blackhole bh) {
        for (int i = 0; i < operationCount; i++) {
            NibblePath nibblePath = NibblePath.of(nibblePaths.get(i));

            // Simulate typical nibble operations
            int[] copy = nibblePath.getNibbles();
            int length = nibblePath.length();

            // Slice operation
            if (length > 4) {
                NibblePath slice = nibblePath.slice(2, length - 2);
                bh.consume(slice);
            }

            // Common prefix calculation
            if (i > 0) {
                NibblePath other = NibblePath.of(nibblePaths.get(i - 1));
                int commonLength = nibblePath.commonPrefixLength(other);
                bh.consume(commonLength);
            }

            bh.consume(copy);
            bh.consume(length);
        }
    }

    /**
     * Baseline: Direct storage operations.
     *
     * <p>Measures performance of direct NodeStore operations.</p>
     */
    @Benchmark
    public void rawStorageOperations(Blackhole bh) throws Exception {
        for (int i = 0; i < Math.min(operationCount, 100); i++) {
            // Create test leaf node
            LeafNode node = createTestLeafNode(i);

            byte[] hash = node.commit(hashFn, commitments);
            byte[] encoded = node.encode();

            // Store
            store.put(hash, encoded);

            // Load
            byte[] retrieved = store.get(hash);

            // Delete
            store.delete(hash);

            bh.consume(retrieved);
        }
    }

    /**
     * Type-safe: NodePersistence operations.
     *
     * <p>Measures performance of the same operations using NodePersistence wrapper.</p>
     */
    @Benchmark
    public void persistenceLayerOperations(Blackhole bh) throws Exception {
        for (int i = 0; i < Math.min(operationCount, 100); i++) {
            // Create test leaf node
            LeafNode node = createTestLeafNode(i);

            // Store
            NodeHash hash = persistence.persist(node);

            // Load
            var retrieved = persistence.load(hash);

            // Delete
            persistence.delete(hash);

            bh.consume(retrieved);
        }
    }

    /**
     * Benchmarks hex string conversion overhead.
     *
     * <p>Compares raw byte-to-hex conversion with NodeHash hex conversion.</p>
     */
    @Benchmark
    public void hexStringConversion(Blackhole bh) {
        for (int i = 0; i < operationCount; i++) {
            byte[] hash = hashes.get(i);

            // Raw conversion
            StringBuilder rawHex = new StringBuilder();
            for (byte b : hash) {
                rawHex.append(String.format("%02x", b & 0xFF));
            }

            // NodeHash conversion
            NodeHash nodeHash = NodeHash.of(hash);
            String wrapperHex = nodeHash.toHexString();

            bh.consume(rawHex.toString());
            bh.consume(wrapperHex);
        }
    }

    /**
     * Benchmarks object creation overhead.
     *
     * <p>Measures the allocation and GC overhead of creating wrapper objects
     * compared to working with raw arrays.</p>
     */
    @Benchmark
    public void objectCreationOverhead(Blackhole bh) {
        // Test NodeHash creation
        for (int i = 0; i < operationCount; i++) {
            NodeHash hash = NodeHash.of(hashes.get(i));
            bh.consume(hash);
        }

        // Test NibblePath creation
        for (int i = 0; i < operationCount; i++) {
            NibblePath path = NibblePath.of(nibblePaths.get(i));
            bh.consume(path);
        }
    }

    /**
     * Benchmarks round-trip conversions.
     *
     * <p>Tests the overhead of converting between raw types and wrappers
     * in typical usage scenarios.</p>
     */
    @Benchmark
    public void roundTripConversions(Blackhole bh) {
        for (int i = 0; i < operationCount; i++) {
            // NodeHash round trip
            byte[] originalHash = hashes.get(i);
            NodeHash nodeHash = NodeHash.of(originalHash);
            byte[] convertedHash = nodeHash.getBytes();

            // NibblePath round trip
            int[] originalNibbles = nibblePaths.get(i);
            NibblePath nibblePath = NibblePath.of(originalNibbles);
            int[] convertedNibbles = nibblePath.getNibbles();

            bh.consume(convertedHash);
            bh.consume(convertedNibbles);
        }
    }

    // Helper method to create test leaf nodes
    private LeafNode createTestLeafNode(int index) {
        byte[] hp = Nibbles.packHP(true, new int[]{index % 16, (index / 16) % 16});
        byte[] value = ("value-" + index).getBytes(StandardCharsets.UTF_8);
        return LeafNode.of(hp, value);
    }
}
