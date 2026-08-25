package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.txflow.exec.ExecutionEventView;
import com.bloxbean.cardano.client.txflow.exec.FlowEvent;
import com.bloxbean.cardano.client.txflow.exec.FlowEventType;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionRequest;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionResult;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Deterministic scripted {@link EngineGateway} for stream tests: every start
 * returns a controllable handle completed explicitly by the test.
 */
final class StubEngineGateway implements EngineGateway {
    static final Instant NOW = Instant.parse("2026-07-18T00:00:00Z");

    final List<FlowExecutionRequest> started = new CopyOnWriteArrayList<>();
    final List<StubHandle> handles = new CopyOnWriteArrayList<>();
    final Map<String, FlowExecutionSnapshot> snapshots = new ConcurrentHashMap<>();
    final List<String> callLog;
    /** Whether the scripted engine reports a durable execution store (P5). */
    volatile boolean durable;
    volatile RuntimeException startFailure;
    /** Optional caller-owned dispatcher exposed for builder inheritance tests. */
    volatile Executor executionExecutor;
    /**
     * When set, start() completes the returned handle with this result unless
     * the function returns {@code null}, in which case the handle stays running
     * (lets a test auto-complete some executions — e.g. the fan-out bootstrap —
     * while driving others manually).
     */
    volatile Function<FlowExecutionRequest, FlowExecutionResult> immediateResult;
    /**
     * Opt-in engine-idempotent MATCH: once a started execution completes, a
     * later start for the same execution id returns a completed, step-less
     * handle (the stored-execution shape) instead of re-running — modelling the
     * engine's idempotency claim across restarts / a second stream instance.
     */
    volatile boolean idempotentMatch;
    final Map<String, FlowExecutionResult> storedResults = new ConcurrentHashMap<>();
    /** When set, invoked at the top of start(); lets tests gate/observe dispatch. */
    volatile Runnable startHook;
    /** When set, invoked after a start's handle is registered in {@link #handles}. */
    volatile Runnable handleCreatedHook;

    StubEngineGateway() {
        this(new CopyOnWriteArrayList<>());
    }

    StubEngineGateway(List<String> callLog) {
        this.callLog = callLog;
    }

    @Override
    public Optional<Executor> executionExecutor() {
        return Optional.ofNullable(executionExecutor);
    }

    @Override
    public ExecutionHandle start(FlowExecutionRequest request) {
        Runnable hook = startHook;
        if (hook != null) hook.run();
        RuntimeException failure = startFailure;
        if (failure != null) throw failure;
        if (idempotentMatch) {
            FlowExecutionResult stored = storedResults.get(request.getExecutionId());
            if (stored != null) {
                // Idempotent MATCH: a completed, step-less handle, NOT recorded
                // as a fresh start — the wallet is never re-split.
                callLog.add("match:" + request.getExecutionId());
                StubHandle matched = new StubHandle(request.getExecutionId());
                matched.future.complete(new FlowExecutionResult(request.getExecutionId(), "fp",
                        FlowExecutionState.COMPLETED, List.of(), null, NOW, NOW));
                handles.add(matched);
                return matched;
            }
        }
        callLog.add("start:" + request.getExecutionId());
        started.add(request);
        StubHandle handle = new StubHandle(request.getExecutionId());
        handles.add(handle);
        if (idempotentMatch) {
            handle.future.whenComplete((result, error) -> {
                if (result != null) {
                    storedResults.putIfAbsent(request.getExecutionId(), result);
                }
            });
        }
        Runnable createdHook = handleCreatedHook;
        if (createdHook != null) createdHook.run();
        Function<FlowExecutionRequest, FlowExecutionResult> immediate = immediateResult;
        if (immediate != null) {
            FlowExecutionResult result = immediate.apply(request);
            if (result != null) {
                handle.future.complete(result);
            }
        }
        return handle;
    }

    @Override
    public boolean durableExecution() {
        return durable;
    }

    @Override
    public Optional<FlowExecutionSnapshot> executionSnapshot(String executionId) {
        return Optional.ofNullable(snapshots.get(executionId));
    }

    @Override
    public Optional<ExecutionEventView> executionEvents(String executionId,
                                                        long afterSequence, int limit) {
        return Optional.empty();
    }

    StubHandle lastHandle() {
        return handles.get(handles.size() - 1);
    }

    void putSnapshot(String executionId, FlowExecutionState state) {
        putSnapshot(executionId, state, Map.of());
    }

    void putSnapshot(String executionId, FlowExecutionState state, Map<String, Object> data) {
        snapshots.put(executionId, new FlowExecutionSnapshot(executionId, "fp", "req",
                state, 1, 1, 0, NOW, data));
    }

    static final class StubHandle implements ExecutionHandle {
        final String executionId;
        final CompletableFuture<FlowExecutionResult> future = new CompletableFuture<>();
        final List<FlowEvent> events = new CopyOnWriteArrayList<>();
        final AtomicBoolean cancelRequested = new AtomicBoolean();
        volatile String cancelReason;

        private StubHandle(String executionId) {
            this.executionId = executionId;
        }

        @Override
        public String executionId() {
            return executionId;
        }

        @Override
        public boolean isDone() {
            return future.isDone();
        }

        @Override
        public FlowExecutionResult resultIfDone() {
            return future.getNow(null);
        }

        @Override
        public CompletionStage<FlowExecutionResult> completion() {
            return future;
        }

        @Override
        public List<FlowEvent> events() {
            return List.copyOf(events);
        }

        @Override
        public List<FlowEvent> eventsAfter(long sequence) {
            return events.stream()
                    .filter(event -> event.sequence() > sequence)
                    .collect(Collectors.toList());
        }

        @Override
        public void requestCancel(String reason) {
            cancelRequested.set(true);
            cancelReason = reason;
        }

        void submittedEvent(String stepId, String hash) {
            events.add(new FlowEvent(events.size() + 1L, executionId,
                    FlowEventType.TRANSACTION_SUBMITTED, NOW, stepId, hash, Map.of()));
        }

        void completeConfirmed(String stepId, String hash) {
            submittedEvent(stepId, hash);
            future.complete(new FlowExecutionResult(executionId, "fp",
                    FlowExecutionState.COMPLETED,
                    List.of(FlowStepResult.successAt(stepId, hash, List.of(), List.of(), NOW)),
                    null, NOW, NOW));
        }

        void completeCancelled() {
            future.complete(new FlowExecutionResult(executionId, "fp",
                    FlowExecutionState.CANCELLED, List.of(), null, NOW, NOW));
        }

        void complete(FlowExecutionResult result) {
            future.complete(result);
        }
    }
}
