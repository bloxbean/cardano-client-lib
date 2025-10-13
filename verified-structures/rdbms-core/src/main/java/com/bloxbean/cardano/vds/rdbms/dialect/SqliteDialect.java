package com.bloxbean.cardano.vds.rdbms.dialect;

import com.bloxbean.cardano.vds.rdbms.common.KeyCodec;
import com.bloxbean.cardano.vds.rdbms.common.StandardKeyCodec;

/**
 * SQLite SQL dialect implementation.
 *
 * @since 0.8.0
 */
public class SqliteDialect implements SqlDialect {

    private final KeyCodec keyCodec = new StandardKeyCodec();

    @Override
    public String name() {
        return "SQLite";
    }

    @Override
    public String upsertLatestSql(String tableName) {
        return "INSERT OR REPLACE INTO " + tableName +
               " (namespace, latest_version, latest_root, updated_at) " +
               "VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
    }

    @Override
    public String binaryType() {
        return "BLOB";
    }

    @Override
    public String bigintType() {
        return "INTEGER";  // SQLite uses INTEGER for 64-bit integers
    }

    @Override
    public String smallintType() {
        return "INTEGER";  // SQLite uses INTEGER for all integer types
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
        return true;  // SQLite 3.35.0+ supports RETURNING
    }

    @Override
    public String insertOrIgnoreSql(String tableName, String columns, String placeholders) {
        return String.format(
            "INSERT OR IGNORE INTO %s (%s) VALUES (%s)",
            tableName, columns, placeholders
        );
    }

    @Override
    public String toString() {
        return "SqliteDialect{}";
    }
}
