package com.bloxbean.cardano.statetrees.metrics;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTreeV2;
import com.bloxbean.cardano.statetrees.jmt.JmtProof;
import com.bloxbean.cardano.statetrees.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.store.InMemoryJmtStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test demonstrating JellyfishMerkleTreeV2 with Micrometer metrics.
 */
class JmtMetricsIntegrationTest {

    @Test
    void demonstrateMetricsIntegration() {
        // Setup
        MeterRegistry registry = new SimpleMeterRegistry();
        MicrometerJmtMetrics metrics = new MicrometerJmtMetrics(registry, "jmt.test");

        HashFunction hashFn = Blake2b256::digest;
        CommitmentScheme commitments = new ClassicJmtCommitmentScheme(hashFn);
        InMemoryJmtStore store = new InMemoryJmtStore();

        // Create JMT with metrics enabled
        JellyfishMerkleTreeV2 tree = new JellyfishMerkleTreeV2(store, commitments, hashFn, metrics);

        // Perform commits
        Map<byte[], byte[]> batch1 = new LinkedHashMap<>();
        for (int i = 0; i < 10; i++) {
            batch1.put(("key-" + i).getBytes(), ("value-" + i).getBytes());
        }
        tree.put(1, batch1);

        Map<byte[], byte[]> batch2 = new LinkedHashMap<>();
        for (int i = 10; i < 20; i++) {
            batch2.put(("key-" + i).getBytes(), ("value-" + i).getBytes());
        }
        tree.put(2, batch2);

        // Generate proofs
        Optional<JmtProof> proof1 = tree.getProof("key-5".getBytes(), 1);
        Optional<JmtProof> proof2 = tree.getProof("key-15".getBytes(), 2);
        Optional<JmtProof> proof3 = tree.getProof("key-999".getBytes(), 2); // Non-existent

        assertTrue(proof1.isPresent());
        assertTrue(proof2.isPresent());
        assertTrue(proof3.isPresent());

        // Verify metrics were recorded
        assertNotNull(registry.find("jmt.test.commit.duration").timer());
        assertEquals(2, registry.find("jmt.test.commit.duration").timer().count(),
                "Should have recorded 2 commits");

        assertNotNull(registry.find("jmt.test.proof.duration").timer());
        assertEquals(3, registry.find("jmt.test.proof.duration").timer().count(),
                "Should have recorded 3 proof generations");

        assertNotNull(registry.find("jmt.test.commit.batch_size").summary());
        assertNotNull(registry.find("jmt.test.commit.nodes_written").counter());
        assertNotNull(registry.find("jmt.test.commit.nodes_stale").counter());

        assertNotNull(registry.find("jmt.test.storage.version").gauge());
        assertEquals(2.0, registry.find("jmt.test.storage.version").gauge().value(), 0.01,
                "Storage version should be 2");

        assertNotNull(registry.find("jmt.test.cache.size").gauge());

        System.out.println("=== Metrics Summary ===");
        System.out.printf("Commits: %d%n",
                registry.find("jmt.test.commit.duration").timer().count());
        System.out.printf("Avg commit latency: %.2f ms%n",
                registry.find("jmt.test.commit.duration").timer().mean(java.util.concurrent.TimeUnit.MILLISECONDS));
        System.out.printf("Proofs generated: %d%n",
                registry.find("jmt.test.proof.duration").timer().count());
        System.out.printf("Avg proof latency: %.2f ms%n",
                registry.find("jmt.test.proof.duration").timer().mean(java.util.concurrent.TimeUnit.MILLISECONDS));
        System.out.printf("Nodes written: %.0f%n",
                registry.find("jmt.test.commit.nodes_written").counter().count());
        System.out.printf("Nodes stale: %.0f%n",
                registry.find("jmt.test.commit.nodes_stale").counter().count());
        System.out.printf("Current version: %.0f%n",
                registry.find("jmt.test.storage.version").gauge().value());
    }

    @Test
    void metricsDefaultToNoopWhenNotProvided() {
        // Setup without metrics
        HashFunction hashFn = Blake2b256::digest;
        CommitmentScheme commitments = new ClassicJmtCommitmentScheme(hashFn);
        InMemoryJmtStore store = new InMemoryJmtStore();

        // Create JMT without metrics (defaults to NOOP)
        JellyfishMerkleTreeV2 tree = new JellyfishMerkleTreeV2(store, commitments, hashFn);

        // Perform operations
        Map<byte[], byte[]> batch = new LinkedHashMap<>();
        batch.put("key1".getBytes(), "value1".getBytes());
        tree.put(1, batch);

        Optional<JmtProof> proof = tree.getProof("key1".getBytes(), 1);

        // Operations should succeed without metrics
        assertTrue(proof.isPresent());
        assertEquals(1, tree.put(1, batch).version());
    }
}
