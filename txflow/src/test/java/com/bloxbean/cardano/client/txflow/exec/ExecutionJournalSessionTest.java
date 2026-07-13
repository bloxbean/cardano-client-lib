package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.txflow.store.FlowExecutionSnapshot;
import com.bloxbean.cardano.client.txflow.store.FlowStoreException;
import com.bloxbean.cardano.client.txflow.store.InMemoryFlowExecutionStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExecutionJournalSessionTest {
    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");

    @Test
    void advancesAnIncrementalCursorWithoutReappendingEarlierEvents() {
        Fixture fixture = fixture("cursor");

        fixture.journal.record(FlowEventType.EXECUTION_CREATED, null, null, Map.of());
        fixture.journal.record(FlowEventType.EXECUTION_STARTED, null, null, Map.of());
        fixture.journal.persist(FlowExecutionState.RUNNING, data -> data.put("phase", "started"));

        assertEquals(1, fixture.journal.revision());
        assertEquals(2, fixture.journal.persistedSequence());
        assertEquals(2, fixture.journal.persistedEventCount());

        fixture.journal.record(FlowEventType.CONFIRMATION_DEPTH_CHANGED,
                "pay", "ab".repeat(32), Map.of("depth", 2));
        fixture.journal.persist(FlowExecutionState.RUNNING, data -> data.put("phase", "confirming"));

        assertEquals(2, fixture.journal.revision());
        assertEquals(3, fixture.journal.persistedSequence());
        assertEquals(3, fixture.journal.persistedEventCount());
        assertEquals(List.of(1L, 2L, 3L), fixture.store.readEvents("cursor", 0, 10)
                .events().stream().map(FlowEvent::sequence).toList());
        assertEquals("confirming", fixture.store.get("cursor").orElseThrow().data().get("phase"));
        fixture.leases.close();
    }

    @Test
    void advancesNeitherRevisionNorCursorWhenTheFencedAppendFails() {
        Fixture fixture = fixture("conflict");
        fixture.store.append("conflict", 0, fixture.leases.fence(), List.of(),
                current -> current.withState(FlowExecutionState.RUNNING, NOW, current.data()));
        fixture.journal.record(FlowEventType.EXECUTION_STARTED, null, null, Map.of());

        FlowStoreException failure = assertThrows(FlowStoreException.class,
                () -> fixture.journal.persist(FlowExecutionState.RUNNING, data -> { }));

        assertEquals("TXFLOW_REVISION_CONFLICT", failure.getCode());
        assertEquals(0, fixture.journal.revision());
        assertEquals(0, fixture.journal.persistedSequence());
        assertEquals(0, fixture.journal.persistedEventCount());
        assertEquals(1, fixture.journal.events().size());
        assertEquals(List.of(), fixture.store.readEvents("conflict", 0, 10).events());
        fixture.leases.close();
    }

    private Fixture fixture(String executionId) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        InMemoryFlowExecutionStore store = new InMemoryFlowExecutionStore(clock);
        store.createOrGet("test", executionId, new FlowExecutionSnapshot(
                executionId, "definition", "request", FlowExecutionState.CREATED,
                0, 0, 0, NOW, Map.of()));
        DurableLeaseGuard leases = new DurableLeaseGuard(
                store, clock, Duration.ofMinutes(1), Runnable::run);
        leases.acquireExecution(executionId, "owner");
        ExecutionJournalSession journal = new ExecutionJournalSession(store, executionId, clock);
        journal.attach(leases);
        return new Fixture(store, leases, journal);
    }

    private record Fixture(InMemoryFlowExecutionStore store, DurableLeaseGuard leases,
                           ExecutionJournalSession journal) {
    }
}
