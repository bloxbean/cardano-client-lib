package com.bloxbean.cardano.vds.rdbms.common;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Standard JDBC implementation of {@link KeyCodec}.
 *
 * <p>Uses standard JDBC methods ({@code setBytes}/{@code getBytes}) which work
 * correctly for PostgreSQL, H2, and SQLite.
 *
 * @since 0.8.0
 */
public class StandardKeyCodec implements KeyCodec {

    @Override
    public void setKey(PreparedStatement stmt, int paramIndex, byte[] key) throws SQLException {
        stmt.setBytes(paramIndex, key);
    }

    @Override
    public byte[] getKey(ResultSet rs, String columnName) throws SQLException {
        return rs.getBytes(columnName);
    }

    @Override
    public String toString() {
        return "StandardKeyCodec{}";
    }
}
