package com.bloxbean.cardano.client.txflow.store.rdbms;

import com.bloxbean.cardano.client.txflow.store.FlowStoreException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/**
 * SQL dialect for PostgreSQL.
 *
 * <p>The current automated certification target is PostgreSQL 17.x. See the module README for
 * the distinction between that tested profile and application-qualified PostgreSQL versions.</p>
 */
public final class PostgresDialect implements TxFlowSqlDialect {
    /** Shared stateless dialect instance. */
    public static final PostgresDialect INSTANCE = new PostgresDialect();

    private PostgresDialect() {
    }

    @Override
    public String name() {
        return "PostgreSQL";
    }

    @Override
    public boolean accepts(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.startsWith("jdbc:postgresql:");
    }

    @Override
    public String schemaResource() {
        return "/db/txflow/postgresql/V1__txflow_store.sql";
    }

    @Override
    public void validateDatabase(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        if (product == null || !product.toLowerCase(Locale.ROOT).contains("postgresql")) {
            throw new FlowStoreException("TXFLOW_DIALECT_MISMATCH",
                    "Configured PostgreSQL dialect does not match the connected database");
        }
        try (Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("SHOW server_encoding")) {
            if (!row.next() || !"UTF8".equalsIgnoreCase(row.getString(1))) {
                throw new FlowStoreException("TXFLOW_DIALECT_ENCODING_UNSUPPORTED",
                        "The TxFlow PostgreSQL dialect requires UTF-8 server encoding");
            }
        }
    }

    @Override
    public void acquireMigrationLock(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SELECT pg_advisory_xact_lock(1415072847)");
        }
    }

    @Override
    public boolean hasDeterministicTextComparison(Connection connection, String schema,
                                                  String table, String column)
            throws SQLException {
        String sql = "SELECT c.collisdeterministic "
                + "FROM pg_catalog.pg_attribute a "
                + "JOIN pg_catalog.pg_class r ON r.oid = a.attrelid "
                + "JOIN pg_catalog.pg_namespace n ON n.oid = r.relnamespace "
                + "JOIN pg_catalog.pg_collation c ON c.oid = a.attcollation "
                + "WHERE n.nspname = ? AND r.relname = ? "
                + "AND a.attname = ? AND a.attnum > 0 AND NOT a.attisdropped";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            statement.setString(3, column);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() && row.getBoolean(1) && !row.wasNull();
            }
        }
    }

    @Override
    public int minimumTimestampFractionalDigits() {
        return 6;
    }

    @Override
    public boolean hasUsableIndex(Connection connection, String schema, String table,
                                  String index) throws SQLException {
        String sql = "SELECT i.indisvalid AND i.indisready AND i.indislive "
                + "AND i.indpred IS NULL "
                + "FROM pg_catalog.pg_index i "
                + "JOIN pg_catalog.pg_class idx ON idx.oid = i.indexrelid "
                + "JOIN pg_catalog.pg_class tbl ON tbl.oid = i.indrelid "
                + "JOIN pg_catalog.pg_namespace n ON n.oid = tbl.relnamespace "
                + "WHERE n.nspname = ? AND tbl.relname = ? AND idx.relname = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            statement.setString(3, index);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() && row.getBoolean(1) && !row.wasNull();
            }
        }
    }
}
