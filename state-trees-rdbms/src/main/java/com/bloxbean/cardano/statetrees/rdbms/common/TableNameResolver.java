package com.bloxbean.cardano.statetrees.rdbms.common;

/**
 * Resolves table names from table prefix (mirrors RocksDB CF prefix).
 *
 * <p>Pattern: [tablePrefix]_[baseTableName]
 *
 * <p>Examples:
 * <ul>
 *   <li>Default: jmt_nodes, mpt_nodes</li>
 *   <li>Prefix "account": account_jmt_nodes, account_mpt_nodes</li>
 *   <li>Prefix "shard1": shard1_jmt_nodes, shard1_mpt_nodes</li>
 * </ul>
 *
 * @since 0.8.0
 */
public final class TableNameResolver {
    private final String tablePrefix;

    /**
     * Creates a resolver with the specified table prefix.
     *
     * @param tablePrefix the table prefix (null or empty for default tables)
     */
    public TableNameResolver(String tablePrefix) {
        this.tablePrefix = (tablePrefix == null || tablePrefix.isBlank()) ? "" : tablePrefix.trim();
    }

    /**
     * Resolves a base table name to its full name with prefix.
     *
     * @param baseTableName the base table name (e.g., "jmt_nodes")
     * @return the full table name with prefix applied
     */
    public String resolve(String baseTableName) {
        if (baseTableName == null || baseTableName.isBlank()) {
            throw new IllegalArgumentException("Base table name cannot be null or blank");
        }
        return tablePrefix.isEmpty()
            ? baseTableName
            : tablePrefix + "_" + baseTableName;
    }

    /**
     * Returns the table prefix.
     *
     * @return the table prefix (empty string for default)
     */
    public String tablePrefix() {
        return tablePrefix;
    }

    /**
     * Checks if this resolver uses the default prefix (empty).
     *
     * @return true if using default prefix, false otherwise
     */
    public boolean usesDefaultPrefix() {
        return tablePrefix.isEmpty();
    }

    @Override
    public String toString() {
        return String.format("TableNameResolver{tablePrefix='%s'}",
            tablePrefix.isEmpty() ? "<default>" : tablePrefix);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        TableNameResolver that = (TableNameResolver) obj;
        return tablePrefix.equals(that.tablePrefix);
    }

    @Override
    public int hashCode() {
        return tablePrefix.hashCode();
    }
}
