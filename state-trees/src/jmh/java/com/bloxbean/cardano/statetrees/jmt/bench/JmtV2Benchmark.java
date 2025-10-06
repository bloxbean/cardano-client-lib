package com.bloxbean.cardano.statetrees.jmt.bench;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTreeV2;
import com.bloxbean.cardano.statetrees.jmt.JmtProof;
import com.bloxbean.cardano.statetrees.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.store.InMemoryJmtStore;
import com.bloxbean.cardano.statetrees.jmt.store.JmtStore;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmark for JellyfishMerkleTreeV2 (Diem-style implementation).
 *
 * <p>Benchmarks key operations:
 * <ul>
 *   <li>Single inserts (commit throughput)</li>
 *   <li>Batch inserts (various batch sizes)</li>
 *   <li>Updates (hot key updates)</li>
 *   <li>Proof generation</li>
 * </ul>
 *
 * <p>Run with:
 * <pre>
 * ./gradlew :state-trees:jmh -Pjmh.include=".*JmtV2Benchmark.*"
 * </pre>
 *
 * <p>Run specific benchmark:
 * <pre>
 * ./gradlew :state-trees:jmh -Pjmh.include=".*insertBatch.*"
 * </pre>
 *
 * <p>With GC profiler:
 * <pre>
 * ./gradlew :state-trees:jmh -Pjmh.include=".*JmtV2Benchmark.*" -Pjmh.prof=gc
 * </pre>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class JmtV2Benchmark {

    /**
     * Batch size for insert benchmarks.
     * Test with small (10), medium (100), and large (1000) batches.
     */
    @Param({"10", "100", "1000"})
    public int batchSize;

    /**
     * Initial tree size for setup.
     * Benchmarks run against trees with this many keys already inserted.
     */
    @Param({"1000", "10000"})
    public int initialTreeSize;

    private final HashFunction hashFn = Blake2b256::digest;
    private final CommitmentScheme commitments = new ClassicJmtCommitmentScheme(hashFn);

    private JmtStore store;
    private JellyfishMerkleTreeV2 tree;
    private long version;

    // Pre-generated keys for consistent benchmarking
    private List<byte[]> existingKeys;
    private int keyIndex = 0;

    @Setup(Level.Trial)
    public void setUp() {
        // Create in-memory store
        store = new InMemoryJmtStore();
        tree = new JellyfishMerkleTreeV2(store, commitments, hashFn);

        // Build initial tree
        version = 0;
        existingKeys = new ArrayList<>();
        int numBatches = initialTreeSize / 100; // Insert in batches of 100

        for (int b = 0; b < numBatches; b++) {
            Map<byte[], byte[]> batch = new HashMap<>();
            for (int i = 0; i < 100; i++) {
                byte[] key = randomKey();
                byte[] value = randomValue();
                batch.put(key, value);
                existingKeys.add(key);
            }
            tree.put(++version, batch);
        }

        keyIndex = 0;
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        // Cleanup
        if (store instanceof AutoCloseable) {
            try {
                ((AutoCloseable) store).close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    /**
     * Benchmark: Insert new keys in batches.
     * Measures TreeCache efficiency and commit throughput.
     */
    @Benchmark
    public void insertBatch(Blackhole bh) {
        Map<byte[], byte[]> updates = new HashMap<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            updates.put(randomKey(), randomValue());
        }

        JellyfishMerkleTreeV2.CommitResult result = tree.put(++version, updates);
        bh.consume(result.rootHash());
    }

    /**
     * Benchmark: Update existing keys.
     * Measures update performance vs insert performance.
     */
    @Benchmark
    public void updateExistingKeys(Blackhole bh) {
        Map<byte[], byte[]> updates = new HashMap<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            byte[] existingKey = existingKeys.get(nextKeyIndex());
            updates.put(existingKey, randomValue());
        }

        JellyfishMerkleTreeV2.CommitResult result = tree.put(++version, updates);
        bh.consume(result.rootHash());
    }

    /**
     * Benchmark: Mixed insert/update workload (50/50).
     * Measures realistic blockchain transaction patterns.
     */
    @Benchmark
    public void mixedInsertUpdate(Blackhole bh) {
        Map<byte[], byte[]> updates = new HashMap<>(batchSize);

        // 50% new inserts
        for (int i = 0; i < batchSize / 2; i++) {
            updates.put(randomKey(), randomValue());
        }

        // 50% updates
        for (int i = 0; i < batchSize / 2; i++) {
            byte[] existingKey = existingKeys.get(nextKeyIndex());
            updates.put(existingKey, randomValue());
        }

        JellyfishMerkleTreeV2.CommitResult result = tree.put(++version, updates);
        bh.consume(result.rootHash());
    }

    /**
     * Benchmark: Single key insert.
     * Measures minimum latency for small transactions.
     */
    @Benchmark
    public void insertSingleKey(Blackhole bh) {
        Map<byte[], byte[]> updates = Map.of(randomKey(), randomValue());

        JellyfishMerkleTreeV2.CommitResult result = tree.put(++version, updates);
        bh.consume(result.rootHash());
    }

    /**
     * Benchmark: Proof generation for existing keys.
     * Measures read performance and tree traversal.
     */
    @Benchmark
    public void generateProof(Blackhole bh) {
        byte[] key = existingKeys.get(nextKeyIndex());
        byte[] keyHash = hashFn.digest(key);

        JmtProof proof = tree.getProof(keyHash, version).get();
        bh.consume(proof);
    }

    /**
     * Benchmark: Batch proof generation.
     * Measures proof generation for multiple keys.
     */
    @Benchmark
    public void generateBatchProofs(Blackhole bh) {
        List<JmtProof> proofs = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            byte[] key = existingKeys.get(nextKeyIndex());
            byte[] keyHash = hashFn.digest(key);
            proofs.add(tree.getProof(keyHash, version).get());
        }
        bh.consume(proofs);
    }

    // ========== Helper Methods ==========

    private byte[] randomKey() {
        byte[] key = new byte[32];
        ThreadLocalRandom.current().nextBytes(key);
        return key;
    }

    private byte[] randomValue() {
        byte[] value = new byte[64];
        ThreadLocalRandom.current().nextBytes(value);
        return value;
    }

    private int nextKeyIndex() {
        if (existingKeys.isEmpty()) {
            throw new IllegalStateException("No existing keys");
        }
        int idx = keyIndex;
        keyIndex = (keyIndex + 1) % existingKeys.size();
        return idx;
    }

    /**
     * Main method to run benchmarks from command line.
     */
    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }
}
