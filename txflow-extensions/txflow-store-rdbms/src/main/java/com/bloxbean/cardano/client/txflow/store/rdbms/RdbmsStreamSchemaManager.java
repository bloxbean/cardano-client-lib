package com.bloxbean.cardano.client.txflow.store.rdbms;

import com.bloxbean.cardano.client.txflow.store.FlowStoreException;
import com.bloxbean.cardano.client.txflow.stream.TxStreamStoreCodec;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Applies and validates the independently versioned TxFlow <em>stream</em> relational schema.
 *
 * <p>This mirrors {@link RdbmsSchemaManager} but owns a separate migration namespace
 * (history table {@code txstream_schema_history}, resources under {@code /db/txstream/...}) so
 * the stream store's schema evolves independently of the engine execution store's. The stream
 * tables deliberately use a {@code txstream_} prefix rather than {@code txflow_stream_}: the
 * engine store's own migration treats <em>every</em> {@code txflow_}-prefixed table as its own, so
 * a shared database in which the stream store migrated first would otherwise break the engine
 * store's migration. The disjoint prefixes let both stores share one database or schema without
 * either migration seeing the other's tables.</p>
 */
final class RdbmsStreamSchemaManager {
    static final int CURRENT_VERSION = 1;
    private static final Object H2_MIGRATION_LOCK = new Object();
    private static final String OBJECT_PREFIX = "txstream_";
    private static final String HISTORY_TABLE = "txstream_schema_history";
    private static final List<String> MIGRATION_TABLE_ORDER = List.of(
            HISTORY_TABLE,
            "txstream_item",
            "txstream_binding",
            "txstream_planned",
            "txstream_batch",
            "txstream_bootstrap",
            "txstream_ownership");
    private static final Map<String, TableSpec> REQUIRED_SCHEMA = requiredSchema();

    private final DataSource dataSource;
    private final TxFlowSqlDialect dialect;
    private final Clock clock;

    RdbmsStreamSchemaManager(DataSource dataSource, TxFlowSqlDialect dialect, Clock clock) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Selects the stream migration resource for a dialect. The stream store never reuses
     * {@link TxFlowSqlDialect#schemaResource()} (that names the engine store's script).
     *
     * @param dialect certified dialect
     * @return classpath resource for the stream schema
     */
    static String streamSchemaResource(TxFlowSqlDialect dialect) {
        if (dialect == H2Dialect.INSTANCE) {
            return "/db/txstream/h2/V1__txstream_store.sql";
        }
        if (dialect == PostgresDialect.INSTANCE) {
            return "/db/txstream/postgresql/V1__txstream_store.sql";
        }
        throw new FlowStoreException("TXFLOW_SCHEMA_RESOURCE_MISSING",
                "No stream schema resource for dialect " + dialect.name());
    }

    private static Map<String, TableSpec> requiredSchema() {
        Map<String, TableSpec> schema = new LinkedHashMap<>();
        schema.put(HISTORY_TABLE, table(Map.of(
                "version_no", integer(false),
                "description", text(255, false),
                "checksum", text(64, false),
                "installed_at", timestamp(false)),
                List.of("version_no"), Map.of()));
        schema.put("txstream_item", table(Map.ofEntries(
                        Map.entry("item_id", text(512, false)),
                        Map.entry("stream_id", text(255, true)),
                        Map.entry("idempotency_key", text(512, true)),
                        Map.entry("lane_name", text(512, true)),
                        Map.entry("fingerprint", text(512, true)),
                        Map.entry("accepted_at", timestamp(true)),
                        Map.entry("status", text(64, true)),
                        Map.entry("execution_id", text(512, true)),
                        Map.entry("step_id", text(512, true)),
                        Map.entry("projection_lane_name", text(512, true)),
                        Map.entry("transaction_hash", text(512, true)),
                        Map.entry("error_code", text(255, true)),
                        Map.entry("error_message", text(4096, true)),
                        Map.entry("updated_at", timestamp(true)),
                        Map.entry("projection_sequence", bigint(true)),
                        Map.entry("terminal", bool(true))),
                List.of("item_id"),
                Map.of("idx_txstream_item_nonterminal", List.of("stream_id", "terminal"))));
        schema.put("txstream_binding", table(Map.of(
                        "item_id", text(512, false),
                        "execution_id", text(512, false),
                        "flow_id", text(512, false),
                        "step_id", text(512, false),
                        "lane_name", text(512, false),
                        "outcome", text(32, true)),
                List.of("item_id"), Map.of()));
        schema.put("txstream_planned", table(Map.of(
                        "execution_id", text(512, false),
                        "stream_id", text(255, false),
                        "idempotency_key", text(512, false),
                        "lane_name", text(512, false),
                        "canonical_spending_identity", text(1024, false),
                        "portable_flow", payload(false),
                        "metadata_payload", payload(false)),
                List.of("execution_id"),
                Map.of("idx_txstream_planned_stream", List.of("stream_id"))));
        schema.put("txstream_batch", table(Map.ofEntries(
                        Map.entry("stream_id", text(255, false)),
                        Map.entry("batch_id", text(255, false)),
                        Map.entry("status", text(64, false)),
                        Map.entry("item_ids", payload(false)),
                        Map.entry("execution_ids", payload(false)),
                        Map.entry("failure_code", text(255, true)),
                        Map.entry("failure_message", text(4096, true))),
                List.of("stream_id", "batch_id"), Map.of()));
        schema.put("txstream_bootstrap", table(Map.of(
                        "stream_id", text(255, false),
                        "fingerprint", text(1024, false)),
                List.of("stream_id"), Map.of()));
        schema.put("txstream_ownership", table(Map.of(
                        "stream_id", text(255, false),
                        "owner_token", text(512, true),
                        "epoch", bigint(false),
                        "expires_at", timestamp(false)),
                List.of("stream_id"), Map.of()));
        return Map.copyOf(schema);
    }

    private static TableSpec table(Map<String, ColumnSpec> columns, List<String> primaryKey,
                                   Map<String, List<String>> indexes) {
        return new TableSpec(Map.copyOf(columns), List.copyOf(primaryKey), Map.copyOf(indexes));
    }

    private static ColumnSpec text(int minimumSize, boolean nullable) {
        return new ColumnSpec(ColumnKind.TEXT, nullable, minimumSize);
    }

    private static ColumnSpec payload(boolean nullable) {
        return new ColumnSpec(ColumnKind.PAYLOAD, nullable,
                TxStreamStoreCodec.DEFAULT_MAX_PAYLOAD_BYTES);
    }

    private static ColumnSpec integer(boolean nullable) {
        return new ColumnSpec(ColumnKind.INTEGER, nullable, 0);
    }

    private static ColumnSpec bigint(boolean nullable) {
        return new ColumnSpec(ColumnKind.BIGINT, nullable, 0);
    }

    private static ColumnSpec bool(boolean nullable) {
        return new ColumnSpec(ColumnKind.BOOLEAN, nullable, 0);
    }

    private static ColumnSpec timestamp(boolean nullable) {
        return new ColumnSpec(ColumnKind.TIMESTAMP, nullable, 0);
    }

    void initialize(SchemaManagement management) {
        Objects.requireNonNull(management, "management");
        if (management == SchemaManagement.NONE) return;
        if (dialect == H2Dialect.INSTANCE) {
            synchronized (H2_MIGRATION_LOCK) {
                initializeTransaction(management);
            }
        } else {
            initializeTransaction(management);
        }
    }

    private void initializeTransaction(SchemaManagement management) {
        inTransaction(connection -> {
            dialect.validateDatabase(connection);
            dialect.acquireMigrationLock(connection);
            Migration migration = migration();
            Map<String, String> tables = tablesInEffectiveSchema(connection);
            boolean historyExists = tables.containsKey(
                    runtimeIdentifier(connection, HISTORY_TABLE));
            boolean streamObjectsExist = containsStreamObjects(tables);
            MigrationState state = historyExists
                    ? readMigrationState(connection, migration) : MigrationState.MISSING;
            if (state == MigrationState.CURRENT) {
                validateRequiredSchema(connection);
                return null;
            }
            if (historyExists || streamObjectsExist) {
                if (!canRecoverInterruptedH2Migration(management) || !historyExists) {
                    if (historyExists) {
                        throw incompatible(
                                "TxFlow stream migration history has no supported current version");
                    }
                    throw incompatible(
                            "Pre-existing TxFlow stream tables have no compatible migration history");
                }
                repairRecoverableH2PartialSchema(connection, tables);
            }
            if (management == SchemaManagement.VALIDATE) {
                throw new FlowStoreException("TXFLOW_SCHEMA_MISSING",
                        "TxFlow stream database schema is not initialized");
            }
            executeScript(connection, migration.sql());
            validateRequiredSchema(connection);
            insertHistory(connection, migration);
            return null;
        });
    }

    private boolean canRecoverInterruptedH2Migration(SchemaManagement management) {
        return dialect == H2Dialect.INSTANCE && management == SchemaManagement.MIGRATE;
    }

    private MigrationState readMigrationState(Connection connection, Migration expected)
            throws SQLException {
        boolean currentFound = false;
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT version_no, checksum FROM " + HISTORY_TABLE
                             + " ORDER BY version_no")) {
            while (rows.next()) {
                int version = rows.getInt(1);
                if (version > CURRENT_VERSION) {
                    throw new FlowStoreException("TXFLOW_SCHEMA_NEWER",
                            "Database stream schema is newer than this TxFlow store");
                }
                if (version == CURRENT_VERSION) {
                    currentFound = true;
                    if (!expected.checksum().equals(rows.getString(2))) {
                        throw new FlowStoreException("TXFLOW_SCHEMA_CHECKSUM_MISMATCH",
                                "TxFlow stream schema migration checksum does not match");
                    }
                }
            }
        }
        return currentFound ? MigrationState.CURRENT : MigrationState.MISSING;
    }

    private void insertHistory(Connection connection, Migration migration) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + HISTORY_TABLE
                        + " (version_no, description, checksum, installed_at) VALUES (?, ?, ?, ?)")) {
            statement.setInt(1, CURRENT_VERSION);
            statement.setString(2, "initial TxFlow stream store");
            statement.setString(3, migration.checksum());
            statement.setTimestamp(4, Timestamp.from(
                    clock.instant().truncatedTo(ChronoUnit.MICROS)));
            statement.executeUpdate();
        }
    }

    private void repairRecoverableH2PartialSchema(
            Connection connection, Map<String, String> tables) throws SQLException {
        String schema = effectiveSchema(connection);
        Map<String, String> logicalByRuntimeName = new LinkedHashMap<>();
        for (String logicalName : MIGRATION_TABLE_ORDER) {
            logicalByRuntimeName.put(runtimeIdentifier(connection, logicalName), logicalName);
        }
        for (String actualTable : tables.keySet()) {
            if (normalize(actualTable).startsWith(OBJECT_PREFIX)
                    && !logicalByRuntimeName.containsKey(actualTable)) {
                throw incompatible(
                        "Interrupted H2 stream migration contains an unexpected table");
            }
        }

        int prefixLength = 0;
        boolean missingSeen = false;
        for (String logicalName : MIGRATION_TABLE_ORDER) {
            boolean present = tables.containsKey(runtimeIdentifier(connection, logicalName));
            if (!present) {
                missingSeen = true;
            } else if (missingSeen) {
                throw incompatible(
                        "Interrupted H2 stream migration tables are not a valid V1 prefix");
            } else {
                prefixLength++;
            }
        }
        if (prefixLength == 0) {
            throw incompatible("Interrupted H2 stream migration history marker is missing");
        }

        String actualHistory = tables.get(runtimeIdentifier(connection, HISTORY_TABLE));
        TableSpec history = REQUIRED_SCHEMA.get(HISTORY_TABLE);
        validateColumns(connection, schema, actualHistory, history, true);
        validatePrimaryKey(connection, schema, actualHistory, history);

        for (int index = 0; index < prefixLength; index++) {
            requireEmptyRecoveryTable(connection, MIGRATION_TABLE_ORDER.get(index));
        }
        for (int index = prefixLength - 1; index >= 0; index--) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP TABLE " + MIGRATION_TABLE_ORDER.get(index));
            }
        }
    }

    private void requireEmptyRecoveryTable(Connection connection, String table)
            throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT 1 FROM " + table + " LIMIT 1")) {
            if (rows.next()) {
                throw incompatible(
                        "Interrupted H2 stream migration contains durable rows");
            }
        }
    }

    private void validateRequiredSchema(Connection connection) throws SQLException {
        String schema = effectiveSchema(connection);
        Map<String, String> tables = tablesInEffectiveSchema(connection);
        for (Map.Entry<String, TableSpec> required : REQUIRED_SCHEMA.entrySet()) {
            String actualTable = tables.get(runtimeIdentifier(connection, required.getKey()));
            if (actualTable == null) {
                throw incompatible("Required TxFlow stream table is missing: "
                        + required.getKey());
            }
            validateColumns(connection, schema, actualTable, required.getValue());
            validatePrimaryKey(connection, schema, actualTable, required.getValue());
            validateIndexes(connection, schema, actualTable, required.getValue());
        }
    }

    private void validateColumns(Connection connection, String schema, String table,
                                 TableSpec required) throws SQLException {
        validateColumns(connection, schema, table, required, false);
    }

    private void validateColumns(Connection connection, String schema, String table,
                                 TableSpec required, boolean exact) throws SQLException {
        Map<String, ActualColumn> actual = new LinkedHashMap<>();
        DatabaseMetaData metadata = connection.getMetaData();
        String catalog = connection.getCatalog();
        try (ResultSet columns = metadata.getColumns(catalog, schema, table, "%")) {
            while (columns.next()) {
                if (!matchesMetadataObject(columns, "TABLE_CAT", catalog,
                        "TABLE_SCHEM", schema, "TABLE_NAME", table)) continue;
                int fractionalDigits = columns.getInt("DECIMAL_DIGITS");
                Integer nullableFractionalDigits = columns.wasNull() ? null : fractionalDigits;
                String name = columns.getString("COLUMN_NAME");
                actual.put(name, new ActualColumn(
                        columns.getInt("DATA_TYPE"), columns.getString("TYPE_NAME"),
                        columns.getInt("NULLABLE"), columns.getInt("COLUMN_SIZE"),
                        nullableFractionalDigits));
            }
        }
        if (exact && !actual.keySet().equals(
                new LinkedHashSet<>(runtimeIdentifiers(
                        connection, List.copyOf(required.columns().keySet()))))) {
            throw incompatible("Interrupted H2 stream migration has unexpected columns: "
                    + normalize(table));
        }
        for (Map.Entry<String, ColumnSpec> entry : required.columns().entrySet()) {
            String expectedColumn = runtimeIdentifier(connection, entry.getKey());
            ActualColumn column = actual.get(expectedColumn);
            ColumnSpec expected = entry.getValue();
            if (column == null || !expected.kind().accepts(column, dialect)
                    || column.size() < expected.minimumSize()
                    || (column.nullability() == DatabaseMetaData.columnNullable)
                    != expected.nullable()
                    || column.nullability() == DatabaseMetaData.columnNullableUnknown) {
                throw incompatible("Required stream column is incompatible: "
                        + normalize(table) + '.' + entry.getKey());
            }
            if (expected.kind() == ColumnKind.TEXT
                    && !dialect.hasDeterministicTextComparison(
                    connection, schema, table, expectedColumn)) {
                throw incompatible("Required stream text comparison is incompatible: "
                        + normalize(table) + '.' + entry.getKey());
            }
        }
    }

    private void validatePrimaryKey(Connection connection, String schema, String table,
                                    TableSpec required) throws SQLException {
        String catalog = connection.getCatalog();
        Map<Short, String> ordered = new TreeMap<>();
        try (ResultSet keys = connection.getMetaData().getPrimaryKeys(catalog, schema, table)) {
            while (keys.next()) {
                if (!matchesMetadataObject(keys, "TABLE_CAT", catalog,
                        "TABLE_SCHEM", schema, "TABLE_NAME", table)) continue;
                ordered.put(keys.getShort("KEY_SEQ"), keys.getString("COLUMN_NAME"));
            }
        }
        if (!List.copyOf(ordered.values()).equals(runtimeIdentifiers(
                connection, required.primaryKey()))) {
            throw incompatible("Required stream primary key is incompatible: " + normalize(table));
        }
    }

    private void validateIndexes(Connection connection, String schema, String table,
                                 TableSpec required) throws SQLException {
        if (required.indexes().isEmpty()) return;
        String catalog = connection.getCatalog();
        Map<String, ActualIndex> actual = new LinkedHashMap<>();
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(
                catalog, schema, table, false, false)) {
            while (indexes.next()) {
                if (!matchesMetadataObject(indexes, "TABLE_CAT", catalog,
                        "TABLE_SCHEM", schema, "TABLE_NAME", table)) continue;
                if (indexes.getShort("TYPE") == DatabaseMetaData.tableIndexStatistic) continue;
                String name = indexes.getString("INDEX_NAME");
                String column = indexes.getString("COLUMN_NAME");
                if (name == null || column == null) continue;
                boolean nonUnique = indexes.getBoolean("NON_UNIQUE");
                String filter = indexes.getString("FILTER_CONDITION");
                ActualIndex index = actual.computeIfAbsent(name,
                        ignored -> new ActualIndex(new TreeMap<>(), nonUnique, true));
                index.columns().put(indexes.getShort("ORDINAL_POSITION"), column);
                index.compatible(index.compatible() && index.nonUnique() == nonUnique
                        && (filter == null || filter.isBlank()));
            }
        }
        for (Map.Entry<String, List<String>> expected : required.indexes().entrySet()) {
            String expectedName = runtimeIdentifier(connection, expected.getKey());
            ActualIndex index = actual.get(expectedName);
            if (index == null || !index.nonUnique() || !index.compatible()
                    || !List.copyOf(index.columns().values()).equals(
                    runtimeIdentifiers(connection, expected.getValue()))
                    || !dialect.hasUsableIndex(connection, schema, table, expectedName)) {
                throw incompatible("Required stream index is incompatible: " + expected.getKey());
            }
        }
    }

    private void executeScript(Connection connection, String sql) throws SQLException {
        for (String command : splitStatements(sql)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(command);
            }
        }
    }

    private Map<String, String> tablesInEffectiveSchema(Connection connection)
            throws SQLException {
        String schema = effectiveSchema(connection);
        String catalog = connection.getCatalog();
        DatabaseMetaData metadata = connection.getMetaData();
        Map<String, String> result = new LinkedHashMap<>();
        try (ResultSet tables = metadata.getTables(catalog, schema, "%",
                new String[]{"TABLE"})) {
            while (tables.next()) {
                if (!Objects.equals(catalog, tables.getString("TABLE_CAT"))
                        || !Objects.equals(schema, tables.getString("TABLE_SCHEM"))) {
                    continue;
                }
                String name = tables.getString("TABLE_NAME");
                result.put(name, name);
            }
        }
        return result;
    }

    private String effectiveSchema(Connection connection) throws SQLException {
        String schema = connection.getSchema();
        if (schema != null && !schema.isBlank()) return schema;
        try (Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("SELECT CURRENT_SCHEMA")) {
            if (row.next() && row.getString(1) != null && !row.getString(1).isBlank()) {
                return row.getString(1);
            }
        }
        throw incompatible("The effective database schema cannot be determined");
    }

    private boolean containsStreamObjects(Map<String, String> tables) {
        return tables.keySet().stream()
                .map(this::normalize)
                .anyMatch(name -> name.startsWith(OBJECT_PREFIX));
    }

    private boolean matchesMetadataObject(ResultSet rows,
                                          String catalogColumn, String catalog,
                                          String schemaColumn, String schema,
                                          String tableColumn, String table)
            throws SQLException {
        return Objects.equals(catalog, rows.getString(catalogColumn))
                && Objects.equals(schema, rows.getString(schemaColumn))
                && Objects.equals(table, rows.getString(tableColumn));
    }

    private List<String> runtimeIdentifiers(Connection connection, List<String> identifiers)
            throws SQLException {
        List<String> result = new ArrayList<>(identifiers.size());
        for (String identifier : identifiers) {
            result.add(runtimeIdentifier(connection, identifier));
        }
        return List.copyOf(result);
    }

    private String runtimeIdentifier(Connection connection, String identifier)
            throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        if (metadata.storesUpperCaseIdentifiers()) {
            return identifier.toUpperCase(Locale.ROOT);
        }
        if (metadata.storesLowerCaseIdentifiers()) {
            return identifier.toLowerCase(Locale.ROOT);
        }
        return identifier;
    }

    private String normalize(String identifier) {
        return identifier.toLowerCase(Locale.ROOT);
    }

    private FlowStoreException incompatible(String message) {
        return new FlowStoreException("TXFLOW_SCHEMA_INCOMPATIBLE", message);
    }

    private Migration migration() {
        String resource = streamSchemaResource(dialect);
        try (InputStream stream = RdbmsStreamSchemaManager.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new FlowStoreException("TXFLOW_SCHEMA_RESOURCE_MISSING",
                        "TxFlow stream schema resource is unavailable for " + dialect.name());
            }
            byte[] bytes = stream.readAllBytes();
            return new Migration(new String(bytes, StandardCharsets.UTF_8), sha256(bytes));
        } catch (IOException failure) {
            throw new FlowStoreException("TXFLOW_SCHEMA_RESOURCE_FAILED",
                    "TxFlow stream schema resource could not be read", failure);
        }
    }

    private List<String> splitStatements(String script) {
        StringBuilder normalized = new StringBuilder();
        for (String line : script.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("--")) normalized.append(line).append('\n');
        }
        List<String> statements = new ArrayList<>();
        for (String candidate : normalized.toString().split(";")) {
            String statement = candidate.trim();
            if (!statement.isEmpty()) statements.add(statement);
        }
        return statements;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
                    .toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private <T> T inTransaction(SqlWork<T> work) {
        Connection connection;
        try {
            connection = dataSource.getConnection();
        } catch (SQLException failure) {
            throw schemaOperationFailed(failure);
        }

        boolean originalAutoCommit;
        try {
            originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
        } catch (SQLException failure) {
            closeAfterFailure(connection, failure);
            throw schemaOperationFailed(failure);
        }

        T result;
        try {
            result = work.apply(connection);
        } catch (FlowStoreException failure) {
            rollbackOrThrow(connection, failure);
            restoreAutoCommitAfterFailure(connection, originalAutoCommit, failure);
            closeAfterFailure(connection, failure);
            throw failure;
        } catch (SQLException failure) {
            rollbackOrThrow(connection, failure);
            restoreAutoCommitAfterFailure(connection, originalAutoCommit, failure);
            closeAfterFailure(connection, failure);
            throw schemaOperationFailed(failure);
        } catch (RuntimeException | Error failure) {
            rollbackOrThrow(connection, failure);
            restoreAutoCommitAfterFailure(connection, originalAutoCommit, failure);
            closeAfterFailure(connection, failure);
            throw failure;
        }

        try {
            connection.commit();
        } catch (SQLException failure) {
            FlowStoreException uncertain = new FlowStoreException(
                    "TXFLOW_STORE_COMMIT_UNCERTAIN",
                    "TxFlow stream schema commit outcome is uncertain",
                    RdbmsSqlExceptionSanitizer.sanitize(failure));
            closeAfterFailure(connection, uncertain);
            throw uncertain;
        }

        restoreAutoCommitAfterSuccess(connection, originalAutoCommit);
        closeAfterSuccess(connection);
        return result;
    }

    private void rollbackOrThrow(Connection connection, Throwable original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            FlowStoreException uncertain = new FlowStoreException(
                    "TXFLOW_STORE_ROLLBACK_UNCERTAIN",
                    "TxFlow stream schema rollback outcome is uncertain",
                    RdbmsSqlExceptionSanitizer.sanitize(rollbackFailure));
            uncertain.addSuppressed(sanitizeOriginalFailure(original));
            closeAfterFailure(connection, uncertain);
            throw uncertain;
        }
    }

    private Throwable sanitizeOriginalFailure(Throwable original) {
        return original instanceof SQLException sqlFailure
                ? RdbmsSqlExceptionSanitizer.sanitize(sqlFailure)
                : original;
    }

    private FlowStoreException schemaOperationFailed(SQLException failure) {
        return new FlowStoreException("TXFLOW_SCHEMA_OPERATION_FAILED",
                "TxFlow stream database schema operation failed",
                RdbmsSqlExceptionSanitizer.sanitize(failure));
    }

    private void restoreAutoCommitAfterFailure(Connection connection, boolean autoCommit,
                                               Throwable original) {
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException restoreFailure) {
            original.addSuppressed(RdbmsSqlExceptionSanitizer.sanitize(restoreFailure));
        }
    }

    private void closeAfterFailure(Connection connection, Throwable original) {
        try {
            connection.close();
        } catch (SQLException closeFailure) {
            original.addSuppressed(RdbmsSqlExceptionSanitizer.sanitize(closeFailure));
        }
    }

    private void restoreAutoCommitAfterSuccess(Connection connection, boolean autoCommit) {
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException ignored) {
            // The migration is committed; connection-state cleanup cannot change its outcome.
        }
    }

    private void closeAfterSuccess(Connection connection) {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // The migration is committed. Connection disposal is best-effort here.
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T apply(Connection connection) throws SQLException;
    }

    private enum MigrationState { MISSING, CURRENT }

    private enum ColumnKind {
        TEXT {
            @Override
            boolean accepts(ActualColumn column, TxFlowSqlDialect dialect) {
                int type = column.jdbcType();
                return (type == Types.VARCHAR || type == Types.LONGVARCHAR
                        || type == Types.NVARCHAR || type == Types.LONGNVARCHAR)
                        && !isExplicitlyCaseInsensitive(column.typeName());
            }
        },
        PAYLOAD {
            @Override
            boolean accepts(ActualColumn column, TxFlowSqlDialect dialect) {
                int type = column.jdbcType();
                return type == Types.CLOB || type == Types.NCLOB
                        || type == Types.LONGVARCHAR || type == Types.LONGNVARCHAR
                        || type == Types.VARCHAR || type == Types.NVARCHAR;
            }
        },
        INTEGER {
            @Override
            boolean accepts(ActualColumn column, TxFlowSqlDialect dialect) {
                return column.jdbcType() == Types.INTEGER;
            }
        },
        BIGINT {
            @Override
            boolean accepts(ActualColumn column, TxFlowSqlDialect dialect) {
                return column.jdbcType() == Types.BIGINT;
            }
        },
        BOOLEAN {
            @Override
            boolean accepts(ActualColumn column, TxFlowSqlDialect dialect) {
                // H2 reports BOOLEAN; the PostgreSQL driver reports bool as BIT.
                return column.jdbcType() == Types.BOOLEAN || column.jdbcType() == Types.BIT;
            }
        },
        TIMESTAMP {
            @Override
            boolean accepts(ActualColumn column, TxFlowSqlDialect dialect) {
                String typeName = normalizeTypeName(column.typeName());
                boolean supportedType;
                if (dialect == PostgresDialect.INSTANCE) {
                    supportedType = typeName.equals("TIMESTAMPTZ")
                            || typeName.equals("TIMESTAMP WITH TIME ZONE");
                } else if (dialect == H2Dialect.INSTANCE) {
                    supportedType = column.jdbcType() == Types.TIMESTAMP_WITH_TIMEZONE
                            && typeName.equals("TIMESTAMP WITH TIME ZONE");
                } else {
                    supportedType = column.jdbcType() == Types.TIMESTAMP_WITH_TIMEZONE;
                }
                int minimumDigits = dialect.minimumTimestampFractionalDigits();
                return supportedType && (minimumDigits == 0
                        || column.fractionalDigits() != null
                        && column.fractionalDigits() >= minimumDigits);
            }
        };

        abstract boolean accepts(ActualColumn column, TxFlowSqlDialect dialect);

        private static boolean isExplicitlyCaseInsensitive(String typeName) {
            if (typeName == null) return true;
            String normalized = typeName.toUpperCase(Locale.ROOT);
            return normalized.contains("IGNORECASE") || normalized.equals("CITEXT");
        }

        private static String normalizeTypeName(String typeName) {
            return typeName == null ? "" : typeName.trim().toUpperCase(Locale.ROOT);
        }
    }

    private record ColumnSpec(ColumnKind kind, boolean nullable, int minimumSize) {
    }

    private record ActualColumn(int jdbcType, String typeName, int nullability, int size,
                                Integer fractionalDigits) {
    }

    private static final class ActualIndex {
        private final Map<Short, String> columns;
        private final boolean nonUnique;
        private boolean compatible;

        private ActualIndex(Map<Short, String> columns, boolean nonUnique, boolean compatible) {
            this.columns = columns;
            this.nonUnique = nonUnique;
            this.compatible = compatible;
        }

        private Map<Short, String> columns() {
            return columns;
        }

        private boolean nonUnique() {
            return nonUnique;
        }

        private boolean compatible() {
            return compatible;
        }

        private void compatible(boolean value) {
            compatible = value;
        }
    }

    private record TableSpec(Map<String, ColumnSpec> columns, List<String> primaryKey,
                             Map<String, List<String>> indexes) {
    }

    private record Migration(String sql, String checksum) {
    }
}
