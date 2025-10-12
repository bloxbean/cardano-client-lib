package com.bloxbean.cardano.statetrees.rdbms.dialect;

import com.bloxbean.cardano.statetrees.rdbms.common.KeyCodec;

/**
 * Abstraction for database-specific SQL syntax.
 *
 * <p>Different databases have different SQL dialects. This interface encapsulates
 * those differences to allow database-neutral code.
 *
 * @since 0.8.0
 */
public interface SqlDialect {

    /**
     * Returns the dialect name (e.g., "PostgreSQL", "H2", "SQLite").
     *
     * @return the dialect name
     */
    String name();

    /**
     * Returns an UPSERT statement for updating latest root/version metadata.
     *
     * <p>The statement should have 3 parameters:
     * <ol>
     *   <li>namespace (SMALLINT)</li>
     *   <li>latest_version (BIGINT)</li>
     *   <li>latest_root (BYTEA/BINARY/BLOB)</li>
     * </ol>
     *
     * <p>Examples:
     * <ul>
     *   <li>PostgreSQL: {@code INSERT ... ON CONFLICT ... DO UPDATE}</li>
     *   <li>H2: {@code MERGE INTO}</li>
     *   <li>SQLite: {@code INSERT OR REPLACE}</li>
     * </ul>
     *
     * @param tableName the table name
     * @return the UPSERT SQL statement
     */
    String upsertLatestSql(String tableName);

    /**
     * Returns the binary column type for this database.
     *
     * @return "BYTEA" for PostgreSQL, "VARBINARY" for H2, "BLOB" for SQLite
     */
    String binaryType();

    /**
     * Returns the bigint column type for this database.
     *
     * @return "BIGINT" for most databases
     */
    String bigintType();

    /**
     * Returns the smallint column type for this database.
     *
     * @return "SMALLINT" for most databases
     */
    String smallintType();

    /**
     * Returns the current timestamp function for this database.
     *
     * @return "CURRENT_TIMESTAMP" for most databases
     */
    String currentTimestamp();

    /**
     * Returns the key codec for this dialect.
     *
     * @return the key codec implementation
     */
    KeyCodec keyCodec();

    /**
     * Returns whether this dialect supports the RETURNING clause.
     *
     * @return true if RETURNING is supported, false otherwise
     */
    boolean supportsReturning();

    /**
     * Returns an INSERT OR IGNORE statement for idempotent inserts.
     *
     * <p>The statement format varies by database:
     * <ul>
     *   <li>PostgreSQL: {@code INSERT ... ON CONFLICT DO NOTHING}</li>
     *   <li>H2/SQLite: {@code INSERT OR IGNORE}</li>
     * </ul>
     *
     * @param tableName the table name
     * @param columns comma-separated column names
     * @param placeholders comma-separated value placeholders (e.g., "?, ?, ?")
     * @return the INSERT OR IGNORE SQL statement
     */
    String insertOrIgnoreSql(String tableName, String columns, String placeholders);
}
