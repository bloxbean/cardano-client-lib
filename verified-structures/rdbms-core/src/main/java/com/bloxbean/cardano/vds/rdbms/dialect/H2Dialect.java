package com.bloxbean.cardano.vds.rdbms.dialect;

import com.bloxbean.cardano.vds.rdbms.common.KeyCodec;
import com.bloxbean.cardano.vds.rdbms.common.StandardKeyCodec;

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
    public String insertOrIgnoreSql(String tableName, String columns, String placeholders,
                                    String keyColumns) {
        // Callers supply keys explicitly; inferring them from a prefixed table name is ambiguous.
        return String.format(
            "MERGE INTO %s (%s) KEY(%s) VALUES (%s)",
            tableName, columns, keyColumns, placeholders
        );
    }

    @Override
    public String toString() {
        return "H2Dialect{}";
    }
}
