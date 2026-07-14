package com.bloxbean.cardano.client.txflow.store.rdbms;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Database-specific SQL boundary used by {@link RdbmsFlowExecutionStore}.
 *
 * <p>The common store owns transactions and domain validation. A dialect only
 * supplies syntax or error classification that genuinely differs between
 * relational databases. Implementing this interface does not by itself make a
 * database supported; an adapter must also pass the published store contract.</p>
 */
public interface TxFlowSqlDialect {
    /** @return stable human-readable dialect name */
    String name();

    /**
     * Tests whether this dialect recognizes a JDBC URL.
     *
     * @param jdbcUrl JDBC URL
     * @return whether the URL belongs to this dialect
     */
    boolean accepts(String jdbcUrl);

    /** @return classpath resource containing the current schema migration */
    String schemaResource();

    /**
     * Performs dialect-specific database/version validation.
     *
     * @param connection open connection
     * @throws SQLException when metadata cannot be read
     */
    default void validateDatabase(Connection connection) throws SQLException {
        // Certified dialects currently require no check beyond JDBC metadata.
    }

    /**
     * Acquires a transaction-scoped lock used to serialize schema migration.
     *
     * <p>The H2 embedded profile is already serialized in-process by the
     * schema manager. Shared database dialects should override this method.</p>
     *
     * @param connection migration transaction
     * @throws SQLException when the lock cannot be acquired
     */
    default void acquireMigrationLock(Connection connection) throws SQLException {
        // No database-level lock is needed for the certified single-JVM H2 profile.
    }

    /**
     * Reports whether one text column preserves deterministic identity comparison.
     *
     * <p>Portable TxFlow identifiers use exact Java {@link String#equals(Object)} semantics.
     * Dialects whose database can attach nondeterministic collations should override this hook
     * and inspect their trusted system catalog. Column, table, and schema names are metadata
     * values and must not be interpolated into SQL without validation.</p>
     *
     * @param connection schema-validation connection
     * @param schema effective schema name
     * @param table table name returned by JDBC metadata
     * @param column column name from the required schema
     * @return whether comparison is deterministic
     * @throws SQLException when the comparison metadata cannot be read
     */
    default boolean hasDeterministicTextComparison(Connection connection, String schema,
                                                   String table, String column)
            throws SQLException {
        return true;
    }

    /**
     * Returns the required fractional-second precision for durable instant columns.
     *
     * @return minimum decimal digits, or zero when the dialect makes no portable guarantee
     */
    default int minimumTimestampFractionalDigits() {
        return 0;
    }

    /**
     * Reports whether a required named index is complete and usable.
     *
     * <p>JDBC metadata exposes index names, columns, uniqueness, and sometimes predicates, but
     * does not portably expose database-specific validity state. Dialects should use a trusted
     * system catalog when an invalid or partial index can otherwise look usable.</p>
     *
     * @param connection schema-validation connection
     * @param schema effective schema name
     * @param table table name returned by JDBC metadata
     * @param index required index name in database identifier form
     * @return whether the index is complete and usable
     * @throws SQLException when index state cannot be read
     */
    default boolean hasUsableIndex(Connection connection, String schema, String table,
                                   String index) throws SQLException {
        return true;
    }

    /**
     * Returns SQL that locks selected rows until the current transaction ends.
     *
     * @param selectSql SELECT statement without a lock clause
     * @return dialect-specific locking statement
     */
    default String forUpdate(String selectSql) {
        return selectSql + " FOR UPDATE";
    }

    /**
     * Returns SQL for a bounded ordered event page.
     *
     * @param eventTable validated event table identifier
     * @return SQL with execution, cursor, and limit parameters in that order
     */
    default String eventPageSql(String eventTable) {
        return "SELECT sequence_no, event_type, event_time, step_id, transaction_hash, "
                + "details_format, details_version, details_payload FROM " + eventTable
                + " WHERE execution_id = ? AND sequence_no > ? ORDER BY sequence_no LIMIT ?";
    }

    /**
     * Classifies the standard unique-constraint SQL state used by certified dialects.
     *
     * @param failure database failure
     * @return whether it represents a uniqueness violation
     */
    default boolean isUniqueConstraintViolation(SQLException failure) {
        SQLException current = failure;
        while (current != null) {
            if ("23505".equals(current.getSQLState())) return true;
            current = current.getNextException();
        }
        return false;
    }
}
