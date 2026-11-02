package com.bloxbean.cardano.vds.mpf.rocksdb.gc;

/** Outcome of a GC run. */
public class GcReport {
    /** Nodes marked as reachable from the retained roots (both strategies). */
    public long marked;
    /** Nodes examined: node keys scanned by mark-sweep, nodes decremented by refcount GC. */
    public long total;
    /** Nodes deleted. */
    public long deleted;
    /**
     * Refcount GC only: nodes whose count reached zero but that are still reachable from a retained root, so they
     * were kept. Non-zero values indicate refcounts written by older code (or another inconsistency); the kept
     * nodes are a leak that {@code OnDiskMarkSweepStrategy} reclaims.
     */
    public long protectedNodes;
    /** Wall-clock duration of the run. */
    public long durationMillis;
}
