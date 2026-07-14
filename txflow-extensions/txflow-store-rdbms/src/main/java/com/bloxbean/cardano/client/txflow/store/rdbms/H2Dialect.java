package com.bloxbean.cardano.client.txflow.store.rdbms;

import com.bloxbean.cardano.client.txflow.store.FlowStoreException;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

/** SQL dialect requiring H2 major version 2 or later; automated certification uses H2 2.x. */
public final class H2Dialect implements TxFlowSqlDialect {
    /** Shared stateless dialect instance. */
    public static final H2Dialect INSTANCE = new H2Dialect();

    private H2Dialect() {
    }

    @Override
    public String name() {
        return "H2";
    }

    @Override
    public boolean accepts(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.startsWith("jdbc:h2:");
    }

    @Override
    public String schemaResource() {
        return "/db/txflow/h2/V1__txflow_store.sql";
    }

    @Override
    public void validateDatabase(Connection connection) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String product = metadata.getDatabaseProductName();
        if (!"H2".equalsIgnoreCase(product)) {
            throw new FlowStoreException("TXFLOW_DIALECT_MISMATCH",
                    "Configured H2 dialect does not match the connected database");
        }
        if (metadata.getDatabaseMajorVersion() < 2) {
            throw new FlowStoreException("TXFLOW_DIALECT_VERSION_UNSUPPORTED",
                    "The TxFlow H2 dialect requires H2 major version 2 or later");
        }
    }

    @Override
    public int minimumTimestampFractionalDigits() {
        return 6;
    }
}
