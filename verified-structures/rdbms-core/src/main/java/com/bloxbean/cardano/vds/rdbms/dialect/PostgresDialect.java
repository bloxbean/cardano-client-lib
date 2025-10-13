package com.bloxbean.cardano.vds.rdbms.dialect;

import com.bloxbean.cardano.vds.rdbms.common.KeyCodec;
import com.bloxbean.cardano.vds.rdbms.common.StandardKeyCodec;

/**
 * PostgreSQL SQL dialect implementation.
 *
 * @since 0.8.0
 */
public class PostgresDialect implements SqlDialect {

    private final KeyCodec keyCodec = new StandardKeyCodec();

    @Override
    public String name() {
        return "PostgreSQL";
    }

    @Override
    public String upsertLatestSql(String tableName) {
        return "INSERT INTO " + tableName +
               " (namespace, latest_version, latest_root, updated_at) " +
               "VALUES (?, ?, ?, CURRENT_TIMESTAMP) " +
               "ON CONFLICT (namespace) DO UPDATE SET " +
               "latest_version = EXCLUDED.latest_version, " +
               "latest_root = EXCLUDED.latest_root, " +
               "updated_at = CURRENT_TIMESTAMP";
    }

    @Override
    public String binaryType() {
        return "BYTEA";
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
        return true;
    }

    @Override
    public String insertOrIgnoreSql(String tableName, String columns, String placeholders) {
        return String.format(
            "INSERT INTO %s (%s) VALUES (%s) ON CONFLICT DO NOTHING",
            tableName, columns, placeholders
        );
    }

    @Override
    public String toString() {
        return "PostgresDialect{}";
    }
}
