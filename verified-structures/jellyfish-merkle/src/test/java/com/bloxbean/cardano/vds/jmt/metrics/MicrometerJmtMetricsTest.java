package com.bloxbean.cardano.vds.jmt.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MicrometerJmtMetrics integration.
 */
class MicrometerJmtMetricsTest {

    @Test
    void createsMetricsWithSimpleRegistry() {
        MeterRegistry registry = new SimpleMeterRegistry();
        JmtMetrics metrics = new MicrometerJmtMetrics(registry, "test.jmt");

        // Record some metrics
        metrics.recordCommit(100, 1, 10, 5, 2);
        metrics.recordProofGeneration(5, 3, true);
        metrics.recordRead(2, true, true);
        metrics.recordPrune(500, 100, 50, 30);
        metrics.recordStorageStats(1, 32, 1000, 500);
        metrics.recordCacheStats(100, 10, 50);
        metrics.recordRocksDbStats(1024 * 1024, 2, 512 * 1024, 1);

        // Verify metrics were registered
        assertNotNull(registry.find("test.jmt.commit.duration").timer());
        assertNotNull(registry.find("test.jmt.commit.batch_size").summary());
        assertNotNull(registry.find("test.jmt.commit.nodes_written").counter());
        assertNotNull(registry.find("test.jmt.proof.duration").timer());
        assertNotNull(registry.find("test.jmt.cache.hits").counter());
        assertNotNull(registry.find("test.jmt.storage.version").gauge());
        assertNotNull(registry.find("test.jmt.rocksdb.pending_compaction_bytes").gauge());

        // Verify values
        assertEquals(1, registry.find("test.jmt.commit.duration").timer().count());
        assertEquals(1, registry.find("test.jmt.proof.duration").timer().count());
        assertEquals(1, registry.find("test.jmt.cache.hits").counter().count());
        assertEquals(5, registry.find("test.jmt.commit.nodes_written").counter().count());
    }

    @Test
    void createsMetricsWithPrometheusRegistry() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        JmtMetrics metrics = new MicrometerJmtMetrics(registry);

        // Record metrics
        metrics.recordCommit(150, 2, 20, 10, 3);

        // Get Prometheus output
        String prometheusOutput = registry.scrape();

        // Verify Prometheus format
        assertTrue(prometheusOutput.contains("jmt_commit_duration"));
        assertTrue(prometheusOutput.contains("jmt_commit_batch_size"));
        assertTrue(prometheusOutput.contains("jmt_commit_nodes_written_total"));

        System.out.println("=== Prometheus Metrics Sample ===");
        System.out.println(prometheusOutput.lines()
                .filter(line -> line.startsWith("jmt_"))
                .limit(10)
                .reduce("", (a, b) -> a + b + "\n"));
    }

    @Test
    void handlesMultipleCommits() {
        MeterRegistry registry = new SimpleMeterRegistry();
        JmtMetrics metrics = new MicrometerJmtMetrics(registry, "perf");

        // Simulate multiple commits
        for (int i = 0; i < 100; i++) {
            metrics.recordCommit(
                    10 + (i % 20), // Duration varies 10-30ms
                    i,
                    100 + (i % 50), // Batch size varies
                    50,
                    5
            );
        }

        // Verify aggregation
        assertEquals(100, registry.find("perf.commit.duration").timer().count());
        assertEquals(5000, registry.find("perf.commit.nodes_written").counter().count()); // 50 * 100
        assertTrue(registry.find("perf.commit.duration").timer().mean(java.util.concurrent.TimeUnit.MILLISECONDS) > 0);
    }

    @Test
    void gaugesUpdateCorrectly() {
        MeterRegistry registry = new SimpleMeterRegistry();
        JmtMetrics metrics = new MicrometerJmtMetrics(registry);

        // Update storage version
        metrics.recordStorageStats(100, 32, 10000, 5000);
        assertEquals(100.0, registry.find("jmt.storage.version").gauge().value(), 0.01);

        // Update cache size
        metrics.recordCacheStats(1000, 100, 500);
        assertEquals(500.0, registry.find("jmt.cache.size").gauge().value(), 0.01);

        // Update RocksDB stats
        metrics.recordRocksDbStats(2 * 1024 * 1024, 3, 1024 * 1024, 2);
        assertEquals(2 * 1024.0 * 1024.0, registry.find("jmt.rocksdb.pending_compaction_bytes").gauge().value(), 0.01);
        assertEquals(3.0, registry.find("jmt.rocksdb.running_compactions").gauge().value(), 0.01);
    }

    @Test
    void throwsExceptionForNullRegistry() {
        assertThrows(IllegalArgumentException.class, () -> new MicrometerJmtMetrics(null));
    }

    @Test
    void usesDefaultPrefixWhenNull() {
        MeterRegistry registry = new SimpleMeterRegistry();
        JmtMetrics metrics = new MicrometerJmtMetrics(registry, null);

        metrics.recordCommit(100, 1, 10, 5, 2);

        // Should use "jmt" prefix
        assertNotNull(registry.find("jmt.commit.duration").timer());
    }
}
