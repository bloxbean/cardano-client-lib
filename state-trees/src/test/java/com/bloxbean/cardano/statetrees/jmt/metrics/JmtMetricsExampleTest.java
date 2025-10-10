package com.bloxbean.cardano.statetrees.jmt.metrics;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.statetrees.jmt.JmtMetrics;
import com.bloxbean.cardano.statetrees.jmt.JmtProof;
import com.bloxbean.cardano.statetrees.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.store.InMemoryJmtStore;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Example demonstrating JMT metrics usage.
 * Shows how to wrap JMT operations to collect metrics.
 */
class JmtMetricsExampleTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new ClassicJmtCommitmentScheme(HASH);

    @Test
    void demonstrateMetricsWrapping() {
        // Create a simple in-memory metrics collector
        SimpleMetricsCollector collector = new SimpleMetricsCollector();

        // Create JMT tree
        InMemoryJmtStore store = new InMemoryJmtStore();
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store, COMMITMENTS, HASH);

        // Wrap operations with metrics
        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        updates.put("alice".getBytes(), "100".getBytes());
        updates.put("bob".getBytes(), "200".getBytes());

        // Record commit metrics
        long commitStart = System.currentTimeMillis();
        JellyfishMerkleTree.CommitResult result = tree.put(1, updates);
        long commitDuration = System.currentTimeMillis() - commitStart;

        collector.recordCommit(
            commitDuration,
            1L,
            updates.size(),
            result.nodes().size(),
            result.staleNodes().size()
        );

        // Record proof generation metrics
        byte[] keyHash = HASH.digest("alice".getBytes());
        long proofStart = System.currentTimeMillis();
        Optional<JmtProof> proof = tree.getProof(keyHash, 1);
        long proofDuration = System.currentTimeMillis() - proofStart;

        assertTrue(proof.isPresent());
        collector.recordProofGeneration(
            proofDuration,
            proof.get().steps().size(),
            proof.get().type() == JmtProof.ProofType.INCLUSION
        );

        // Verify metrics were collected
        assertTrue(collector.commitCount.get() > 0, "Commit metrics should be recorded");
        assertTrue(collector.proofCount.get() > 0, "Proof metrics should be recorded");
        assertEquals(2, collector.lastBatchSize.get(), "Batch size should be 2");
        assertTrue(collector.lastNodesWritten.get() > 0, "Nodes written should be > 0");

        System.out.println("=== Collected Metrics ===");
        System.out.println("Commits: " + collector.commitCount.get());
        System.out.println("Commit duration: " + collector.lastCommitDuration.get() + "ms");
        System.out.println("Batch size: " + collector.lastBatchSize.get());
        System.out.println("Nodes written: " + collector.lastNodesWritten.get());
        System.out.println("Nodes stale: " + collector.lastNodesStale.get());
        System.out.println();
        System.out.println("Proofs: " + collector.proofCount.get());
        System.out.println("Proof duration: " + collector.lastProofDuration.get() + "ms");
        System.out.println("Proof size: " + collector.lastProofSize.get());
    }

    @Test
    void demonstrateNoOpMetrics() {
        // When metrics are disabled, use NOOP for zero overhead
        JmtMetrics metrics = JmtMetrics.NOOP;

        // All calls are no-ops (JIT will likely eliminate these entirely)
        metrics.recordCommit(100, 1, 10, 5, 2);
        metrics.recordProofGeneration(5, 10, true);
        metrics.recordRead(2, true, true);
        metrics.recordPrune(500, 100, 50, 30);

        // No exceptions, no overhead
        assertTrue(true, "NOOP metrics should work without errors");
    }

    /**
     * Simple in-memory metrics collector for demonstration.
     * In production, use MicrometerJmtMetrics with Prometheus.
     */
    private static class SimpleMetricsCollector implements JmtMetrics {
        final AtomicLong commitCount = new AtomicLong(0);
        final AtomicLong proofCount = new AtomicLong(0);
        final AtomicLong readCount = new AtomicLong(0);
        final AtomicLong pruneCount = new AtomicLong(0);

        final AtomicLong lastCommitDuration = new AtomicLong(0);
        final AtomicLong lastBatchSize = new AtomicLong(0);
        final AtomicLong lastNodesWritten = new AtomicLong(0);
        final AtomicLong lastNodesStale = new AtomicLong(0);

        final AtomicLong lastProofDuration = new AtomicLong(0);
        final AtomicLong lastProofSize = new AtomicLong(0);

        @Override
        public void recordCommit(long durationMillis, long version, int batchSize,
                                  int nodesWritten, int nodesStale) {
            commitCount.incrementAndGet();
            lastCommitDuration.set(durationMillis);
            lastBatchSize.set(batchSize);
            lastNodesWritten.set(nodesWritten);
            lastNodesStale.set(nodesStale);
        }

        @Override
        public void recordProofGeneration(long durationMillis, int proofSize, boolean found) {
            proofCount.incrementAndGet();
            lastProofDuration.set(durationMillis);
            lastProofSize.set(proofSize);
        }

        @Override
        public void recordRead(long durationMillis, boolean cacheHit, boolean found) {
            readCount.incrementAndGet();
        }

        @Override
        public void recordPrune(long durationMillis, long versionPruned, int nodesPruned, int valuesPruned) {
            pruneCount.incrementAndGet();
        }

        @Override
        public void recordStorageStats(long version, int rootHashSize, long nodeCount, long valueCount) {
        }

        @Override
        public void recordCacheStats(long cacheHits, long cacheMisses, int cacheSize) {
        }

        @Override
        public void recordRocksDbStats(long pendingCompactionBytes, int runningCompactions,
                                        long memTableSize, int immutableMemTables) {
        }
    }
}
