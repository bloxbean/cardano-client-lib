package com.bloxbean.cardano.statetrees.rdbms.mpt;

import com.bloxbean.cardano.statetrees.rdbms.common.TableNameResolver;

/**
 * Schema definition for MPT RDBMS storage with namespace support.
 *
 * <p>Maps table prefix (equivalent to RocksDB CF prefix) to actual table names.
 *
 * @since 0.8.0
 */
final class RdbmsMptSchema {

    // Base table names (no prefix)
    private static final String CF_NODES = "mpt_nodes";
    private static final String CF_ROOTS = "mpt_roots";
    private static final String CF_LATEST = "mpt_latest";

    private final TableNameResolver resolver;

    /**
     * Creates a schema with the specified table prefix.
     *
     * @param tablePrefix the table prefix (null or empty for default tables)
     */
    RdbmsMptSchema(String tablePrefix) {
        this.resolver = new TableNameResolver(tablePrefix);
    }

    /**
     * Returns the nodes table name.
     */
    String nodesTable() {
        return resolver.resolve(CF_NODES);
    }

    /**
     * Returns the roots table name (optional - for versioning).
     */
    String rootsTable() {
        return resolver.resolve(CF_ROOTS);
    }

    /**
     * Returns the latest metadata table name (optional).
     */
    String latestTable() {
        return resolver.resolve(CF_LATEST);
    }
}
