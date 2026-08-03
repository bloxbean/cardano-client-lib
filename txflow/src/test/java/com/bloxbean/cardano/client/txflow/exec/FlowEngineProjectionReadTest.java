package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.txflow.store.ExecutionLease;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionSnapshot;
import com.bloxbean.cardano.client.txflow.store.InMemoryFlowExecutionStore;
import com.bloxbean.cardano.client.txflow.store.MutationFence;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Covers the ADR 0004 engine prerequisites: the non-cancelling
 * {@code FlowExecutionHandle.completion()} stage (P1), the compaction-safe
 * {@code executionSnapshot}/{@code executionEvents} projection reads (P2), and the
 * durability capability probe {@code capabilities().durableExecution()} (P5).
 */
class FlowEngineProjectionReadTest {
    private static final Instant NOW = Instant.parse("2026-07-18T00:00:00Z");

    // ---- P5: capabilities().durableExecution() ----

    @Test
    void capabilitiesReportDurableExecutionOnlyWhenAStoreIsConfigured() {
        assertFalse(engineBuilder().build().capabilities().durableExecution(),
                "an engine without a store is not durable");
        assertTrue(durableEngine(new InMemoryFlowExecutionStore(fixedClock()))
                        .capabilities().durableExecution(),
                "an engine with a store reports durable execution");
    }

    // ---- P1: FlowExecutionHandle.completion() ----

    @Test
    void completionStageCompletesWithTheTerminalResult() {
        CompletableFuture<FlowExecutionResult> source = new CompletableFuture<>();
        FlowExecutionHandle handle = handle(source);
        CompletableFuture<FlowExecutionResult> observed =
                handle.completion().toCompletableFuture();

        assertFalse(observed.isDone());
        FlowExecutionResult result = result("exec-1");
        source.complete(result);
        assertEquals(result, observed.join());
    }

    @Test
    void completionStageIsAViewThatCannotAffectTheExecutionOrOtherObservers() {
        CompletableFuture<FlowExecutionResult> source = new CompletableFuture<>();
        FlowExecutionHandle handle = handle(source);

        CompletableFuture<FlowExecutionResult> hostile =
                handle.completion().toCompletableFuture();
        // Minimal stages reject caller-side completion outright, or hand out a
        // detached future; either way the source and other observers are
        // untouched by anything done to the returned stage.
        try {
            hostile.cancel(true);
        } catch (UnsupportedOperationException expected) {
            // acceptable: the stage refuses caller-side completion entirely
        }
        try {
            hostile.complete(result("forged"));
        } catch (UnsupportedOperationException expected) {
            // acceptable for the same reason
        }

        assertFalse(source.isDone(), "execution future must not observe stage-side completion");
        assertFalse(handle.isDone());

        FlowExecutionResult real = result("exec-1");
        source.complete(real);
        assertEquals(real, handle.completion().toCompletableFuture().join());
        assertEquals(real, handle.await());
    }

    @Test
    void completionStagePropagatesExceptionalCompletion() {
        CompletableFuture<FlowExecutionResult> source = new CompletableFuture<>();
        FlowExecutionHandle handle = handle(source);
        CompletableFuture<FlowExecutionResult> observed =
                handle.completion().toCompletableFuture();

        source.completeExceptionally(new IllegalStateException("boom"));
        assertThrows(Exception.class, observed::join);
        assertTrue(handle.isDone());
    }

    // ---- P2: executionSnapshot / executionEvents ----

    @Test
    void projectionReadsAreEmptyOnAnEngineWithoutADurableStore() {
        FlowEngine engine = engineBuilder().build();
        assertTrue(engine.executionSnapshot("exec-1").isEmpty());
        assertTrue(engine.executionEvents("exec-1", 0, 10).isEmpty());
    }

    @Test
    void projectionReadsAreEmptyForAnUnknownExecution() {
        FlowEngine engine = durableEngine(new InMemoryFlowExecutionStore(fixedClock()));
        assertTrue(engine.executionSnapshot("missing").isEmpty());
        assertTrue(engine.executionEvents("missing", 0, 10).isEmpty());
    }

    @Test
    void projectionReadsValidateArguments() {
        FlowEngine engine = durableEngine(new InMemoryFlowExecutionStore(fixedClock()));
        assertThrows(IllegalArgumentException.class, () -> engine.executionSnapshot(" "));
        assertThrows(IllegalArgumentException.class, () -> engine.executionEvents("x", -1, 10));
        assertThrows(IllegalArgumentException.class, () -> engine.executionEvents("x", 0, 0));
    }

    @Test
    void executionEventsReturnsTheBaselineAndOrderedTailWithoutRebaselining() {
        InMemoryFlowExecutionStore store = storeWithThreeEvents("exec-1");
        FlowEngine engine = durableEngine(store);

        ExecutionEventView view = engine.executionEvents("exec-1", 0, 10).orElseThrow();
        assertFalse(view.rebaselined());
        assertEquals("exec-1", view.baseline().executionId());
        assertEquals(List.of(1L, 2L, 3L),
                view.events().stream().map(FlowEvent::sequence).toList());
        assertEquals(3, view.nextSequence(), "nextSequence is the cursor for the next read");

        ExecutionEventView tail = engine.executionEvents("exec-1", 2, 10).orElseThrow();
        assertFalse(tail.rebaselined());
        assertEquals(List.of(3L), tail.events().stream().map(FlowEvent::sequence).toList());
    }

    @Test
    void executionEventsRebaselinesInsteadOfFailingWhenTheCursorWasCompacted() {
        InMemoryFlowExecutionStore store = storeWithThreeEvents("exec-1");
        store.compactEvents("exec-1", 2);
        FlowEngine engine = durableEngine(store);

        ExecutionEventView view = engine.executionEvents("exec-1", 0, 10).orElseThrow();
        assertTrue(view.rebaselined(), "cursor below the watermark must trigger re-baselining");
        assertEquals(2, view.baseline().compactedThroughSequence());
        assertEquals(List.of(3L), view.events().stream().map(FlowEvent::sequence).toList());

        // A cursor at or past the watermark reads normally.
        ExecutionEventView atWatermark = engine.executionEvents("exec-1", 2, 10).orElseThrow();
        assertFalse(atWatermark.rebaselined());
        assertEquals(List.of(3L),
                atWatermark.events().stream().map(FlowEvent::sequence).toList());
    }

    @Test
    void executionEventsHonorsTheReadLimit() {
        InMemoryFlowExecutionStore store = storeWithThreeEvents("exec-1");
        FlowEngine engine = durableEngine(store);

        ExecutionEventView page = engine.executionEvents("exec-1", 0, 2).orElseThrow();
        assertEquals(List.of(1L, 2L), page.events().stream().map(FlowEvent::sequence).toList());
        ExecutionEventView next = engine
                .executionEvents("exec-1", page.nextSequence(), 2).orElseThrow();
        assertEquals(List.of(3L), next.events().stream().map(FlowEvent::sequence).toList());
    }

    // ---- helpers ----

    private FlowExecutionHandle handle(CompletableFuture<FlowExecutionResult> completion) {
        return new FlowExecutionHandle("exec-1", completion, new AtomicBoolean(),
                Collections.synchronizedList(new ArrayList<>()));
    }

    private FlowExecutionResult result(String executionId) {
        return new FlowExecutionResult(executionId, "fp", FlowExecutionState.COMPLETED,
                List.of(), null, NOW, NOW);
    }

    private InMemoryFlowExecutionStore storeWithThreeEvents(String executionId) {
        InMemoryFlowExecutionStore store = new InMemoryFlowExecutionStore(fixedClock());
        store.createOrGet("tenant", "key", new FlowExecutionSnapshot(executionId, "d", "r",
                FlowExecutionState.CREATED, 0, 0, 0, NOW, Map.of()));
        ExecutionLease lease = store.acquireExecutionLease(
                executionId, "worker", NOW, Duration.ofMinutes(1));
        List<FlowEvent> events = List.of(
                new FlowEvent(1, executionId, FlowEventType.EXECUTION_STARTED, NOW, null, null, Map.of()),
                new FlowEvent(2, executionId, FlowEventType.STEP_STARTED, NOW, "step-1", null, Map.of()),
                new FlowEvent(3, executionId, FlowEventType.EXECUTION_COMPLETED, NOW, null, null, Map.of()));
        store.append(executionId, 0, MutationFence.executionOnly(lease), events,
                current -> current.withState(FlowExecutionState.COMPLETED, NOW, Map.of()));
        return store;
    }

    private FlowEngine durableEngine(InMemoryFlowExecutionStore store) {
        return engineBuilder().store(store).maintenanceExecutor(Runnable::run).build();
    }

    private FlowEngine.Builder engineBuilder() {
        return FlowEngine.builder(mock(UtxoSupplier.class), mock(ProtocolParamsSupplier.class),
                        mock(TransactionProcessor.class), mock(ChainDataSupplier.class))
                .executor(Runnable::run)
                .clock(fixedClock());
    }

    private Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }
}
