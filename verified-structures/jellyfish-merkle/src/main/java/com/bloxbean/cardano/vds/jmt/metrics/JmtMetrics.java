package com.bloxbean.cardano.vds.jmt.metrics;

/**
 * Metrics collection interface for Jellyfish Merkle Tree operations.
 *
 * <p>This interface is designed to be zero-dependency and easily integrated with
 * metrics systems like Micrometer (Prometheus), Dropwizard Metrics, or custom solutions.
 *
 * <p><b>Usage:</b>
 * <pre>
 * // With Micrometer
 * JmtMetrics metrics = new MicrometerJmtMetrics(meterRegistry);
 * JellyfishMerkleTree.Options options = JellyfishMerkleTree.Options.builder()
 *     .metrics(metrics)
 *     .build();
 *
 * // Without metrics (default)
 * JellyfishMerkleTree.Options options = JellyfishMerkleTree.Options.defaults(); // metrics = NOOP
 * </pre>
 *
 * <p><b>Thread Safety:</b> Implementations must be thread-safe for concurrent metric recording.
 */
public interface JmtMetrics {

    /**
     * Records a commit operation.
     *
     * @param durationMillis duration of the commit in milliseconds
     * @param version        version number committed
     * @param batchSize      number of key-value pairs in the batch
     * @param nodesWritten   number of tree nodes written to storage
     * @param nodesStale     number of nodes marked as stale
     */
    void recordCommit(long durationMillis, long version, int batchSize, int nodesWritten, int nodesStale);

    /**
     * Records a proof generation operation.
     *
     * @param durationMillis duration of proof generation in milliseconds
     * @param proofSize      size of the generated proof (number of nodes)
     * @param found          whether the key was found (inclusion proof vs non-inclusion)
     */
    void recordProofGeneration(long durationMillis, int proofSize, boolean found);

    /**
     * Records a tree read operation (getValue or getValueAt).
     *
     * <p><b>Note:</b> Currently not implemented in JellyfishMerkleTree.
     * The current implementation focuses on batch commits and proof generation.
     * Individual reads are not exposed in the public API.
     *
     * @param durationMillis duration of the read in milliseconds
     * @param cacheHit       whether the value was found in cache
     * @param found          whether the key exists in the tree
     */
    void recordRead(long durationMillis, boolean cacheHit, boolean found);

    /**
     * Records a prune operation.
     *
     * <p><b>Note:</b> Currently not implemented in JellyfishMerkleTree.
     * Pruning will be added in a future version (see ADR-0013 production readiness).
     *
     * @param durationMillis   duration of pruning in milliseconds
     * @param versionPruned    version number pruned up to
     * @param nodesPruned      number of nodes removed
     * @param valuesPruned     number of values removed
     */
    void recordPrune(long durationMillis, long versionPruned, int nodesPruned, int valuesPruned);

    /**
     * Records storage statistics snapshot.
     *
     * <p><b>Implementation Status:</b> Partially supported. Version and root hash size
     * are available, but nodeCount and valueCount estimates require storage-level support
     * (available in RocksDbJmtStore but not in JmtStore interface).
     *
     * @param version       current tree version
     * @param rootHashSize  size of root hash in bytes
     * @param nodeCount     estimated number of nodes in storage (0 if unavailable)
     * @param valueCount    estimated number of values in storage (0 if unavailable)
     */
    void recordStorageStats(long version, int rootHashSize, long nodeCount, long valueCount);

    /**
     * Records TreeCache statistics.
     *
     * <p><b>Implementation Status:</b> Partially supported. Cache size is available
     * from TreeCache.nodeCache, but hit/miss tracking is NOT currently implemented
     * in TreeCache. To enable full cache metrics, TreeCache.getNode() would need
     * to add counters for cache hits (found in nodeCache/frozenCache) vs misses
     * (fallback to storage).
     *
     * @param cacheHits   number of cache hits (currently always 0)
     * @param cacheMisses number of cache misses (currently always 0)
     * @param cacheSize   current cache size (number of entries)
     */
    void recordCacheStats(long cacheHits, long cacheMisses, int cacheSize);

    /**
     * Records RocksDB-specific statistics (if using RocksDbJmtStore).
     *
     * @param pendingCompactionBytes estimated pending compaction bytes
     * @param runningCompactions     number of running compactions
     * @param memTableSize           active memtable size in bytes
     * @param immutableMemTables     number of immutable memtables
     */
    void recordRocksDbStats(long pendingCompactionBytes, int runningCompactions,
                            long memTableSize, int immutableMemTables);

    /**
     * No-op implementation with zero overhead.
     * This is the default when metrics are not enabled.
     */
    JmtMetrics NOOP = new JmtMetrics() {
        @Override
        public void recordCommit(long durationMillis, long version, int batchSize, int nodesWritten, int nodesStale) {
        }

        @Override
        public void recordProofGeneration(long durationMillis, int proofSize, boolean found) {
        }

        @Override
        public void recordRead(long durationMillis, boolean cacheHit, boolean found) {
        }

        @Override
        public void recordPrune(long durationMillis, long versionPruned, int nodesPruned, int valuesPruned) {
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
    };
}
