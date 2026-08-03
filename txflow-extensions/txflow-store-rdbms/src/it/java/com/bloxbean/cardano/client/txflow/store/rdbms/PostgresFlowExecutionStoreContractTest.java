package com.bloxbean.cardano.client.txflow.store.rdbms;

import com.bloxbean.cardano.client.txflow.exec.FlowEvent;
import com.bloxbean.cardano.client.txflow.exec.FlowEventType;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.store.ExecutionLease;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionSnapshot;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionStore;
import com.bloxbean.cardano.client.txflow.store.FlowStoreException;
import com.bloxbean.cardano.client.txflow.store.IdempotencyClaimResult;
import com.bloxbean.cardano.client.txflow.store.MutationFence;
import com.bloxbean.cardano.client.txflow.store.contract.AdjustableClock;
import com.bloxbean.cardano.client.txflow.store.contract.FlowExecutionStoreContract;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the complete store contract and multi-instance fencing against a real PostgreSQL server. */
@Testcontainers
class PostgresFlowExecutionStoreContractTest extends FlowExecutionStoreContract {
    private static final AtomicInteger DATABASE_SEQUENCE = new AtomicInteger();

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17.6-alpine");

    @Override
    protected FlowExecutionStore createStore(AdjustableClock clock) throws Exception {
        return newStore(createDatabase(), clock);
    }

    @Test
    @Timeout(20)
    void concurrentStartersSerializeSchemaMigration() throws Exception {
        String database = createDatabase();
        AdjustableClock migrationClock = new AdjustableClock(NOW);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<RdbmsFlowExecutionStore> first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return newStore(database, migrationClock);
            });
            Future<RdbmsFlowExecutionStore> second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return newStore(database, migrationClock);
            });
            assertTrue(ready.await(5, TimeUnit.SECONDS),
                    "migration workers did not become ready");
            start.countDown();
            try (RdbmsFlowExecutionStore firstStore = first.get(15, TimeUnit.SECONDS);
                 RdbmsFlowExecutionStore secondStore = second.get(15, TimeUnit.SECONDS)) {
                firstStore.createOrGet("tenant", "migration",
                        snapshot("migration-execution"));
                assertTrue(secondStore.get("migration-execution").isPresent());
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void independentStoreInstancesShareLeaseFencing() throws Exception {
        String database = createDatabase();
        AdjustableClock storeClock = new AdjustableClock(NOW);
        try (RdbmsFlowExecutionStore first = newStore(database, storeClock);
             RdbmsFlowExecutionStore second = newStore(
                     database, storeClock, SchemaManagement.VALIDATE)) {
            first.createOrGet("tenant", "multi-instance", snapshot("multi-instance"));
            ExecutionLease stale = first.acquireExecutionLease(
                    "multi-instance", "owner-a", NOW, Duration.ofSeconds(10));

            assertCode("TXFLOW_LEASE_CONFLICT", () -> second.acquireExecutionLease(
                    "multi-instance", "owner-b", NOW.plusSeconds(1),
                    Duration.ofMinutes(1)));
            storeClock.advance(Duration.ofSeconds(11));
            ExecutionLease successor = second.acquireExecutionLease(
                    "multi-instance", "owner-b", storeClock.instant(),
                    Duration.ofMinutes(1));
            assertTrue(successor.epoch() > stale.epoch());
            assertCode("TXFLOW_STALE_FENCE", () -> first.append(
                    "multi-instance", 0, MutationFence.executionOnly(stale), List.of(),
                    current -> current));
        }
    }

    @Test
    void postgresNormalizesSubMicrosecondAndRolloverTimestampsBeforePersistence() {
        Instant initialTime = Instant.parse("2026-07-14T00:00:00.123456789Z");
        FlowExecutionSnapshot initial = new FlowExecutionSnapshot(
                "timestamp-precision", "definition", "request",
                FlowExecutionState.CREATED, 0, 0, 0, initialTime, Map.of());

        FlowExecutionSnapshot created = store.createOrGet(
                "tenant", "timestamp-precision", initial).snapshot();

        assertEquals(Instant.parse("2026-07-14T00:00:00.123456Z"), created.updatedAt());
        assertEquals(created, store.get("timestamp-precision").orElseThrow());

        Instant leaseNow = Instant.parse("2026-07-14T00:00:00.123456789Z");
        ExecutionLease lease = store.acquireExecutionLease(
                "timestamp-precision", "owner", leaseNow, Duration.ofNanos(1));
        assertEquals(Instant.parse("2026-07-14T00:00:00.123457Z"), lease.expiresAt());
        assertTrue(lease.expiresAt().isAfter(leaseNow));
        Instant rolloverTime = Instant.parse("2026-07-14T00:00:00.999999789Z");
        FlowEvent event = new FlowEvent(1, "timestamp-precision",
                FlowEventType.EXECUTION_STARTED, rolloverTime, null, null, Map.of());
        FlowExecutionSnapshot updated = store.append(
                "timestamp-precision", 0, MutationFence.executionOnly(lease), List.of(event),
                current -> current.withState(
                        FlowExecutionState.RUNNING, rolloverTime, Map.of()));

        Instant normalizedRollover = Instant.parse("2026-07-14T00:00:00.999999Z");
        assertEquals(normalizedRollover, updated.updatedAt());
        assertEquals(updated, store.get("timestamp-precision").orElseThrow());
        assertEquals(normalizedRollover,
                store.readEvents("timestamp-precision", 0, 10)
                        .events().get(0).timestamp());
    }

    @Test
    @Timeout(20)
    void applicationManagedDataSourceUsesTheProductionConfigurationPath() throws Exception {
        String database = createDatabase();
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(jdbcUrl(database));
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());

        try (RdbmsFlowExecutionStore dataSourceStore = RdbmsFlowExecutionStore.builder()
                .dataSource(dataSource)
                .dialect(PostgresDialect.INSTANCE)
                .schemaManagement(SchemaManagement.MIGRATE)
                .clock(new AdjustableClock(NOW))
                .build()) {
            dataSourceStore.createOrGet("tenant", "data-source",
                    snapshot("data-source-execution"));
            assertTrue(dataSourceStore.get("data-source-execution").isPresent());
        }
        try (Connection connection = dataSource.getConnection()) {
            assertFalse(connection.isClosed(),
                    "closing the store must not close an application DataSource");
        }
    }

    @Test
    @Timeout(20)
    void serverRaisedSerializationFailureMapsToStableStoreCode() throws Exception {
        String database = createDatabase();
        try (RdbmsFlowExecutionStore ignored = newStore(
                database, new AdjustableClock(NOW))) {
            // Initialize the certified schema before installing a deterministic fault trigger.
        }
        execute(database, """
                CREATE FUNCTION txflow_raise_serialization_failure()
                RETURNS trigger
                LANGUAGE plpgsql
                AS $txflow$
                BEGIN
                    RAISE EXCEPTION 'forced failure; password=server-secret'
                        USING ERRCODE = '40001';
                    RETURN NEW;
                END
                $txflow$
                """);
        execute(database, """
                CREATE TRIGGER txflow_raise_serialization_failure
                BEFORE INSERT ON txflow_execution
                FOR EACH ROW
                EXECUTE FUNCTION txflow_raise_serialization_failure()
                """);

        try (RdbmsFlowExecutionStore store = newStore(
                database, new AdjustableClock(NOW), SchemaManagement.VALIDATE)) {
            FlowStoreException failure = assertThrows(FlowStoreException.class,
                    () -> store.createOrGet("tenant", "forced-serialization",
                            snapshot("forced-serialization")));

            assertEquals("TXFLOW_STORE_SERIALIZATION_FAILURE", failure.getCode());
            SQLException cause = assertInstanceOf(SQLException.class, failure.getCause());
            assertEquals("40001", cause.getSQLState());
            assertFalse(cause.getMessage().contains("server-secret"));
            assertTrue(store.get("forced-serialization").isEmpty(),
                    "the failed transaction must not leave an execution row");
        }
    }

    @Test
    @Timeout(20)
    void competingExecutionIdsForOneClaimLeaveNoOrphanExecution() throws Exception {
        String database = createDatabase();
        AdjustableClock storeClock = new AdjustableClock(NOW);
        try (RdbmsFlowExecutionStore first = newStore(database, storeClock);
             RdbmsFlowExecutionStore second = newStore(
                     database, storeClock, SchemaManagement.VALIDATE)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<IdempotencyClaimResult> firstResult = executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return first.createOrGet(
                            "tenant", "same-claim", snapshot("candidate-a"));
                });
                Future<IdempotencyClaimResult> secondResult = executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return second.createOrGet(
                            "tenant", "same-claim", snapshot("candidate-b"));
                });
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();

                IdempotencyClaimResult a = firstResult.get(10, TimeUnit.SECONDS);
                IdempotencyClaimResult b = secondResult.get(10, TimeUnit.SECONDS);
                assertEquals(1, List.of(a, b).stream()
                        .filter(IdempotencyClaimResult::created).count());
                assertEquals(a.snapshot().executionId(), b.snapshot().executionId());
                String winner = a.snapshot().executionId();
                String loser = "candidate-a".equals(winner) ? "candidate-b" : "candidate-a";
                assertTrue(first.get(winner).isPresent());
                assertTrue(first.get(loser).isEmpty(),
                        "the losing transaction must not leave an unclaimed execution");
            } finally {
                start.countDown();
                executor.shutdownNow();
            }
        }
    }

    @Test
    @Timeout(20)
    void databaseAdvisoryLockSerializesMigrationAcrossConnections() throws Exception {
        String database = createDatabase();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection lockConnection = DriverManager.getConnection(
                jdbcUrl(database), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = lockConnection.createStatement()) {
            statement.execute("SELECT pg_advisory_lock(1415072847)");
            Future<RdbmsFlowExecutionStore> blocked = executor.submit(
                    () -> newStore(database, new AdjustableClock(NOW)));
            assertThrows(TimeoutException.class,
                    () -> blocked.get(750, TimeUnit.MILLISECONDS));
            statement.execute("SELECT pg_advisory_unlock(1415072847)");
            try (RdbmsFlowExecutionStore migrated = blocked.get(10, TimeUnit.SECONDS)) {
                migrated.createOrGet("tenant", "advisory-lock",
                        snapshot("advisory-lock-execution"));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void schemaValidationRejectsTimestampWithoutTimeZone() throws Exception {
        String database = createDatabase();
        try (RdbmsFlowExecutionStore ignored = newStore(
                database, new AdjustableClock(NOW))) {
            // Initialize the certified v1 schema before simulating incompatible drift.
        }
        execute(database, "ALTER TABLE txflow_execution ALTER COLUMN updated_at "
                + "TYPE TIMESTAMP WITHOUT TIME ZONE");

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> newStore(database, new AdjustableClock(NOW),
                        SchemaManagement.VALIDATE));

        assertEquals("TXFLOW_SCHEMA_INCOMPATIBLE", failure.getCode());
    }

    @Test
    void schemaValidationRejectsPayloadSmallerThanCodecLimit() throws Exception {
        String database = createDatabase();
        try (RdbmsFlowExecutionStore ignored = newStore(
                database, new AdjustableClock(NOW))) {
            // Initialize the certified v1 schema before simulating incompatible drift.
        }
        execute(database, "ALTER TABLE txflow_execution ALTER COLUMN data_payload "
                + "TYPE VARCHAR(1024)");

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> newStore(database, new AdjustableClock(NOW),
                        SchemaManagement.VALIDATE));

        assertEquals("TXFLOW_SCHEMA_INCOMPATIBLE", failure.getCode());
    }

    @Test
    void schemaValidationRejectsNondeterministicIdentityCollation() throws Exception {
        String database = createDatabase();
        try (RdbmsFlowExecutionStore ignored = newStore(
                database, new AdjustableClock(NOW))) {
            // Initialize the certified v1 schema before simulating incompatible drift.
        }
        execute(database, "CREATE COLLATION txflow_nondeterministic "
                + "(provider = icu, locale = 'und-u-ks-level2', deterministic = false)");
        execute(database, "ALTER TABLE txflow_execution "
                + "ALTER COLUMN definition_fingerprint TYPE VARCHAR(512) "
                + "COLLATE txflow_nondeterministic");

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> newStore(database, new AdjustableClock(NOW),
                        SchemaManagement.VALIDATE));

        assertEquals("TXFLOW_SCHEMA_INCOMPATIBLE", failure.getCode());
    }

    @Test
    void schemaValidationRejectsForeignKeyIntoAnotherSchema() throws Exception {
        String database = createDatabase();
        try (RdbmsFlowExecutionStore ignored = newStore(
                database, new AdjustableClock(NOW))) {
            // Initialize the certified v1 schema before simulating incompatible drift.
        }
        execute(database, "CREATE SCHEMA other_schema");
        execute(database, "CREATE TABLE other_schema.txflow_execution "
                + "(execution_id VARCHAR(512) PRIMARY KEY)");
        execute(database, "ALTER TABLE txflow_event "
                + "DROP CONSTRAINT fk_txflow_event_execution");
        execute(database, "ALTER TABLE txflow_event "
                + "ADD CONSTRAINT fk_txflow_event_execution FOREIGN KEY (execution_id) "
                + "REFERENCES other_schema.txflow_execution (execution_id)");

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> newStore(database, new AdjustableClock(NOW),
                        SchemaManagement.VALIDATE));

        assertEquals("TXFLOW_SCHEMA_INCOMPATIBLE", failure.getCode());
    }

    @Test
    void schemaValidationRejectsUniqueReplacementForRequiredIndex() throws Exception {
        String database = createDatabase();
        try (RdbmsFlowExecutionStore ignored = newStore(
                database, new AdjustableClock(NOW))) {
            // Initialize the certified v1 schema before simulating incompatible drift.
        }
        execute(database, "DROP INDEX idx_txflow_resource_execution");
        execute(database, "CREATE UNIQUE INDEX idx_txflow_resource_execution "
                + "ON txflow_resource_lease (execution_id)");

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> newStore(database, new AdjustableClock(NOW),
                        SchemaManagement.VALIDATE));

        assertEquals("TXFLOW_SCHEMA_INCOMPATIBLE", failure.getCode());
    }

    @Test
    void schemaValidationRejectsPartialReplacementForRequiredIndex() throws Exception {
        String database = createDatabase();
        try (RdbmsFlowExecutionStore ignored = newStore(
                database, new AdjustableClock(NOW))) {
            // Initialize the certified v1 schema before simulating incompatible drift.
        }
        execute(database, "DROP INDEX idx_txflow_execution_recovery");
        execute(database, "CREATE INDEX idx_txflow_execution_recovery "
                + "ON txflow_execution (execution_state, updated_at) "
                + "WHERE execution_state = 'RUNNING'");

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> newStore(database, new AdjustableClock(NOW),
                        SchemaManagement.VALIDATE));

        assertEquals("TXFLOW_SCHEMA_INCOMPATIBLE", failure.getCode());
    }

    @Test
    void schemaValidationRejectsTimestampBelowMicrosecondPrecision() throws Exception {
        String database = createDatabase();
        try (RdbmsFlowExecutionStore ignored = newStore(
                database, new AdjustableClock(NOW))) {
            // Initialize the certified v1 schema before simulating incompatible drift.
        }
        execute(database, "ALTER TABLE txflow_execution ALTER COLUMN updated_at "
                + "TYPE TIMESTAMPTZ(0)");

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> newStore(database, new AdjustableClock(NOW),
                        SchemaManagement.VALIDATE));

        assertEquals("TXFLOW_SCHEMA_INCOMPATIBLE", failure.getCode());
    }

    @Test
    void postgresDialectRejectsNonUtf8ServerEncoding() throws Exception {
        String database = createDatabase("WITH TEMPLATE template0 ENCODING 'LATIN1' "
                + "LC_COLLATE 'C' LC_CTYPE 'C'");

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> newStore(database, new AdjustableClock(NOW)));

        assertEquals("TXFLOW_DIALECT_ENCODING_UNSUPPORTED", failure.getCode());
    }

    private RdbmsFlowExecutionStore newStore(String database, AdjustableClock storeClock) {
        return newStore(database, storeClock, SchemaManagement.MIGRATE);
    }

    private RdbmsFlowExecutionStore newStore(String database, AdjustableClock storeClock,
                                              SchemaManagement schemaManagement) {
        return RdbmsFlowExecutionStore.builder()
                .jdbcUrl(jdbcUrl(database))
                .username(POSTGRES.getUsername())
                .password(POSTGRES.getPassword())
                .dialect(PostgresDialect.INSTANCE)
                .schemaManagement(schemaManagement)
                .clock(storeClock)
                .build();
    }

    private String createDatabase() throws SQLException {
        return createDatabase("");
    }

    private String createDatabase(String options) throws SQLException {
        String database = "txflow_" + DATABASE_SEQUENCE.incrementAndGet();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            statement.execute("CREATE DATABASE " + database
                    + (options.isBlank() ? "" : " " + options));
        }
        return database;
    }

    private String jdbcUrl(String database) {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ':'
                + POSTGRES.getMappedPort(5432) + '/' + database;
    }

    private void execute(String database, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                jdbcUrl(database), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private FlowExecutionSnapshot snapshot(String executionId) {
        return new FlowExecutionSnapshot(executionId, "definition", "request",
                FlowExecutionState.CREATED, 0, 0, 0, NOW, Map.of());
    }

    private void assertCode(String code, Runnable operation) {
        try {
            operation.run();
        } catch (FlowStoreException failure) {
            assertEquals(code, failure.getCode());
            return;
        }
        throw new AssertionError("Expected FlowStoreException with code " + code);
    }
}
