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

class RdbmsSchemaManagerTest {

    @Test
    void migrationRecoversCompatibleEmptyHistoryTableLeftByInterruptedH2Ddl() throws Exception {
        JdbcDataSource dataSource = dataSource();
        createHistoryTable(dataSource);
        createExecutionTable(dataSource);
        execute(dataSource, "CREATE INDEX txflow_partial_extra_index "
                + "ON txflow_execution (revision_no)");
        execute(dataSource, "ALTER TABLE txflow_execution "
                + "ADD CONSTRAINT txflow_partial_extra_check CHECK (revision_no >= 0)");

        migrate(dataSource).close();

        assertEquals(1, rowCount(dataSource, "txflow_schema_history"));
        assertEquals(1, rowCount(dataSource, "txflow_lease_epoch"));
        try (Connection connection = dataSource.getConnection()) {
            assertFalse(indexExists(connection, "TXFLOW_EXECUTION",
                    "TXFLOW_PARTIAL_EXTRA_INDEX"));
            assertFalse(constraintExists(connection, "TXFLOW_PARTIAL_EXTRA_CHECK"));
        }
        validate(dataSource).close();
    }

    @Test
    void migrationDoesNotAdoptNonEmptyPartialH2Schema() throws Exception {
        JdbcDataSource dataSource = dataSource();
        createHistoryTable(dataSource);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO txflow_schema_history "
                             + "(version_no, description, checksum, installed_at) "
                             + "VALUES (?, ?, ?, ?)")) {
            statement.setInt(1, 0);
            statement.setString(2, "unknown migration");
            statement.setString(3, "unknown-checksum");
            statement.setTimestamp(4, Timestamp.from(
                    Instant.parse("2026-07-14T00:00:00Z")));
            statement.executeUpdate();
        }

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> migrate(dataSource));

        assertEquals("TXFLOW_SCHEMA_INCOMPATIBLE", failure.getCode());
        try (Connection connection = dataSource.getConnection()) {
            assertFalse(tableExists(connection, "TXFLOW_EXECUTION"));
        }
    }

    @Test
    void migrationRejectsCompatibleEmptyTablesWithoutHistoryMarker() throws Exception {
        JdbcDataSource dataSource = dataSource();
        createExecutionTable(dataSource);

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> migrate(dataSource));

        assertEquals("TXFLOW_SCHEMA_INCOMPATIBLE", failure.getCode());
        try (Connection connection = dataSource.getConnection()) {
            assertFalse(tableExists(connection, "TXFLOW_SCHEMA_HISTORY"));
            assertEquals(0, rowCount(dataSource, "txflow_execution"));
        }
    }

    @Test
    void migrationRejectsNonPrefixPartialH2TableSet() throws Exception {
        JdbcDataSource dataSource = dataSource();
        createHistoryTable(dataSource);
        execute(dataSource, "CREATE TABLE txflow_idempotency "
                + "(namespace_id VARCHAR(255), claim_key VARCHAR(512))");

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> migrate(dataSource));

        assertEquals("TXFLOW_SCHEMA_INCOMPATIBLE", failure.getCode());
        try (Connection connection = dataSource.getConnection()) {
            assertFalse(tableExists(connection, "TXFLOW_EXECUTION"));
        }
    }

    @Test
    void migrationRejectsPreexistingTxFlowObjectsWithoutHistory() throws Exception {
        JdbcDataSource dataSource = dataSource();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE txflow_idempotency "
                    + "(namespace_id VARCHAR(255), claim_key VARCHAR(512))");
        }

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> migrate(dataSource));

        assertEquals("TXFLOW_SCHEMA_INCOMPATIBLE", failure.getCode());
        try (Connection connection = dataSource.getConnection()) {
            assertFalse(tableExists(connection, "TXFLOW_SCHEMA_HISTORY"));
        }
    }

    @Test
    void validationIsScopedToTheConnectionEffectiveSchema() throws Exception {
        JdbcDataSource publicDataSource = dataSource();
        try (Connection connection = publicDataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA OTHER");
        }
        JdbcDataSource otherDataSource = new JdbcDataSource();
        otherDataSource.setURL(publicDataSource.getURL() + ";SCHEMA=OTHER");
        migrate(otherDataSource).close();

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> RdbmsFlowExecutionStore.builder()
                        .dataSource(publicDataSource)
                        .schemaManagement(SchemaManagement.VALIDATE)
                        .build());

        assertEquals("TXFLOW_SCHEMA_MISSING", failure.getCode());
    }

    @Test
    void validationRejectsMissingRequiredColumn() throws Exception {
        JdbcDataSource dataSource = dataSource();
        migrate(dataSource).close();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE txflow_event DROP COLUMN event_type");
        }

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> validate(dataSource));

        assertEquals("TXFLOW_SCHEMA_INCOMPATIBLE", failure.getCode());
    }

    @Test
    void validationDoesNotReadPatternMatchingDecoySchema() throws Exception {
        JdbcDataSource admin = dataSource();
        execute(admin, "CREATE SCHEMA APP_A");
        execute(admin, "CREATE SCHEMA APPXA");
        migrate(scopedDataSource(admin, "APPXA")).close();

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> validate(scopedDataSource(admin, "APP_A")));

        assertEquals("TXFLOW_SCHEMA_MISSING", failure.getCode());
    }

    @Test
    void validationDoesNotReadPatternMatchingDecoyTable() throws Exception {
        JdbcDataSource dataSource = dataSource();
        migrate(dataSource).close();
        execute(dataSource, "CREATE TABLE txflowXevent AS SELECT * FROM txflow_event");
        execute(dataSource, "ALTER TABLE txflow_event DROP COLUMN event_type");

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> validate(dataSource));

        assertEquals("TXFLOW_SCHEMA_INCOMPATIBLE", failure.getCode());
    }

    @Test
    void validationRejectsQuotedWrongCaseRequiredColumn() throws Exception {
        JdbcDataSource dataSource = dataSource();
        migrate(dataSource).close();
        execute(dataSource, "ALTER TABLE txflow_event ALTER COLUMN event_type "
                + "RENAME TO \"Event_Type\"");

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> validate(dataSource));

        assertEquals("TXFLOW_SCHEMA_INCOMPATIBLE", failure.getCode());
    }

    @Test
    void validationRejectsMissingCriticalPrimaryKey() throws Exception {
        JdbcDataSource dataSource = dataSource();
        migrate(dataSource).close();
        try (Connection connection = dataSource.getConnection()) {
            String primaryKey = primaryKeyName(connection, "TXFLOW_IDEMPOTENCY");
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE txflow_idempotency DROP CONSTRAINT "
                        + primaryKey);
            }
        }

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> validate(dataSource));

        assertEquals("TXFLOW_SCHEMA_INCOMPATIBLE", failure.getCode());
    }

    @Test
    void validationRejectsForeignKeyIntoAnotherSchema() throws Exception {
        JdbcDataSource dataSource = dataSource();
        migrate(dataSource).close();
        execute(dataSource, "CREATE SCHEMA OTHER");
        execute(dataSource, "CREATE TABLE OTHER.txflow_execution "
                + "(execution_id VARCHAR(512) PRIMARY KEY)");
        execute(dataSource, "ALTER TABLE txflow_event "
                + "DROP CONSTRAINT fk_txflow_event_execution");
        execute(dataSource, "ALTER TABLE txflow_event "
                + "ADD CONSTRAINT fk_txflow_event_execution FOREIGN KEY (execution_id) "
                + "REFERENCES OTHER.txflow_execution (execution_id)");

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> validate(dataSource));

        assertEquals("TXFLOW_SCHEMA_INCOMPATIBLE", failure.getCode());
    }

    @Test
    void validationRejectsCompositeForeignKeyMasqueradingAsRequiredKey() throws Exception {
        JdbcDataSource dataSource = dataSource();
        migrate(dataSource).close();
        execute(dataSource, "ALTER TABLE txflow_execution ADD CONSTRAINT "
                + "uq_txflow_execution_composite UNIQUE "
                + "(execution_id, definition_fingerprint)");
        execute(dataSource, "ALTER TABLE txflow_event "
                + "ADD COLUMN definition_fingerprint VARCHAR(512)");
        execute(dataSource, "ALTER TABLE txflow_event "
                + "DROP CONSTRAINT fk_txflow_event_execution");
        execute(dataSource, "ALTER TABLE txflow_event "
                + "ADD CONSTRAINT fk_txflow_event_execution FOREIGN KEY "
                + "(execution_id, definition_fingerprint) REFERENCES txflow_execution "
                + "(execution_id, definition_fingerprint)");

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> validate(dataSource));

        assertEquals("TXFLOW_SCHEMA_INCOMPATIBLE", failure.getCode());
    }

    @Test
    void validationRejectsUniqueReplacementForRequiredIndex() throws Exception {
        JdbcDataSource dataSource = dataSource();
        migrate(dataSource).close();
        execute(dataSource, "DROP INDEX idx_txflow_resource_execution");
        execute(dataSource, "CREATE UNIQUE INDEX idx_txflow_resource_execution "
                + "ON txflow_resource_lease (execution_id)");

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> validate(dataSource));

        assertEquals("TXFLOW_SCHEMA_INCOMPATIBLE", failure.getCode());
    }

    @Test
    void validationRejectsTimestampWithoutTimeZone() throws Exception {
        JdbcDataSource dataSource = dataSource();
        migrate(dataSource).close();
        execute(dataSource, "ALTER TABLE txflow_execution ALTER COLUMN updated_at "
                + "SET DATA TYPE TIMESTAMP");

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> validate(dataSource));

        assertEquals("TXFLOW_SCHEMA_INCOMPATIBLE", failure.getCode());
    }

    @Test
    void validationRejectsTimestampBelowMicrosecondPrecision() throws Exception {
        JdbcDataSource dataSource = dataSource();
        migrate(dataSource).close();
        execute(dataSource, "ALTER TABLE txflow_execution ALTER COLUMN updated_at "
                + "SET DATA TYPE TIMESTAMP(0) WITH TIME ZONE");

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> validate(dataSource));

        assertEquals("TXFLOW_SCHEMA_INCOMPATIBLE", failure.getCode());
    }

    @Test
    void validationRejectsPayloadSmallerThanCodecLimit() throws Exception {
        JdbcDataSource dataSource = dataSource();
        migrate(dataSource).close();
        execute(dataSource, "ALTER TABLE txflow_execution ALTER COLUMN data_payload "
                + "SET DATA TYPE VARCHAR(1024)");

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> validate(dataSource));

        assertEquals("TXFLOW_SCHEMA_INCOMPATIBLE", failure.getCode());
    }

    @Test
    void validationRejectsFixedWidthIdentityText() throws Exception {
        JdbcDataSource dataSource = dataSource();
        migrate(dataSource).close();
        execute(dataSource, "ALTER TABLE txflow_execution "
                + "ALTER COLUMN definition_fingerprint SET DATA TYPE CHAR(512)");

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> validate(dataSource));

        assertEquals("TXFLOW_SCHEMA_INCOMPATIBLE", failure.getCode());
    }

    @Test
    void validationRejectsExplicitlyCaseInsensitiveIdentityText() throws Exception {
        JdbcDataSource dataSource = dataSource();
        migrate(dataSource).close();
        execute(dataSource, "ALTER TABLE txflow_execution "
                + "ALTER COLUMN definition_fingerprint "
                + "SET DATA TYPE VARCHAR_IGNORECASE(512)");

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> validate(dataSource));

        assertEquals("TXFLOW_SCHEMA_INCOMPATIBLE", failure.getCode());
    }

    @Test
    void validationRejectsChangedMigrationChecksum() throws Exception {
        JdbcDataSource dataSource = dataSource();
        migrate(dataSource).close();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE txflow_schema_history "
                    + "SET checksum = 'changed'");
        }

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> validate(dataSource));

        assertEquals("TXFLOW_SCHEMA_CHECKSUM_MISMATCH", failure.getCode());
    }

    @Test
    void validationRejectsSchemaNewerThanTheLibrary() throws Exception {
        JdbcDataSource dataSource = dataSource();
        migrate(dataSource).close();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO txflow_schema_history "
                             + "(version_no, description, checksum, installed_at) "
                             + "VALUES (?, ?, ?, ?)")) {
            statement.setInt(1, RdbmsSchemaManager.CURRENT_VERSION + 1);
            statement.setString(2, "future migration");
            statement.setString(3, "future-checksum");
            statement.setTimestamp(4, Timestamp.from(Instant.parse("2026-07-14T00:00:00Z")));
            statement.executeUpdate();
        }

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> validate(dataSource));

        assertEquals("TXFLOW_SCHEMA_NEWER", failure.getCode());
    }

    private RdbmsFlowExecutionStore migrate(JdbcDataSource dataSource) {
        return RdbmsFlowExecutionStore.builder()
                .dataSource(dataSource)
                .schemaManagement(SchemaManagement.MIGRATE)
                .build();
    }

    private RdbmsFlowExecutionStore validate(JdbcDataSource dataSource) {
        return RdbmsFlowExecutionStore.builder()
                .dataSource(dataSource)
                .schemaManagement(SchemaManagement.VALIDATE)
                .build();
    }

    private JdbcDataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:schema-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1");
        return dataSource;
    }

    private JdbcDataSource scopedDataSource(JdbcDataSource dataSource, String schema) {
        JdbcDataSource scoped = new JdbcDataSource();
        scoped.setURL(dataSource.getURL() + ";SCHEMA=" + schema);
        return scoped;
    }

    private void createHistoryTable(JdbcDataSource dataSource) throws Exception {
        execute(dataSource, """
                CREATE TABLE txflow_schema_history (
                    version_no INTEGER PRIMARY KEY,
                    description VARCHAR(255) NOT NULL,
                    checksum VARCHAR(64) NOT NULL,
                    installed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
                )
                """);
    }

    private void createExecutionTable(JdbcDataSource dataSource) throws Exception {
        execute(dataSource, """
                CREATE TABLE txflow_execution (
                    execution_id VARCHAR(512) PRIMARY KEY,
                    definition_fingerprint VARCHAR(512) NOT NULL,
                    request_fingerprint VARCHAR(512) NOT NULL,
                    execution_state VARCHAR(64) NOT NULL,
                    revision_no BIGINT NOT NULL,
                    last_sequence BIGINT NOT NULL,
                    compacted_through BIGINT NOT NULL,
                    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
                    data_format VARCHAR(64) NOT NULL,
                    data_version INTEGER NOT NULL,
                    data_payload CLOB NOT NULL
                )
                """);
    }

    private boolean tableExists(Connection connection, String table) throws Exception {
        try (ResultSet tables = connection.getMetaData().getTables(
                connection.getCatalog(), connection.getSchema(), table,
                new String[]{"TABLE"})) {
            return tables.next();
        }
    }

    private boolean indexExists(Connection connection, String table, String index)
            throws Exception {
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(
                connection.getCatalog(), connection.getSchema(), table, false, false)) {
            while (indexes.next()) {
                if (index.equals(indexes.getString("INDEX_NAME"))) return true;
            }
            return false;
        }
    }

    private boolean constraintExists(Connection connection, String constraint)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                        + "WHERE CONSTRAINT_SCHEMA = ? AND CONSTRAINT_NAME = ?")) {
            statement.setString(1, connection.getSchema());
            statement.setString(2, constraint);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
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

    private String primaryKeyName(Connection connection, String table) throws Exception {
        try (ResultSet keys = connection.getMetaData().getPrimaryKeys(
                connection.getCatalog(), connection.getSchema(), table)) {
            if (!keys.next()) throw new AssertionError("Primary key is missing before corruption");
            return keys.getString("PK_NAME");
        }
    }
}
