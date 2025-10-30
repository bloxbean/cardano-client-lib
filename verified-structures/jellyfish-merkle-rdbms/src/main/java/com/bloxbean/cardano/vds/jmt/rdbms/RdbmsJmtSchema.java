package com.bloxbean.cardano.vds.jmt.rdbms;

import com.bloxbean.cardano.vds.rdbms.common.TableNameResolver;

/**
 * Schema definition for JMT RDBMS storage with namespace support.
 *
 * <p>Maps table prefix (equivalent to RocksDB CF prefix) to actual table names.
 *
 * @since 0.8.0
 */
final class RdbmsJmtSchema {

    // Base table names (no prefix)
    private static final String CF_NODES = "jmt_nodes";
    private static final String CF_VALUES = "jmt_values";
    private static final String CF_ROOTS = "jmt_roots";
    private static final String CF_LATEST = "jmt_latest";
    private static final String CF_STALE = "jmt_stale";

    private final TableNameResolver resolver;

    /**
     * Creates a schema with the specified table prefix.
     *
     * @param tablePrefix the table prefix (null or empty for default tables)
     */
    RdbmsJmtSchema(String tablePrefix) {
        this.resolver = new TableNameResolver(tablePrefix);
    }

    /**
     * Returns the nodes table name.
     */
    String nodesTable() {
        return resolver.resolve(CF_NODES);
    }

    /**
     * Returns the values table name.
     */
    String valuesTable() {
        return resolver.resolve(CF_VALUES);
    }

    /**
     * Returns the roots table name.
     */
    String rootsTable() {
        return resolver.resolve(CF_ROOTS);
    }

    /**
     * Returns the latest metadata table name.
     */
    String latestTable() {
        return resolver.resolve(CF_LATEST);
    }

    /**
     * Returns the stale nodes table name.
     */
    String staleTable() {
        return resolver.resolve(CF_STALE);
    }
}
