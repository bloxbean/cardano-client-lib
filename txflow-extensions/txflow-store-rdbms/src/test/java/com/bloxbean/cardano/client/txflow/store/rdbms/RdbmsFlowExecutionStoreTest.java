package com.bloxbean.cardano.client.txflow.store.rdbms;

import com.bloxbean.cardano.client.txflow.exec.FlowEvent;
import com.bloxbean.cardano.client.txflow.exec.FlowEventType;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.store.AttemptState;
import com.bloxbean.cardano.client.txflow.store.EventReadResult;
import com.bloxbean.cardano.client.txflow.store.ExecutionLease;
import com.bloxbean.cardano.client.txflow.store.FlowAttemptSnapshot;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionSnapshot;
import com.bloxbean.cardano.client.txflow.store.FlowStoreException;
import com.bloxbean.cardano.client.txflow.store.IdempotencyClaimResult;
import com.bloxbean.cardano.client.txflow.store.InclusionRecord;
import com.bloxbean.cardano.client.txflow.store.MutationFence;
import com.bloxbean.cardano.client.txflow.store.PersistedBinding;
import com.bloxbean.cardano.client.txflow.store.ResourceLease;
import com.bloxbean.cardano.client.txflow.store.SignedPayload;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RdbmsFlowExecutionStoreTest {
    private static final Instant NOW = Instant.parse("2026-07-14T00:00:00Z");
    private static final Duration LEASE_DURATION = Duration.ofMinutes(1);

    private final List<RdbmsFlowExecutionStore> stores = new ArrayList<>();

    @AfterEach
    void closeStores() {
        stores.forEach(RdbmsFlowExecutionStore::close);
    }

    @Test
    void urlBuilderDetectsH2MigratesAndOwnsOnlyItsLifecycleAnchor() {
        RdbmsFlowExecutionStore store = store();

        assertEquals(H2Dialect.INSTANCE, store.dialect());
        store.createOrGet("tenant", "key", snapshot("execution", "request"));
        assertTrue(store.get("execution").isPresent());

        store.close();
        assertTrue(store.isClosed());
        assertEquals("TXFLOW_STORE_CLOSED", assertThrows(FlowStoreException.class,
                () -> store.get("execution")).getCode());
    }

    @Test
    void applicationDataSourceDefaultsToValidationAndIsNotClosed() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:validate-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1");

        FlowStoreException missing = assertThrows(FlowStoreException.class,
                () -> RdbmsFlowExecutionStore.builder().dataSource(dataSource).build());
        assertEquals("TXFLOW_SCHEMA_MISSING", missing.getCode());

        RdbmsFlowExecutionStore store = RdbmsFlowExecutionStore.builder()
                .dataSource(dataSource).schemaManagement(SchemaManagement.MIGRATE)
                .clock(fixedClock()).build();
        stores.add(store);
        store.close();
        try (Connection ignored = dataSource.getConnection()) {
            assertFalse(ignored.isClosed());
        }
    }

    @Test
    void builderRejectsAmbiguousConfigurationAndDialectMismatch() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:mismatch-" + UUID.randomUUID());

        assertThrows(IllegalStateException.class,
                () -> RdbmsFlowExecutionStore.builder().build());
        assertThrows(IllegalStateException.class,
                () -> RdbmsFlowExecutionStore.builder().dataSource(dataSource)
                        .jdbcUrl("jdbc:h2:mem:other").build());
        FlowStoreException mismatch = assertThrows(FlowStoreException.class,
                () -> RdbmsFlowExecutionStore.builder()
                        .jdbcUrl("jdbc:h2:mem:wrong-dialect")
                        .dialect(PostgresDialect.INSTANCE).build());
        assertEquals("TXFLOW_DIALECT_MISMATCH", mismatch.getCode());
    }

    @Test
    void schemaNonePerformsNoStartupMutation() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:none-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1");
        RdbmsFlowExecutionStore store = RdbmsFlowExecutionStore.builder()
                .dataSource(dataSource).schemaManagement(SchemaManagement.NONE)
                .clock(fixedClock()).build();
        stores.add(store);

        assertEquals("TXFLOW_STORE_OPERATION_FAILED", assertThrows(FlowStoreException.class,
                () -> store.get("missing-schema")).getCode());
    }

    @Test
    void databaseIncompatibleNulIsRejectedBeforeJdbc() {
        RdbmsFlowExecutionStore store = store();

        assertThrows(IllegalArgumentException.class,
                () -> store.get("invalid\u0000execution"));
        assertThrows(IllegalArgumentException.class,
                () -> store.acquireResourceLease(
                        "invalid\u0000resource", "missing", "owner", NOW, LEASE_DURATION));
    }

    @Test
    void idempotencyRoundTripsTypedRecoveryDataAndRejectsBothConflictKinds() {
        RdbmsFlowExecutionStore store = store();
        FlowExecutionSnapshot initial = typedSnapshot("typed", "request-one");

        IdempotencyClaimResult created = store.createOrGet("tenant", "operation", initial);
        IdempotencyClaimResult repeated = store.createOrGet("tenant", "operation",
                typedSnapshot("different-request-id", "request-one"));

        assertTrue(created.created());
        assertFalse(repeated.created());
        assertEquals("typed", repeated.snapshot().executionId());
        assertTypedData(repeated.snapshot());
        FlowStoreException idempotencyConflict = assertThrows(FlowStoreException.class,
                () -> store.createOrGet("tenant", "operation",
                        typedSnapshot("ignored", "different-fingerprint")));
        assertEquals("TXFLOW_IDEMPOTENCY_CONFLICT", idempotencyConflict.getCode());
        FlowStoreException executionConflict = assertThrows(FlowStoreException.class,
                () -> store.createOrGet("other", "claim",
                        snapshot("typed", "request-one")));
        assertEquals("TXFLOW_EXECUTION_ID_CONFLICT", executionConflict.getCode());
    }

    @Test
    void h2NormalizesPersistedTimestampsToMicrosecondsBeforeEncodingAndBinding() {
        RdbmsFlowExecutionStore store = store();
        Instant initialTime = Instant.parse("2026-07-14T00:00:00.123456789Z");
        Instant normalizedInitial = Instant.parse("2026-07-14T00:00:00.123456Z");
        FlowExecutionSnapshot initial = new FlowExecutionSnapshot(
                "timestamp-precision", "definition", "request",
                FlowExecutionState.CREATED, 0, 0, 0, initialTime, Map.of());

        IdempotencyClaimResult created = store.createOrGet(
                "tenant", "timestamp-precision", initial);

        assertEquals(normalizedInitial, created.snapshot().updatedAt());
        assertEquals(normalizedInitial,
                store.get("timestamp-precision").orElseThrow().updatedAt());

        Instant leaseNow = Instant.parse("2026-07-14T00:00:01.123456789Z");
        ExecutionLease lease = store.acquireExecutionLease(
                "timestamp-precision", "owner", leaseNow, Duration.ofNanos(1));
        Instant expectedExpiry = Instant.parse("2026-07-14T00:00:01.123457Z");
        assertEquals(expectedExpiry, lease.expiresAt());
        assertTrue(lease.expiresAt().isAfter(leaseNow));

        Instant renewalNow = leaseNow.plusNanos(100);
        ExecutionLease renewed = store.renewExecutionLease(
                lease, renewalNow, Duration.ofNanos(1));
        assertEquals(expectedExpiry, renewed.expiresAt());
        assertTrue(renewed.expiresAt().isAfter(renewalNow));

        ResourceLease resource = store.acquireResourceLease(
                "wallet", "timestamp-precision", "owner", leaseNow, Duration.ofNanos(1));
        assertEquals(expectedExpiry, resource.expiresAt());
        assertTrue(resource.expiresAt().isAfter(leaseNow));
        ResourceLease renewedResource = store.renewResourceLease(
                resource, renewalNow, Duration.ofNanos(1));
        assertEquals(expectedExpiry, renewedResource.expiresAt());
        assertTrue(renewedResource.expiresAt().isAfter(renewalNow));

        Instant eventTime = Instant.parse("2026-07-14T00:00:02.999999789Z");
        FlowEvent event = new FlowEvent(1, "timestamp-precision",
                FlowEventType.EXECUTION_STARTED, eventTime, null, null, Map.of());
        Instant updateTime = Instant.parse("2026-07-14T00:00:03.987654789Z");
        FlowExecutionSnapshot updated = store.append(
                "timestamp-precision", 0, MutationFence.executionOnly(renewed), List.of(event),
                current -> current.withState(
                        FlowExecutionState.RUNNING, updateTime, Map.of()));

        assertEquals(Instant.parse("2026-07-14T00:00:03.987654Z"), updated.updatedAt());
        assertEquals(updated, store.get("timestamp-precision").orElseThrow());
        assertEquals(Instant.parse("2026-07-14T00:00:02.999999Z"),
                store.readEvents("timestamp-precision", 0, 10)
                        .events().get(0).timestamp());
    }

    @Test
    void appendAtomicallyChecksRevisionFenceAndContiguousEvents() {
        RdbmsFlowExecutionStore store = store();
        store.createOrGet("tenant", "append", snapshot("append", "request"));
        ExecutionLease execution = store.acquireExecutionLease(
                "append", "owner", NOW, LEASE_DURATION);
        ResourceLease resource = store.acquireResourceLease(
                "wallet", "append", "owner", NOW, LEASE_DURATION);
        MutationFence fence = new MutationFence(execution, List.of(resource));
        List<FlowEvent> events = List.of(
                event("append", 1, FlowEventType.EXECUTION_CREATED,
                        Map.of("attempt", 1)),
                event("append", 2, FlowEventType.EXECUTION_STARTED,
                        Map.of("resources", Set.of("wallet"))));

        FlowExecutionSnapshot updated = store.append("append", 0, fence, events,
                current -> current.withState(FlowExecutionState.RUNNING,
                        NOW.plusSeconds(1), Map.of("attempt", 1L)));

        assertEquals(1, updated.revision());
        assertEquals(2, updated.lastSequence());
        assertEquals(FlowExecutionState.RUNNING, store.get("append").orElseThrow().state());
        assertEquals(events, store.readEvents("append", 0, 10).events());

        assertEquals("TXFLOW_REVISION_CONFLICT", assertThrows(FlowStoreException.class,
                () -> store.append("append", 0, fence, List.of(), current -> current))
                .getCode());
        FlowEvent sequenceGap = event("append", 4, FlowEventType.EXECUTION_FAILED, Map.of());
        assertEquals("TXFLOW_EVENT_SEQUENCE", assertThrows(FlowStoreException.class,
                () -> store.append("append", 1, fence, List.of(sequenceGap),
                        current -> current.withState(FlowExecutionState.FAILED,
                                NOW.plusSeconds(2), current.data()))).getCode());
        assertEquals(1, store.get("append").orElseThrow().revision());
        assertEquals(2, store.readEvents("append", 0, 10).events().size());

        ExecutionLease stale = new ExecutionLease("append", "owner",
                execution.epoch() + 100, execution.expiresAt());
        assertEquals("TXFLOW_STALE_FENCE", assertThrows(FlowStoreException.class,
                () -> store.append("append", 1, MutationFence.executionOnly(stale),
                        List.of(), current -> current)).getCode());
    }

    @Test
    void eventPagingAndCompactionPreserveMonotonicJournalMetadata() {
        RdbmsFlowExecutionStore store = store();
        store.createOrGet("tenant", "compact", snapshot("compact", "request"));
        ExecutionLease lease = store.acquireExecutionLease(
                "compact", "owner", NOW, LEASE_DURATION);
        List<FlowEvent> events = List.of(
                event("compact", 1, FlowEventType.EXECUTION_CREATED, Map.of()),
                event("compact", 2, FlowEventType.EXECUTION_COMPLETED, Map.of()));
        store.append("compact", 0, MutationFence.executionOnly(lease), events,
                current -> current.withState(FlowExecutionState.COMPLETED,
                        NOW.plusSeconds(1), Map.of()));

        EventReadResult first = store.readEvents("compact", 0, 1);
        EventReadResult second = store.readEvents("compact", first.nextSequence(), 1);
        assertEquals(List.of(events.get(0)), first.events());
        assertEquals(List.of(events.get(1)), second.events());

        store.compactEvents("compact", 1);
        FlowExecutionSnapshot compacted = store.get("compact").orElseThrow();
        assertEquals(2, compacted.revision());
        assertEquals(2, compacted.lastSequence());
        assertEquals(1, compacted.compactedThroughSequence());
        assertEquals("EVENTS_COMPACTED", assertThrows(FlowStoreException.class,
                () -> store.readEvents("compact", 0, 10)).getCode());
        assertEquals(List.of(events.get(1)), store.readEvents("compact", 1, 10).events());

        store.compactEvents("compact", 1);
        assertEquals(2, store.get("compact").orElseThrow().revision());
    }

    @Test
    void nonTerminalExecutionCannotBeCompacted() {
        RdbmsFlowExecutionStore store = store();
        store.createOrGet("tenant", "running", snapshot("running", "request"));

        assertEquals("TXFLOW_COMPACTION_NOT_TERMINAL", assertThrows(
                FlowStoreException.class, () -> store.compactEvents("running", 0)).getCode());
    }

    @Test
    void leaseEpochsAreMonotonicAndExpiredOwnersCannotRenewOrFence() {
        RdbmsFlowExecutionStore store = store();
        store.createOrGet("tenant", "one", snapshot("one", "request"));
        store.createOrGet("tenant", "two", snapshot("two", "request"));
        ExecutionLease first = store.acquireExecutionLease(
                "one", "owner-one", NOW, Duration.ofSeconds(10));
        ExecutionLease reacquired = store.acquireExecutionLease(
                "one", "owner-one", NOW.plusSeconds(1), Duration.ofSeconds(10));
        assertTrue(reacquired.epoch() > first.epoch());
        assertEquals("TXFLOW_STALE_FENCE", assertThrows(FlowStoreException.class,
                () -> store.renewExecutionLease(first, NOW.plusSeconds(2), LEASE_DURATION))
                .getCode());
        assertEquals("TXFLOW_LEASE_CONFLICT", assertThrows(FlowStoreException.class,
                () -> store.acquireExecutionLease("one", "owner-two",
                        NOW.plusSeconds(2), LEASE_DURATION)).getCode());
        ExecutionLease takeover = store.acquireExecutionLease(
                "one", "owner-two", NOW.plusSeconds(12), LEASE_DURATION);
        assertTrue(takeover.epoch() > reacquired.epoch());

        ResourceLease resource = store.acquireResourceLease(
                "wallet", "one", "owner-two", NOW, Duration.ofSeconds(10));
        assertEquals("TXFLOW_RESOURCE_LEASE_CONFLICT", assertThrows(FlowStoreException.class,
                () -> store.acquireResourceLease("wallet", "two", "other",
                        NOW.plusSeconds(1), LEASE_DURATION)).getCode());
        ResourceLease resourceTakeover = store.acquireResourceLease(
                "wallet", "two", "other", NOW.plusSeconds(11), LEASE_DURATION);
        assertTrue(resourceTakeover.epoch() > resource.epoch());
        assertEquals("TXFLOW_STALE_RESOURCE_FENCE", assertThrows(FlowStoreException.class,
                () -> store.releaseResourceLease(resource)).getCode());
    }

    @Test
    void payloadColumnDivergenceFailsClosed() throws Exception {
        String url = "jdbc:h2:mem:corruption-" + UUID.randomUUID();
        RdbmsFlowExecutionStore store = RdbmsFlowExecutionStore.builder()
                .jdbcUrl(url).clock(fixedClock()).build();
        stores.add(store);
        store.createOrGet("tenant", "corrupt", snapshot("corrupt", "request"));
        try (Connection connection = DriverManager.getConnection(url);
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE txflow_execution SET execution_state = 'FAILED' "
                             + "WHERE execution_id = 'corrupt'")) {
            statement.executeUpdate();
        }

        assertEquals("TXFLOW_STORE_CORRUPT", assertThrows(FlowStoreException.class,
                () -> store.get("corrupt")).getCode());
    }

    private RdbmsFlowExecutionStore store() {
        RdbmsFlowExecutionStore store = RdbmsFlowExecutionStore.builder()
                .jdbcUrl("jdbc:h2:mem:txflow-" + UUID.randomUUID())
                .clock(fixedClock()).build();
        stores.add(store);
        return store;
    }

    private Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private FlowExecutionSnapshot snapshot(String executionId, String requestFingerprint) {
        return new FlowExecutionSnapshot(executionId, "definition", requestFingerprint,
                FlowExecutionState.CREATED, 0, 0, 0, NOW, Map.of());
    }

    private FlowExecutionSnapshot typedSnapshot(String executionId, String requestFingerprint) {
        byte[] cbor = new byte[]{1, 2, 3, 4};
        SignedPayload.InlineCbor payload = new SignedPayload.InlineCbor(
                cbor, "digest", "transaction-hash");
        FlowAttemptSnapshot attempt = new FlowAttemptSnapshot("step", 1,
                AttemptState.IN_BLOCK, payload, 10L, 100L, List.of("input#0"),
                List.of(new InclusionRecord(42, "block", 100, NOW, false)), NOW, null);
        PersistedBinding binding = new PersistedBinding(
                "amount", "INTEGER", 42, null, "fingerprint", "42");
        return new FlowExecutionSnapshot(executionId, "definition", requestFingerprint,
                FlowExecutionState.CREATED, 0, 0, 0, NOW,
                Map.of("attempts", Map.of("step#1", attempt),
                        "bindings", List.of(binding), "resources", Set.of("wallet")));
    }

    @SuppressWarnings("unchecked")
    private void assertTypedData(FlowExecutionSnapshot snapshot) {
        Map<String, FlowAttemptSnapshot> attempts =
                (Map<String, FlowAttemptSnapshot>) snapshot.data().get("attempts");
        FlowAttemptSnapshot attempt = attempts.get("step#1");
        assertEquals(AttemptState.IN_BLOCK, attempt.state());
        assertArrayEquals(new byte[]{1, 2, 3, 4},
                ((SignedPayload.InlineCbor) attempt.signedPayload()).cbor());
        List<PersistedBinding> bindings =
                (List<PersistedBinding>) snapshot.data().get("bindings");
        assertEquals(42, bindings.get(0).nonSensitiveValue());
        assertEquals(Set.of("wallet"), snapshot.data().get("resources"));
    }

    private FlowEvent event(String executionId, long sequence, FlowEventType type,
                            Map<String, Object> details) {
        return new FlowEvent(sequence, executionId, type, NOW.plusSeconds(sequence),
                null, null, details);
    }
}
