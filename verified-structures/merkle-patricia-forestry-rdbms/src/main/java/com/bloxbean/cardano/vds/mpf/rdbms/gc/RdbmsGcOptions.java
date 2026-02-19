package com.bloxbean.cardano.vds.mpf.rdbms.gc;

import java.util.function.LongConsumer;

/**
 * Options for RDBMS mark-sweep garbage collection.
 *
 * @since 0.8.0
 */
public class RdbmsGcOptions {

    /** If true, count orphans but don't delete them. */
    public boolean dryRun = false;

    /** Number of nodes to delete per batch DELETE statement. */
    public int deleteBatchSize = 10_000;

    /** Progress callback, called with the number of nodes deleted so far. */
    public LongConsumer progress = null;
}
