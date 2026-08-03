package com.bloxbean.cardano.client.txflow.store.rdbms;

import com.bloxbean.cardano.client.txflow.store.FlowStoreException;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Schema version matrix for the stream store (fresh migrate, validate/checksum, newer rejection). */
class RdbmsStreamSchemaManagerTest {

    @Test
    void freshMigrateInstallsSchemaAndRecordsHistory() throws Exception {
        JdbcDataSource dataSource = dataSource();
        migrate(dataSource).close();

        assertEquals(1, rowCount(dataSource, "txstream_schema_history"));
        // A second MIGRATE against the current schema is a validate-only no-op.
        migrate(dataSource).close();
        validate(dataSource).close();
    }

    @Test
    void validateOnAnEmptyDatabaseFailsClosed() {
        JdbcDataSource dataSource = dataSource();
        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> validate(dataSource));
        assertEquals("TXFLOW_SCHEMA_MISSING", failure.getCode());
    }

    @Test
    void validationRejectsAChangedMigrationChecksum() throws Exception {
        JdbcDataSource dataSource = dataSource();
        migrate(dataSource).close();
        execute(dataSource, "UPDATE txstream_schema_history SET checksum = 'changed'");

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> validate(dataSource));
        assertEquals("TXFLOW_SCHEMA_CHECKSUM_MISMATCH", failure.getCode());
    }

    @Test
    void validationRejectsASchemaNewerThanTheLibrary() throws Exception {
        JdbcDataSource dataSource = dataSource();
        migrate(dataSource).close();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO txstream_schema_history "
                             + "(version_no, description, checksum, installed_at) "
                             + "VALUES (?, ?, ?, ?)")) {
            statement.setInt(1, RdbmsStreamSchemaManager.CURRENT_VERSION + 1);
            statement.setString(2, "future migration");
            statement.setString(3, "future-checksum");
            statement.setTimestamp(4, Timestamp.from(Instant.parse("2026-07-14T00:00:00Z")));
            statement.executeUpdate();
        }

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> validate(dataSource));
        assertEquals("TXFLOW_SCHEMA_NEWER", failure.getCode());
    }

    @Test
    void validationRejectsAMissingRequiredColumn() throws Exception {
        JdbcDataSource dataSource = dataSource();
        migrate(dataSource).close();
        execute(dataSource, "ALTER TABLE txstream_item DROP COLUMN projection_sequence");

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> validate(dataSource));
        assertEquals("TXFLOW_SCHEMA_INCOMPATIBLE", failure.getCode());
    }

    @Test
    void interruptedH2MigrationWithEmptyPrefixIsRecovered() throws Exception {
        JdbcDataSource dataSource = dataSource();
        // Simulate an interrupted first migration: history marker plus an empty prefix table.
        execute(dataSource, """
                CREATE TABLE txstream_schema_history (
                    version_no INTEGER PRIMARY KEY,
                    description VARCHAR(255) NOT NULL,
                    checksum VARCHAR(64) NOT NULL,
                    installed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
                )
                """);
        execute(dataSource, """
                CREATE TABLE txstream_item (
                    item_id VARCHAR(512) PRIMARY KEY,
                    stream_id VARCHAR(255)
                )
                """);

        migrate(dataSource).close();

        assertEquals(1, rowCount(dataSource, "txstream_schema_history"));
        validate(dataSource).close();
    }

    @Test
    void migrationRejectsPreexistingStreamTablesWithoutHistory() throws Exception {
        JdbcDataSource dataSource = dataSource();
        execute(dataSource, "CREATE TABLE txstream_item (item_id VARCHAR(512) PRIMARY KEY)");

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> migrate(dataSource));
        assertEquals("TXFLOW_SCHEMA_INCOMPATIBLE", failure.getCode());
        try (Connection connection = dataSource.getConnection()) {
            assertFalse(tableExists(connection, "TXSTREAM_SCHEMA_HISTORY"));
        }
    }

    private RdbmsTxStreamStateStore migrate(JdbcDataSource dataSource) {
        return RdbmsTxStreamStateStore.builder()
                .dataSource(dataSource)
                .schemaManagement(SchemaManagement.MIGRATE)
                .build();
    }

    private RdbmsTxStreamStateStore validate(JdbcDataSource dataSource) {
        return RdbmsTxStreamStateStore.builder()
                .dataSource(dataSource)
                .schemaManagement(SchemaManagement.VALIDATE)
                .build();
    }

    private JdbcDataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:stream-schema-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        return dataSource;
    }

    private void execute(JdbcDataSource dataSource, String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int rowCount(JdbcDataSource dataSource, String table) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        try (ResultSet tables = connection.getMetaData().getTables(
                connection.getCatalog(), connection.getSchema(), table,
                new String[]{"TABLE"})) {
            return tables.next();
        }
    }
}
