package com.bloxbean.cardano.vds.jmt.metrics;

import io.micrometer.core.instrument.*;

/**
 * Micrometer-based implementation of {@link JmtMetrics}.
 *
 * <p><b>Standard Practice:</b> This is a separate optional module with {@code compileOnly}
 * dependency on Micrometer. Applications choose their own Micrometer version at runtime.
 *
 * <p><b>Gradle Setup:</b>
 * <pre>
 * // Core JMT (no metrics dependency)
 * implementation 'com.bloxbean.cardano:state-trees:version'
 *
 * // Optional: Add metrics support
 * implementation 'com.bloxbean.cardano:state-trees-metrics:version'
 *
 * // Application provides Micrometer version
 * implementation 'io.micrometer:micrometer-core:1.15.4'
 * implementation 'io.micrometer:micrometer-registry-prometheus:1.15.4'
 * </pre>
 *
 * <p><b>Usage:</b>
 * <pre>
 * // Create metrics with your MeterRegistry
 * MeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
 * JmtMetrics metrics = new MicrometerJmtMetrics(registry, "jmt");
 *
 * // Use in application wrapper
 * MetricsAwareJmtWrapper wrapper = new MetricsAwareJmtWrapper(tree, metrics);
 * </pre>
 *
 * <p><b>Exposed Metrics:</b>
 * <ul>
 *   <li>{@code jmt.commit.duration} - Timer (p50/p95/p99)</li>
 *   <li>{@code jmt.commit.batch_size} - Distribution summary</li>
 *   <li>{@code jmt.commit.nodes_written} - Counter</li>
 *   <li>{@code jmt.commit.nodes_stale} - Counter</li>
 *   <li>{@code jmt.proof.duration} - Timer (p50/p95/p99)</li>
 *   <li>{@code jmt.read.duration} - Timer (p50/p95/p99)</li>
 *   <li>{@code jmt.cache.hits/misses} - Counters</li>
 *   <li>{@code jmt.prune.duration} - Timer</li>
 *   <li>{@code jmt.storage.version} - Gauge</li>
 *   <li>{@code jmt.rocksdb.*} - RocksDB health gauges</li>
 * </ul>
 */
public final class MicrometerJmtMetrics implements JmtMetrics {

    private final MeterRegistry registry;
    private final String prefix;

    // Lazy-initialized metrics (thread-safe double-check locking)
    private volatile Timer commitTimer;
    private volatile DistributionSummary commitBatchSizeSummary;
    private volatile Counter commitNodesWrittenCounter;
    private volatile Counter commitNodesStaleCounter;

    private volatile Timer proofTimer;
    private volatile DistributionSummary proofSizeSummary;

    private volatile Timer readTimer;
    private volatile Counter cacheHitCounter;
    private volatile Counter cacheMissCounter;

    private volatile Timer pruneTimer;
    private volatile Counter pruneNodesCounter;
    private volatile Counter pruneValuesCounter;

    private final AtomicLong storageVersionGauge = new AtomicLong(0);
    private final AtomicLong cacheSizeGauge = new AtomicLong(0);
    private final AtomicLong rocksDbPendingCompactionGauge = new AtomicLong(0);
    private final AtomicLong rocksDbRunningCompactionsGauge = new AtomicLong(0);
    private final AtomicLong rocksDbMemTableSizeGauge = new AtomicLong(0);
    private final AtomicLong rocksDbImmutableMemTablesGauge = new AtomicLong(0);

    /**
     * Creates a Micrometer-based metrics implementation.
     *
     * @param registry Micrometer {@code MeterRegistry} instance
     * @param prefix   metric name prefix (e.g., "jmt" produces "jmt.commit.duration")
     */
    public MicrometerJmtMetrics(MeterRegistry registry, String prefix) {
        if (registry == null) {
            throw new IllegalArgumentException("registry cannot be null");
        }
        this.registry = registry;
        this.prefix = prefix != null ? prefix : "jmt";
        initializeGauges();
    }

    /**
     * Creates a Micrometer-based metrics implementation with default prefix "jmt".
     *
     * @param registry Micrometer {@code MeterRegistry} instance
     */
    public MicrometerJmtMetrics(MeterRegistry registry) {
        this(registry, "jmt");
    }

    private void initializeGauges() {
        // Register gauges early so they appear in metrics even before first update
        Gauge.builder(prefix + ".storage.version", storageVersionGauge, AtomicLong::doubleValue)
                .description("Current JMT tree version")
                .register(registry);

        Gauge.builder(prefix + ".cache.size", cacheSizeGauge, AtomicLong::doubleValue)
                .description("TreeCache size (number of entries)")
                .register(registry);

        Gauge.builder(prefix + ".rocksdb.pending_compaction_bytes", rocksDbPendingCompactionGauge, AtomicLong::doubleValue)
                .description("RocksDB estimated pending compaction bytes")
                .register(registry);

        Gauge.builder(prefix + ".rocksdb.running_compactions", rocksDbRunningCompactionsGauge, AtomicLong::doubleValue)
                .description("RocksDB number of running compactions")
                .register(registry);

        Gauge.builder(prefix + ".rocksdb.memtable_size_bytes", rocksDbMemTableSizeGauge, AtomicLong::doubleValue)
                .description("RocksDB active memtable size in bytes")
                .register(registry);

        Gauge.builder(prefix + ".rocksdb.immutable_memtables", rocksDbImmutableMemTablesGauge, AtomicLong::doubleValue)
                .description("RocksDB number of immutable memtables")
                .register(registry);
    }

    @Override
    public void recordCommit(long durationMillis, long version, int batchSize, int nodesWritten, int nodesStale) {
        getCommitTimer().record(durationMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
        getCommitBatchSizeSummary().record(batchSize);
        getCommitNodesWrittenCounter().increment(nodesWritten);
        getCommitNodesStaleCounter().increment(nodesStale);
    }

    @Override
    public void recordProofGeneration(long durationMillis, int proofSize, boolean found) {
        getProofTimer().record(durationMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
        getProofSizeSummary().record(proofSize);
    }

    @Override
    public void recordRead(long durationMillis, boolean cacheHit, boolean found) {
        getReadTimer().record(durationMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
        if (cacheHit) {
            getCacheHitCounter().increment();
        } else {
            getCacheMissCounter().increment();
        }
    }

    @Override
    public void recordPrune(long durationMillis, long versionPruned, int nodesPruned, int valuesPruned) {
        getPruneTimer().record(durationMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
        getPruneNodesCounter().increment(nodesPruned);
        getPruneValuesCounter().increment(valuesPruned);
    }

    @Override
    public void recordStorageStats(long version, int rootHashSize, long nodeCount, long valueCount) {
        storageVersionGauge.set(version);
    }

    @Override
    public void recordCacheStats(long cacheHits, long cacheMisses, int cacheSize) {
        cacheSizeGauge.set(cacheSize);
    }

    @Override
    public void recordRocksDbStats(long pendingCompactionBytes, int runningCompactions,
                                    long memTableSize, int immutableMemTables) {
        rocksDbPendingCompactionGauge.set(pendingCompactionBytes);
        rocksDbRunningCompactionsGauge.set(runningCompactions);
        rocksDbMemTableSizeGauge.set(memTableSize);
        rocksDbImmutableMemTablesGauge.set(immutableMemTables);
    }

    // ========== Lazy Metric Initialization (Thread-Safe Double-Check Locking) ==========

    private Timer getCommitTimer() {
        if (commitTimer == null) {
            synchronized (this) {
                if (commitTimer == null) {
                    commitTimer = Timer.builder(prefix + ".commit.duration")
                            .description("JMT commit operation duration")
                            .publishPercentileHistogram()
                            .register(registry);
                }
            }
        }
        return commitTimer;
    }

    private DistributionSummary getCommitBatchSizeSummary() {
        if (commitBatchSizeSummary == null) {
            synchronized (this) {
                if (commitBatchSizeSummary == null) {
                    commitBatchSizeSummary = DistributionSummary.builder(prefix + ".commit.batch_size")
                            .description("JMT commit batch size (key-value pairs)")
                            .publishPercentileHistogram()
                            .register(registry);
                }
            }
        }
        return commitBatchSizeSummary;
    }

    private Counter getCommitNodesWrittenCounter() {
        if (commitNodesWrittenCounter == null) {
            synchronized (this) {
                if (commitNodesWrittenCounter == null) {
                    commitNodesWrittenCounter = Counter.builder(prefix + ".commit.nodes_written")
                            .description("Total tree nodes written during commits")
                            .register(registry);
                }
            }
        }
        return commitNodesWrittenCounter;
    }

    private Counter getCommitNodesStaleCounter() {
        if (commitNodesStaleCounter == null) {
            synchronized (this) {
                if (commitNodesStaleCounter == null) {
                    commitNodesStaleCounter = Counter.builder(prefix + ".commit.nodes_stale")
                            .description("Total nodes marked stale during commits")
                            .register(registry);
                }
            }
        }
        return commitNodesStaleCounter;
    }

    private Timer getProofTimer() {
        if (proofTimer == null) {
            synchronized (this) {
                if (proofTimer == null) {
                    proofTimer = Timer.builder(prefix + ".proof.duration")
                            .description("JMT proof generation duration")
                            .publishPercentileHistogram()
                            .register(registry);
                }
            }
        }
        return proofTimer;
    }

    private DistributionSummary getProofSizeSummary() {
        if (proofSizeSummary == null) {
            synchronized (this) {
                if (proofSizeSummary == null) {
                    proofSizeSummary = DistributionSummary.builder(prefix + ".proof.size")
                            .description("JMT proof size (number of sibling nodes)")
                            .register(registry);
                }
            }
        }
        return proofSizeSummary;
    }

    private Timer getReadTimer() {
        if (readTimer == null) {
            synchronized (this) {
                if (readTimer == null) {
                    readTimer = Timer.builder(prefix + ".read.duration")
                            .description("JMT read operation duration")
                            .publishPercentileHistogram()
                            .register(registry);
                }
            }
        }
        return readTimer;
    }

    private Counter getCacheHitCounter() {
        if (cacheHitCounter == null) {
            synchronized (this) {
                if (cacheHitCounter == null) {
                    cacheHitCounter = Counter.builder(prefix + ".cache.hits")
                            .description("JMT cache hits")
                            .register(registry);
                }
            }
        }
        return cacheHitCounter;
    }

    private Counter getCacheMissCounter() {
        if (cacheMissCounter == null) {
            synchronized (this) {
                if (cacheMissCounter == null) {
                    cacheMissCounter = Counter.builder(prefix + ".cache.misses")
                            .description("JMT cache misses")
                            .register(registry);
                }
            }
        }
        return cacheMissCounter;
    }

    private Timer getPruneTimer() {
        if (pruneTimer == null) {
            synchronized (this) {
                if (pruneTimer == null) {
                    pruneTimer = Timer.builder(prefix + ".prune.duration")
                            .description("JMT prune operation duration")
                            .register(registry);
                }
            }
        }
        return pruneTimer;
    }

    private Counter getPruneNodesCounter() {
        if (pruneNodesCounter == null) {
            synchronized (this) {
                if (pruneNodesCounter == null) {
                    pruneNodesCounter = Counter.builder(prefix + ".prune.nodes")
                            .description("Total nodes pruned from storage")
                            .register(registry);
                }
            }
        }
        return pruneNodesCounter;
    }

    private Counter getPruneValuesCounter() {
        if (pruneValuesCounter == null) {
            synchronized (this) {
                if (pruneValuesCounter == null) {
                    pruneValuesCounter = Counter.builder(prefix + ".prune.values")
                            .description("Total values pruned from storage")
                            .register(registry);
                }
            }
        }
        return pruneValuesCounter;
    }

    /**
     * Thread-safe AtomicLong wrapper for gauge values.
     * Extends java.util.concurrent.atomic.AtomicLong to expose as Number.
     */
    private static final class AtomicLong extends java.util.concurrent.atomic.AtomicLong {
        AtomicLong(long initialValue) {
            super(initialValue);
        }

        public double doubleValue() {
            return (double) get();
        }
    }
}
