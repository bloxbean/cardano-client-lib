package com.bloxbean.cardano.statetrees.jmt;

/**
 * Simple, zero-dependency metrics hook for JMT streaming operations.
 *
 * Applications can provide an implementation that forwards to a metrics system
 * (e.g., Micrometer) or structured logs. The default is a no-op implementation.
 */
public interface JmtMetrics {

    /** Records a node-cache hit or miss event. */
    void nodeCacheHit(boolean hit);

    /** Records a value-cache hit or miss event. */
    void valueCacheHit(boolean hit);

    /**
     * Records a commit event.
     *
     * @param millis  elapsed time in milliseconds
     * @param puts    number of put operations (nodes+values)
     * @param deletes number of delete operations (nodes+values)
     */
    void recordCommit(long millis, int puts, int deletes);

    /**
     * Records a prune event.
     *
     * @param millis       elapsed time in milliseconds
     * @param nodesPruned  number of nodes/values pruned
     * @param cacheEvicted number of cache entries evicted
     */
    void recordPrune(long millis, int nodesPruned, int cacheEvicted);

    /** No-op implementation. */
    JmtMetrics NOOP = new JmtMetrics() {
        @Override public void nodeCacheHit(boolean hit) { }
        @Override public void valueCacheHit(boolean hit) { }
        @Override public void recordCommit(long millis, int puts, int deletes) { }
        @Override public void recordPrune(long millis, int nodesPruned, int cacheEvicted) { }
    };
}

