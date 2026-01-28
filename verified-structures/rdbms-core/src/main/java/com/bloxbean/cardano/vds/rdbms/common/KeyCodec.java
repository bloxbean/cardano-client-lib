package com.bloxbean.cardano.vds.rdbms.common;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Handles binary key encoding/decoding for different SQL dialects.
 *
 * <p>Different databases use different binary types:
 * <ul>
 *   <li>PostgreSQL: BYTEA type</li>
 *   <li>H2: BINARY/VARBINARY types</li>
 *   <li>SQLite: BLOB type</li>
 * </ul>
 *
 * <p>This interface provides a uniform API for binary data handling.
 *
 * @since 0.8.0
 */
public interface KeyCodec {

    /**
     * Sets a binary key parameter in a prepared statement.
     *
     * @param stmt the prepared statement
     * @param paramIndex the parameter index (1-based)
     * @param key the binary key data
     * @throws SQLException if a database error occurs
     */
    void setKey(PreparedStatement stmt, int paramIndex, byte[] key) throws SQLException;

    /**
     * Gets a binary key from a result set.
     *
     * @param rs the result set
     * @param columnName the column name
     * @return the binary key data, or null if the column is NULL
     * @throws SQLException if a database error occurs
     */
    byte[] getKey(ResultSet rs, String columnName) throws SQLException;

    /**
     * Validates that a key has the expected length.
     *
     * @param key the key to validate
     * @param expectedLength the expected length in bytes
     * @throws IllegalArgumentException if the key length is invalid
     */
    default void validateKeyLength(byte[] key, int expectedLength) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        if (key.length != expectedLength) {
            throw new IllegalArgumentException(
                String.format("Invalid key length: expected %d bytes, got %d bytes",
                    expectedLength, key.length)
            );
        }
    }
}
