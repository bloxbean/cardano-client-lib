package com.bloxbean.cardano.client.txflow.store.contract;

import com.bloxbean.cardano.client.txflow.exec.FlowEvent;
import com.bloxbean.cardano.client.txflow.exec.FlowEventType;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.store.EventReadResult;
import com.bloxbean.cardano.client.txflow.store.ExecutionLease;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionSnapshot;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionStore;
import com.bloxbean.cardano.client.txflow.store.FlowStoreException;
import com.bloxbean.cardano.client.txflow.store.IdempotencyClaimResult;
import com.bloxbean.cardano.client.txflow.store.MutationFence;
import com.bloxbean.cardano.client.txflow.store.ResourceLease;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reusable behavioral contract for {@link FlowExecutionStore} adapters.
 *
 * <p>Concrete tests provide a fresh store for the supplied clock. The same
 * tests are run against the in-memory reference and every certified durable
 * adapter.</p>
 */
public abstract class FlowExecutionStoreContract {
    /** Fixed starting point used by the contract. */
    protected static final Instant NOW = Instant.parse("2026-07-14T00:00:00Z");

    protected AdjustableClock clock;
    protected FlowExecutionStore store;

    /** Creates a fresh empty store whose expiry checks use the supplied clock. */
    protected abstract FlowExecutionStore createStore(AdjustableClock clock) throws Exception;

    @BeforeEach
    void openStore() throws Exception {
        clock = new AdjustableClock(NOW);
        store = createStore(clock);
    }

    @AfterEach
    void closeStore() throws Exception {
        if (store instanceof AutoCloseable) ((AutoCloseable) store).close();
    }

    @Test
    void createOrGetIsIdempotentAndRejectsIdentityConflicts() {
        FlowExecutionSnapshot initial = snapshot("execution", "definition", "request");

        IdempotencyClaimResult created = store.createOrGet("tenant", "operation", initial);
        IdempotencyClaimResult existing = store.createOrGet("tenant", "operation", initial);

        assertTrue(created.created());
        assertFalse(existing.created());
        assertEquals(initial, existing.snapshot());
        assertCode("TXFLOW_IDEMPOTENCY_CONFLICT", () -> store.createOrGet(
                "tenant", "operation", snapshot("other", "definition", "different")));
        assertCode("TXFLOW_EXECUTION_ID_CONFLICT", () -> store.createOrGet(
                "other-tenant", "other-operation",
                snapshot("execution", "definition", "request")));
    }

    @Test
    void storeIdentitiesRejectDatabaseIncompatibleNul() {
        FlowExecutionSnapshot initial = snapshot(
                "invalid-claim", "definition", "request");

        assertThrows(IllegalArgumentException.class,
                () -> store.createOrGet("a\u0000b", "c", initial));
        assertThrows(IllegalArgumentException.class,
                () -> store.createOrGet("a", "b\u0000c", initial));
        assertThrows(IllegalArgumentException.class,
                () -> store.get("invalid\u0000execution"));
        assertThrows(IllegalArgumentException.class,
                () -> store.acquireExecutionLease(
                        "invalid-claim", "invalid\u0000owner", NOW, Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class,
                () -> store.acquireResourceLease(
                        "invalid\u0000resource", "invalid-claim", "owner", NOW,
                        Duration.ofMinutes(1)));
    }

    @Test
    void persistedIdentityLimitsAreMeasuredInUtf8Bytes() {
        FlowExecutionSnapshot initial = snapshot(
                "portable-limits", "definition", "request");
        store.createOrGet("tenant", "portable-limits", initial);

        assertThrows(IllegalArgumentException.class, () -> store.createOrGet(
                "é".repeat(128), "key", snapshot(
                        "namespace-too-large", "definition", "request")));
        assertThrows(IllegalArgumentException.class, () -> store.createOrGet(
                "tenant", "k".repeat(513), snapshot(
                        "key-too-large", "definition", "request")));
        assertThrows(IllegalArgumentException.class, () -> store.acquireExecutionLease(
                "portable-limits", "o".repeat(513), NOW, Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class, () -> store.acquireResourceLease(
                "r".repeat(1025), "portable-limits", "owner", NOW,
                Duration.ofMinutes(1)));
    }

    @Test
    @Timeout(15)
    void concurrentCreateOrGetHasExactlyOneWinner() throws Exception {
        int callers = 12;
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<IdempotencyClaimResult>> calls = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            calls.add(() -> {
                ready.countDown();
                start.await();
                return store.createOrGet("tenant", "race",
                        snapshot("race-execution", "definition", "request"));
            });
        }
        try {
            List<Future<IdempotencyClaimResult>> futures = calls.stream()
                    .map(executor::submit).toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS), "claim workers did not become ready");
            start.countDown();
            List<IdempotencyClaimResult> results = new ArrayList<>();
            for (Future<IdempotencyClaimResult> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }

            assertEquals(1, results.stream().filter(IdempotencyClaimResult::created).count());
            assertTrue(results.stream().allMatch(result ->
                    "race-execution".equals(result.snapshot().executionId())));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @Timeout(15)
    void concurrentAppendHasExactlyOneRevisionWinner() throws Exception {
        store.createOrGet("tenant", "append-race",
                snapshot("append-race", "definition", "request"));
        ExecutionLease lease = store.acquireExecutionLease(
                "append-race", "owner", NOW, Duration.ofMinutes(1));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> append = () -> {
            ready.countDown();
            start.await();
            try {
                store.append("append-race", 0, MutationFence.executionOnly(lease),
                        List.of(event(1, "append-race", FlowEventType.EXECUTION_STARTED)),
                        current -> current.withState(FlowExecutionState.RUNNING,
                                NOW.plusSeconds(1), Map.of("winner", true)));
                return "SUCCESS";
            } catch (FlowStoreException failure) {
                return failure.getCode();
            }
        };
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> futures = List.of(
                    executor.submit(append), executor.submit(append));
            assertTrue(ready.await(5, TimeUnit.SECONDS),
                    "append workers did not become ready");
            start.countDown();
            List<String> outcomes = List.of(
                    futures.get(0).get(10, TimeUnit.SECONDS),
                    futures.get(1).get(10, TimeUnit.SECONDS));

            assertEquals(1, outcomes.stream().filter("SUCCESS"::equals).count());
            assertEquals(1, outcomes.stream()
                    .filter("TXFLOW_REVISION_CONFLICT"::equals).count());
            assertEquals(1, store.get("append-race").orElseThrow().revision());
            assertEquals(1, store.readEvents("append-race", 0, 10).events().size());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void appendCommitsEventsAndSnapshotAsOneRevision() {
        store.createOrGet("tenant", "append", snapshot("append", "definition", "request"));
        ExecutionLease lease = store.acquireExecutionLease(
                "append", "owner", NOW, Duration.ofMinutes(1));
        FlowEvent event = event(1, "append", FlowEventType.EXECUTION_STARTED);

        FlowExecutionSnapshot updated = store.append("append", 0,
                MutationFence.executionOnly(lease), List.of(event),
                current -> current.withState(FlowExecutionState.RUNNING, NOW.plusSeconds(1),
                        Map.of("step_count", 0)));

        assertEquals(1, updated.revision());
        assertEquals(1, updated.lastSequence());
        assertEquals(FlowExecutionState.RUNNING, updated.state());
        assertEquals(updated, store.get("append").orElseThrow());
        assertEquals(List.of(event), store.readEvents("append", 0, 10).events());
    }

    @Test
    void failedMutationRollsBackEventAndSnapshotTogether() {
        store.createOrGet("tenant", "rollback", snapshot("rollback", "definition", "request"));
        ExecutionLease lease = store.acquireExecutionLease(
                "rollback", "owner", NOW, Duration.ofMinutes(1));

        assertThrows(IllegalStateException.class, () -> store.append("rollback", 0,
                MutationFence.executionOnly(lease),
                List.of(event(1, "rollback", FlowEventType.EXECUTION_STARTED)), current -> {
                    throw new IllegalStateException("mutation failed");
                }));

        FlowExecutionSnapshot unchanged = store.get("rollback").orElseThrow();
        assertEquals(0, unchanged.revision());
        assertEquals(0, unchanged.lastSequence());
        assertTrue(store.readEvents("rollback", 0, 10).events().isEmpty());
    }

    @Test
    void appendRejectsStaleRevisionEventSequenceAndFence() {
        store.createOrGet("tenant", "fence", snapshot("fence", "definition", "request"));
        ExecutionLease first = store.acquireExecutionLease(
                "fence", "owner-a", NOW, Duration.ofSeconds(10));
        clock.advance(Duration.ofSeconds(11));
        ExecutionLease successor = store.acquireExecutionLease(
                "fence", "owner-b", clock.instant(), Duration.ofMinutes(1));

        assertCode("TXFLOW_STALE_FENCE", () -> store.append("fence", 0,
                MutationFence.executionOnly(first), List.of(), current -> current));
        assertCode("TXFLOW_EVENT_SEQUENCE", () -> store.append("fence", 0,
                MutationFence.executionOnly(successor),
                List.of(event(2, "fence", FlowEventType.EXECUTION_STARTED)), current -> current));
        store.append("fence", 0, MutationFence.executionOnly(successor),
                List.of(event(1, "fence", FlowEventType.EXECUTION_STARTED)), current -> current);
        assertCode("TXFLOW_REVISION_CONFLICT", () -> store.append("fence", 0,
                MutationFence.executionOnly(successor), List.of(), current -> current));
    }

    @Test
    void leaseRenewalRetainsEpochAndReleaseRejectsStaleOwners() {
        store.createOrGet("tenant", "leases", snapshot("leases", "definition", "request"));
        ExecutionLease acquired = store.acquireExecutionLease(
                "leases", "owner", NOW, Duration.ofMinutes(1));
        ExecutionLease renewed = store.renewExecutionLease(
                acquired, NOW.plusSeconds(10), Duration.ofMinutes(1));

        assertEquals(acquired.epoch(), renewed.epoch());
        assertTrue(renewed.expiresAt().isAfter(acquired.expiresAt()));
        store.releaseExecutionLease(renewed);
        ExecutionLease successor = store.acquireExecutionLease(
                "leases", "other", NOW.plusSeconds(11), Duration.ofMinutes(1));
        assertTrue(successor.epoch() > renewed.epoch());
        assertCode("TXFLOW_STALE_FENCE", () -> store.releaseExecutionLease(acquired));
        store.releaseExecutionLease(successor);
    }

    @Test
    void executionLeaseConflictExpiryAndTakeoverAreFenced() {
        store.createOrGet("tenant", "execution-lease-matrix",
                snapshot("execution-lease-matrix", "definition", "request"));
        ExecutionLease first = store.acquireExecutionLease(
                "execution-lease-matrix", "owner-a", NOW, Duration.ofSeconds(10));

        assertCode("TXFLOW_LEASE_CONFLICT", () -> store.acquireExecutionLease(
                "execution-lease-matrix", "owner-b", NOW.plusSeconds(1),
                Duration.ofMinutes(1)));
        clock.advance(Duration.ofSeconds(11));
        assertCode("TXFLOW_LEASE_EXPIRED", () -> store.renewExecutionLease(
                first, clock.instant(), Duration.ofMinutes(1)));

        ExecutionLease successor = store.acquireExecutionLease(
                "execution-lease-matrix", "owner-b", clock.instant(),
                Duration.ofMinutes(1));
        assertTrue(successor.epoch() > first.epoch());
        assertCode("TXFLOW_STALE_FENCE", () -> store.renewExecutionLease(
                first, clock.instant(), Duration.ofMinutes(1)));
        assertCode("TXFLOW_STALE_FENCE", () -> store.releaseExecutionLease(first));
        assertCode("TXFLOW_STALE_FENCE", () -> store.append(
                "execution-lease-matrix", 0, MutationFence.executionOnly(first),
                List.of(), current -> current));
    }

    @Test
    void resourceLeaseCannotBeStolenByAnotherOwnerOfTheSameExecution() {
        store.createOrGet("tenant", "resources",
                snapshot("resources", "definition", "request"));
        ResourceLease lease = store.acquireResourceLease(
                "wallet", "resources", "owner-a", NOW, Duration.ofMinutes(1));

        assertCode("TXFLOW_RESOURCE_LEASE_CONFLICT", () -> store.acquireResourceLease(
                "wallet", "resources", "owner-b", NOW.plusSeconds(1), Duration.ofMinutes(1)));
        ResourceLease renewed = store.renewResourceLease(
                lease, NOW.plusSeconds(2), Duration.ofMinutes(1));
        store.releaseResourceLease(renewed);
    }

    @Test
    void resourceLeaseRequiresAnExistingExecution() {
        assertCode("TXFLOW_EXECUTION_NOT_FOUND", () -> store.acquireResourceLease(
                "wallet", "missing-execution", "owner", NOW, Duration.ofMinutes(1)));
    }

    @Test
    void mutationFenceRejectsDuplicateResourceIdentitiesAtConstruction() {
        store.createOrGet("tenant", "duplicate-fence",
                snapshot("duplicate-fence", "definition", "request"));
        ExecutionLease execution = store.acquireExecutionLease(
                "duplicate-fence", "owner", NOW, Duration.ofMinutes(1));
        ResourceLease resource = store.acquireResourceLease(
                "wallet", "duplicate-fence", "owner", NOW, Duration.ofMinutes(1));

        assertThrows(IllegalArgumentException.class,
                () -> new MutationFence(execution, List.of(resource, resource)));
    }

    @Test
    void resourceLeaseConflictExpiryTakeoverAndMutationFenceAreEnforced() {
        store.createOrGet("tenant", "resource-one",
                snapshot("resource-one", "definition", "request"));
        store.createOrGet("tenant", "resource-two",
                snapshot("resource-two", "definition", "request"));
        ResourceLease first = store.acquireResourceLease(
                "wallet", "resource-one", "owner-one", NOW, Duration.ofSeconds(10));

        assertCode("TXFLOW_RESOURCE_LEASE_CONFLICT", () -> store.acquireResourceLease(
                "wallet", "resource-two", "owner-two", NOW.plusSeconds(1),
                Duration.ofMinutes(1)));
        clock.advance(Duration.ofSeconds(11));
        assertCode("TXFLOW_RESOURCE_LEASE_EXPIRED", () -> store.renewResourceLease(
                first, clock.instant(), Duration.ofMinutes(1)));

        ResourceLease successor = store.acquireResourceLease(
                "wallet", "resource-two", "owner-two", clock.instant(),
                Duration.ofMinutes(1));
        assertTrue(successor.epoch() > first.epoch());
        assertCode("TXFLOW_STALE_RESOURCE_FENCE", () -> store.renewResourceLease(
                first, clock.instant(), Duration.ofMinutes(1)));
        assertCode("TXFLOW_STALE_RESOURCE_FENCE", () -> store.releaseResourceLease(first));

        ExecutionLease execution = store.acquireExecutionLease(
                "resource-two", "owner-two", clock.instant(), Duration.ofMinutes(1));
        ResourceLease foreign = store.acquireResourceLease(
                "foreign-wallet", "resource-one", "owner-one", clock.instant(),
                Duration.ofMinutes(1));
        assertCode("TXFLOW_STALE_RESOURCE_FENCE", () -> store.append(
                "resource-two", 0, new MutationFence(execution, List.of(successor, foreign)),
                List.of(), current -> current));

        FlowEvent started = event(1, "resource-two", FlowEventType.EXECUTION_STARTED);
        FlowExecutionSnapshot updated = store.append(
                "resource-two", 0, new MutationFence(execution, List.of(successor)),
                List.of(started), current -> current.withState(
                        FlowExecutionState.RUNNING, clock.instant(), Map.of()));
        assertEquals(1, updated.revision());
        assertEquals(List.of(started), store.readEvents("resource-two", 0, 10).events());
    }

    @Test
    void eventPagingAndCompactionUseMonotonicCursors() {
        store.createOrGet("tenant", "events", snapshot("events", "definition", "request"));
        ExecutionLease lease = store.acquireExecutionLease(
                "events", "owner", NOW, Duration.ofMinutes(1));
        List<FlowEvent> events = List.of(
                event(1, "events", FlowEventType.EXECUTION_STARTED),
                event(2, "events", FlowEventType.EXECUTION_COMPLETED));
        store.append("events", 0, MutationFence.executionOnly(lease), events,
                current -> current.withState(FlowExecutionState.COMPLETED,
                        NOW.plusSeconds(1), Map.of()));

        EventReadResult firstPage = store.readEvents("events", 0, 1);
        assertEquals(List.of(events.get(0)), firstPage.events());
        assertEquals(1, firstPage.nextSequence());
        store.compactEvents("events", 1);
        store.compactEvents("events", 1);

        assertCode("EVENTS_COMPACTED", () -> store.readEvents("events", 0, 10));
        assertEquals(List.of(events.get(1)), store.readEvents("events", 1, 10).events());
        assertEquals(1, store.get("events").orElseThrow().compactedThroughSequence());
    }

    private FlowExecutionSnapshot snapshot(String id, String definition, String request) {
        return new FlowExecutionSnapshot(id, definition, request, FlowExecutionState.CREATED,
                0, 0, 0, NOW, Map.of());
    }

    private FlowEvent event(long sequence, String executionId, FlowEventType type) {
        return new FlowEvent(sequence, executionId, type, NOW.plusSeconds(sequence),
                null, null, Map.of("sequence", sequence));
    }

    private void assertCode(String code, Runnable operation) {
        FlowStoreException failure = assertThrows(FlowStoreException.class, operation::run);
        assertEquals(code, failure.getCode());
    }
}
