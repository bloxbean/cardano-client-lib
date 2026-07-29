package com.bloxbean.cardano.client.txflow.store;

import com.bloxbean.cardano.client.txflow.exec.FlowEvent;
import com.bloxbean.cardano.client.txflow.exec.FlowEventType;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryFlowExecutionStoreTest {
    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");

    @Test
    void atomicIdempotencyClaimReturnsExistingAndRejectsMismatch() {
        InMemoryFlowExecutionStore store = store();
        IdempotencyClaimResult created = store.createOrGet("tenant", "key", snapshot("one", "d1", "r1"));
        IdempotencyClaimResult existing = store.createOrGet("tenant", "key", snapshot("two", "d1", "r1"));

        assertTrue(created.created());
        assertFalse(existing.created());
        assertEquals("one", existing.snapshot().executionId());
        FlowStoreException conflict = assertThrows(FlowStoreException.class,
                () -> store.createOrGet("tenant", "key", snapshot("three", "d1", "different")));
        assertEquals("TXFLOW_IDEMPOTENCY_CONFLICT", conflict.getCode());
    }

    @Test
    void appendRequiresRevisionAndCurrentExecutionAndResourceFences() {
        InMemoryFlowExecutionStore store = store();
        store.createOrGet("tenant", "key", snapshot("one", "d", "r"));
        ExecutionLease execution = store.acquireExecutionLease("one", "worker", NOW, Duration.ofMinutes(1));
        ResourceLease resource = store.acquireResourceLease("treasury", "one", "worker", NOW, Duration.ofMinutes(1));
        FlowEvent event = new FlowEvent(1, "one", FlowEventType.EXECUTION_STARTED,
                NOW, null, null, Map.of());

        FlowExecutionSnapshot updated = store.append("one", 0,
                new MutationFence(execution, List.of(resource)), List.of(event),
                current -> current.withState(FlowExecutionState.RUNNING, NOW, Map.of()));
        assertEquals(1, updated.revision());
        assertEquals(1, updated.lastSequence());

        assertEquals("TXFLOW_REVISION_CONFLICT", assertThrows(FlowStoreException.class,
                () -> store.append("one", 0, MutationFence.executionOnly(execution), List.of(), value -> value))
                .getCode());

        store.releaseExecutionLease(execution);
        assertEquals("TXFLOW_STALE_FENCE", assertThrows(FlowStoreException.class,
                () -> store.append("one", 1, MutationFence.executionOnly(execution), List.of(), value -> value))
                .getCode());
    }

    @Test
    void appendBindsEveryFenceAndEventToTheTargetExecutionOwner() {
        InMemoryFlowExecutionStore store = store();
        FlowExecutionSnapshot initial = snapshot("two", "d", "r");
        store.createOrGet("tenant", "one-key", snapshot("one", "d", "r"));
        store.createOrGet("tenant", "two-key", initial);
        ExecutionLease firstExecution = store.acquireExecutionLease(
                "one", "worker-a", NOW, Duration.ofMinutes(1));
        ExecutionLease secondExecution = store.acquireExecutionLease(
                "two", "worker-b", NOW, Duration.ofMinutes(1));
        ResourceLease wrongExecution = store.acquireResourceLease(
                "first-resource", "one", "worker-a", NOW, Duration.ofMinutes(1));
        ResourceLease wrongOwner = store.acquireResourceLease(
                "second-resource", "two", "worker-a", NOW, Duration.ofMinutes(1));
        FlowEvent wrongEvent = new FlowEvent(1, "one", FlowEventType.EXECUTION_STARTED,
                NOW, null, null, Map.of());

        FlowStoreException executionMismatch = assertThrows(FlowStoreException.class,
                () -> store.append("two", 0, MutationFence.executionOnly(firstExecution),
                        List.of(), current -> current));
        assertEquals("TXFLOW_STALE_FENCE", executionMismatch.getCode());
        FlowStoreException resourceExecutionMismatch = assertThrows(FlowStoreException.class,
                () -> store.append("two", 0,
                        new MutationFence(secondExecution, List.of(wrongExecution)),
                        List.of(), current -> current));
        assertEquals("TXFLOW_STALE_RESOURCE_FENCE", resourceExecutionMismatch.getCode());
        FlowStoreException resourceOwnerMismatch = assertThrows(FlowStoreException.class,
                () -> store.append("two", 0,
                        new MutationFence(secondExecution, List.of(wrongOwner)),
                        List.of(), current -> current));
        assertEquals("TXFLOW_STALE_RESOURCE_FENCE", resourceOwnerMismatch.getCode());
        FlowStoreException eventMismatch = assertThrows(FlowStoreException.class,
                () -> store.append("two", 0, MutationFence.executionOnly(secondExecution),
                        List.of(wrongEvent), current -> current));
        assertEquals("TXFLOW_EVENT_SEQUENCE", eventMismatch.getCode());

        assertEquals(initial, store.get("two").orElseThrow());
        EventReadResult journal = store.readEvents("two", 0, 10);
        assertTrue(journal.events().isEmpty());
        assertEquals(0, journal.nextSequence());
    }

    @Test
    void appendIsAtomicWhenSnapshotMutationThrows() {
        InMemoryFlowExecutionStore store = store();
        FlowExecutionSnapshot initial = snapshot("one", "d", "r");
        store.createOrGet("tenant", "key", initial);
        ExecutionLease lease = store.acquireExecutionLease(
                "one", "worker", NOW, Duration.ofMinutes(1));
        FlowEvent event = new FlowEvent(1, "one", FlowEventType.EXECUTION_STARTED,
                NOW, null, null, Map.of());
        IllegalStateException failure = new IllegalStateException("mutation failed");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> store.append("one", 0, MutationFence.executionOnly(lease), List.of(event),
                        current -> {
                            throw failure;
                        }));

        assertSame(failure, thrown);
        assertEquals(initial, store.get("one").orElseThrow());
        EventReadResult emptyJournal = store.readEvents("one", 0, 10);
        assertTrue(emptyJournal.events().isEmpty());
        assertEquals(0, emptyJournal.nextSequence());

        FlowExecutionSnapshot retried = store.append("one", 0,
                MutationFence.executionOnly(lease), List.of(event),
                current -> current.withState(FlowExecutionState.RUNNING, NOW, Map.of()));
        assertEquals(1, retried.revision());
        assertEquals(1, retried.lastSequence());
        assertEquals(List.of(event), store.readEvents("one", 0, 10).events());
    }

    @Test
    void eventCursorReportsCompaction() {
        InMemoryFlowExecutionStore store = store();
        store.createOrGet("tenant", "key", snapshot("one", "d", "r"));
        ExecutionLease lease = store.acquireExecutionLease("one", "worker", NOW, Duration.ofMinutes(1));
        List<FlowEvent> events = List.of(
                new FlowEvent(1, "one", FlowEventType.EXECUTION_STARTED, NOW, null, null, Map.of()),
                new FlowEvent(2, "one", FlowEventType.EXECUTION_COMPLETED, NOW, null, null, Map.of()));
        store.append("one", 0, MutationFence.executionOnly(lease), events,
                value -> value.withState(FlowExecutionState.COMPLETED, NOW, Map.of()));
        assertEquals(2, store.readEvents("one", 0, 10).events().size());

        store.compactEvents("one", 1);
        FlowStoreException compacted = assertThrows(FlowStoreException.class,
                () -> store.readEvents("one", 0, 10));
        assertEquals("EVENTS_COMPACTED", compacted.getCode());
        assertEquals(1, store.readEvents("one", 1, 10).events().size());
    }

    @Test
    void compactionWatermarkNeverRegressesOrSilentlySkipsEvents() {
        InMemoryFlowExecutionStore store = store();
        store.createOrGet("tenant", "key", snapshot("one", "d", "r"));
        ExecutionLease lease = store.acquireExecutionLease(
                "one", "worker", NOW, Duration.ofMinutes(1));
        FlowEvent first = new FlowEvent(1, "one", FlowEventType.EXECUTION_STARTED,
                NOW, null, null, Map.of());
        FlowEvent second = new FlowEvent(2, "one", FlowEventType.STEP_STARTED,
                NOW, "step-1", null, Map.of());
        FlowEvent third = new FlowEvent(3, "one", FlowEventType.EXECUTION_COMPLETED,
                NOW, null, null, Map.of());
        store.append("one", 0, MutationFence.executionOnly(lease),
                List.of(first, second, third),
                current -> current.withState(FlowExecutionState.COMPLETED, NOW, Map.of()));

        store.compactEvents("one", 2);
        store.compactEvents("one", 1);

        assertEquals(2, store.get("one").orElseThrow().compactedThroughSequence());
        FlowStoreException compacted = assertThrows(FlowStoreException.class,
                () -> store.readEvents("one", 1, 10));
        assertEquals("EVENTS_COMPACTED", compacted.getCode());
        assertEquals(List.of(third), store.readEvents("one", 2, 10).events());
    }

    @Test
    void resourceLeaseSerializesExecutionsAndCanBeRenewed() {
        InMemoryFlowExecutionStore store = store();
        store.createOrGet("tenant", "one", snapshot("one", "d", "r"));
        store.createOrGet("tenant", "two", snapshot("two", "d", "r"));
        ResourceLease first = store.acquireResourceLease(
                "treasury", "one", "worker-a", NOW, Duration.ofMinutes(1));
        assertEquals("TXFLOW_RESOURCE_LEASE_CONFLICT", assertThrows(FlowStoreException.class,
                () -> store.acquireResourceLease("treasury", "two", "worker-b", NOW, Duration.ofMinutes(1)))
                .getCode());
        ResourceLease renewed = store.renewResourceLease(first, NOW.plusSeconds(10), Duration.ofMinutes(1));
        assertEquals(first.epoch(), renewed.epoch());
        store.releaseResourceLease(renewed);
        assertEquals("two", store.acquireResourceLease(
                "treasury", "two", "worker-b", NOW, Duration.ofMinutes(1)).executionId());
    }

    @Test
    void expiredExecutionLeaseCannotBeRenewedOrUsedAsFence() {
        InMemoryFlowExecutionStore store = store();
        store.createOrGet("tenant", "key", snapshot("one", "d", "r"));
        Duration duration = Duration.ofMinutes(1);
        ExecutionLease expired = store.acquireExecutionLease(
                "one", "worker", NOW.minus(duration), duration);
        ExecutionLease forgedLaterExpiry = new ExecutionLease(expired.executionId(),
                expired.ownerToken(), expired.epoch(), NOW.plus(duration));

        FlowStoreException renewal = assertThrows(FlowStoreException.class,
                () -> store.renewExecutionLease(forgedLaterExpiry, NOW, duration));
        assertEquals("TXFLOW_LEASE_EXPIRED", renewal.getCode());
        FlowStoreException append = assertThrows(FlowStoreException.class,
                () -> store.append("one", 0, MutationFence.executionOnly(forgedLaterExpiry),
                        List.of(), current -> current));
        assertEquals("TXFLOW_LEASE_EXPIRED", append.getCode());

        ExecutionLease replacement = store.acquireExecutionLease(
                "one", "worker", NOW, duration);
        assertTrue(replacement.epoch() > expired.epoch());
        FlowExecutionSnapshot updated = store.append("one", 0,
                MutationFence.executionOnly(replacement), List.of(), current -> current);
        assertEquals(1, updated.revision());
    }

    @Test
    void expiredResourceLeaseCannotBeRenewedOrUsedAsFence() {
        InMemoryFlowExecutionStore store = store();
        store.createOrGet("tenant", "key", snapshot("one", "d", "r"));
        Duration duration = Duration.ofMinutes(1);
        ExecutionLease execution = store.acquireExecutionLease(
                "one", "worker", NOW, duration);
        ResourceLease expired = store.acquireResourceLease(
                "treasury", "one", "worker", NOW.minus(duration), duration);
        ResourceLease forgedLaterExpiry = new ResourceLease(expired.resourceId(),
                expired.executionId(), expired.ownerToken(), expired.epoch(), NOW.plus(duration));

        FlowStoreException renewal = assertThrows(FlowStoreException.class,
                () -> store.renewResourceLease(forgedLaterExpiry, NOW, duration));
        assertEquals("TXFLOW_RESOURCE_LEASE_EXPIRED", renewal.getCode());
        FlowStoreException append = assertThrows(FlowStoreException.class,
                () -> store.append("one", 0,
                        new MutationFence(execution, List.of(forgedLaterExpiry)),
                        List.of(), current -> current));
        assertEquals("TXFLOW_RESOURCE_LEASE_EXPIRED", append.getCode());

        ResourceLease replacement = store.acquireResourceLease(
                "treasury", "one", "worker", NOW, duration);
        assertTrue(replacement.epoch() > expired.epoch());
        FlowExecutionSnapshot updated = store.append("one", 0,
                new MutationFence(execution, List.of(replacement)),
                List.of(), current -> current);
        assertEquals(1, updated.revision());
    }

    @Test
    void concurrentIdempotencyClaimRaceHasExactlyOneWinner() {
        InMemoryFlowExecutionStore store = store();
        List<IdempotencyClaimResult> results = IntStream.range(0, 24)
                .mapToObj(index -> CompletableFuture.supplyAsync(() -> store.createOrGet(
                        "tenant", "raced-key", snapshot("execution-" + index, "d", "r"))))
                .toList().stream().map(CompletableFuture::join).toList();
        assertEquals(1, results.stream().filter(IdempotencyClaimResult::created).count());
        assertEquals(1, results.stream().map(result -> result.snapshot().executionId())
                .distinct().count());
    }

    private InMemoryFlowExecutionStore store() {
        return new InMemoryFlowExecutionStore(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private FlowExecutionSnapshot snapshot(String id, String definition, String request) {
        return new FlowExecutionSnapshot(id, definition, request, FlowExecutionState.CREATED,
                0, 0, 0, NOW, Map.of());
    }
}
