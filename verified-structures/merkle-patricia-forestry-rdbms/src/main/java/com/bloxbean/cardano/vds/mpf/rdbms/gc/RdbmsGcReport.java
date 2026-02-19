package com.bloxbean.cardano.vds.mpf.rdbms.gc;

/**
 * Report from RDBMS mark-sweep garbage collection.
 *
 * @since 0.8.0
 */
public class RdbmsGcReport {

    /** Number of nodes marked as reachable. */
    public long marked;

    /** Total number of nodes in the store. */
    public long total;

    /** Number of orphaned nodes deleted. */
    public long deleted;

    /** Duration of the GC run in milliseconds. */
    public long durationMillis;

    @Override
    public String toString() {
        return "RdbmsGcReport{marked=" + marked + ", total=" + total +
               ", deleted=" + deleted + ", durationMillis=" + durationMillis + '}';
    }
}
