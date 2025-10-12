package com.bloxbean.cardano.statetrees.rdbms.dialect;

import com.bloxbean.cardano.statetrees.rdbms.common.KeyCodec;
import com.bloxbean.cardano.statetrees.rdbms.common.StandardKeyCodec;

/**
 * H2 Database SQL dialect implementation.
 *
 * @since 0.8.0
 */
public class H2Dialect implements SqlDialect {

    private final KeyCodec keyCodec = new StandardKeyCodec();

    @Override
    public String name() {
        return "H2";
    }

    @Override
    public String upsertLatestSql(String tableName) {
        return "MERGE INTO " + tableName +
               " (namespace, latest_version, latest_root, updated_at) " +
               "KEY (namespace) VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
    }

    @Override
    public String binaryType() {
        return "VARBINARY";
    }

    @Override
    public String bigintType() {
        return "BIGINT";
    }

    @Override
    public String smallintType() {
        return "SMALLINT";
    }

    @Override
    public String currentTimestamp() {
        return "CURRENT_TIMESTAMP";
    }

    @Override
    public KeyCodec keyCodec() {
        return keyCodec;
    }

    @Override
    public boolean supportsReturning() {
        return false;
    }

    @Override
    public String insertOrIgnoreSql(String tableName, String columns, String placeholders) {
        // For H2, MERGE INTO uses the PRIMARY KEY to determine insert vs update
        // This provides "last write wins" semantics matching RocksDB batch.put() and Map.put()
        // We specify KEY to explicitly indicate which columns form the unique key
        String[] cols = columns.split(",\\s*");
        String[] keyColumns;

        // Determine key columns based on table name
        if (tableName.contains("jmt_nodes")) {
            // JMT nodes: PRIMARY KEY (namespace, node_path, version)
            keyColumns = new String[]{"namespace", "node_path", "version"};
        } else if (tableName.contains("mpt_nodes")) {
            // MPT nodes: PRIMARY KEY (namespace, node_hash) - no versioning
            keyColumns = new String[]{"namespace", "node_hash"};
        } else if (tableName.contains("values")) {
            // jmt_values: PRIMARY KEY (namespace, key_hash, version)
            keyColumns = new String[]{"namespace", "key_hash", "version"};
        } else {
            // Default: use all columns as key (will insert or update all)
            keyColumns = cols;
        }

        String keyClause = String.join(", ", keyColumns);
        return String.format(
            "MERGE INTO %s (%s) KEY(%s) VALUES (%s)",
            tableName, columns, keyClause, placeholders
        );
    }

    @Override
    public String toString() {
        return "H2Dialect{}";
    }
}
