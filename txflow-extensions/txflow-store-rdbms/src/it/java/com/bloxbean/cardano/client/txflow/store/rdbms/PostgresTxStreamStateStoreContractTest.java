package com.bloxbean.cardano.client.txflow.store.rdbms;

import com.bloxbean.cardano.client.txflow.stream.TxStreamStateStore;
import com.bloxbean.cardano.client.txflow.stream.contract.TxStreamStateStoreContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the complete durable stream-store contract against a real PostgreSQL server. */
@Testcontainers
class PostgresTxStreamStateStoreContractTest extends TxStreamStateStoreContract {
    private static final AtomicInteger DATABASE_SEQUENCE = new AtomicInteger();

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17.6-alpine");

    @Override
    protected TxStreamStateStore createStore() throws Exception {
        return newStore(createDatabase(), SchemaManagement.MIGRATE);
    }

    @Test
    @Timeout(30)
    void applicationManagedDataSourceMigratesAndNeverClosesTheDataSource() throws Exception {
        String database = createDatabase();
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(jdbcUrl(database));
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());

        try (RdbmsTxStreamStateStore store = RdbmsTxStreamStateStore.builder()
                .dataSource(dataSource)
                .dialect(PostgresDialect.INSTANCE)
                .schemaManagement(SchemaManagement.MIGRATE)
                .build()) {
            store.registerItem(new com.bloxbean.cardano.client.txflow.stream.TxStreamItemRecord(
                    "item-ds", "key-ds", "lane", "fp", NOW));
        }
        try (Connection connection = dataSource.getConnection()) {
            assertFalse(connection.isClosed(),
                    "closing the store must not close an application DataSource");
        }
    }

    @Test
    @Timeout(30)
    void schemaValidateRejectsAnUninitializedDatabase() throws Exception {
        String database = createDatabase();
        assertTrue(assertThrowsFlowStore(() -> newStore(database, SchemaManagement.VALIDATE))
                .getCode().equals("TXFLOW_SCHEMA_MISSING"));
    }

    private com.bloxbean.cardano.client.txflow.store.FlowStoreException assertThrowsFlowStore(
            Runnable operation) {
        try {
            operation.run();
        } catch (com.bloxbean.cardano.client.txflow.store.FlowStoreException failure) {
            return failure;
        }
        throw new AssertionError("Expected a FlowStoreException");
    }

    private RdbmsTxStreamStateStore newStore(String database, SchemaManagement management) {
        return RdbmsTxStreamStateStore.builder()
                .jdbcUrl(jdbcUrl(database))
                .username(POSTGRES.getUsername())
                .password(POSTGRES.getPassword())
                .dialect(PostgresDialect.INSTANCE)
                .schemaManagement(management)
                .build();
    }

    private String createDatabase() throws SQLException {
        String database = "txstream_" + DATABASE_SEQUENCE.incrementAndGet();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            statement.execute("CREATE DATABASE " + database);
        }
        return database;
    }

    private String jdbcUrl(String database) {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ':'
                + POSTGRES.getMappedPort(5432) + '/' + database;
    }
}
