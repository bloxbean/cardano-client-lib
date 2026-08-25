package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.txflow.exec.ExecutionEventView;
import com.bloxbean.cardano.client.txflow.exec.FlowEvent;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionRequest;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionResult;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionSnapshot;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * Thin seam over the engine surface the stream depends on, so stream behavior
 * can be tested deterministically against a scripted gateway while production
 * always runs through {@link FlowEngineGateway}.
 */
interface EngineGateway {
    /**
     * Compiles and starts (or idempotently matches) one execution.
     *
     * @param request immutable execution request
     * @return handle view for completion, events, and cooperative cancellation
     */
    ExecutionHandle start(FlowExecutionRequest request);

    /**
     * Reports whether the underlying engine durably persists executions
     * (delegates to {@code FlowEngine.capabilities().durableExecution()}). The
     * durable-stream builder invariant gates on this: a durable stream store
     * requires a durable engine store, since "no stored execution ⇒ it never
     * ran" — the premise of restart re-attach — holds only against a durable
     * engine. Defaults to {@code false} so non-durable test gateways need not
     * override it.
     *
     * @return {@code true} when the engine durably persists executions
     */
    default boolean durableExecution() {
        return false;
    }

    /**
     * Returns the caller-owned execution dispatcher when the underlying engine
     * can expose it. Test/custom gateways may return empty and require an
     * explicit stream executor.
     *
     * @return execution dispatcher available for stream inheritance
     */
    default Optional<Executor> executionExecutor() {
        return Optional.empty();
    }

    /**
     * Reads the durable snapshot of one execution, when a store is configured.
     *
     * @param executionId execution identity
     * @return durable snapshot when present
     */
    Optional<FlowExecutionSnapshot> executionSnapshot(String executionId);

    /**
     * Reads a compaction-safe page of one stored execution's events.
     *
     * @param executionId execution identity
     * @param afterSequence exclusive sequence cursor
     * @param limit maximum events to return
     * @return baseline snapshot plus event tail when present
     */
    Optional<ExecutionEventView> executionEvents(String executionId, long afterSequence, int limit);

    /** Minimal view of a {@code FlowExecutionHandle}. */
    interface ExecutionHandle {
        /**
         * Returns the execution identity.
         *
         * @return execution id
         */
        String executionId();

        /**
         * Reports whether the execution has produced its terminal result.
         *
         * @return {@code true} after completion
         */
        boolean isDone();

        /**
         * Returns the terminal result when already available.
         *
         * @return terminal result, or {@code null} while running
         */
        FlowExecutionResult resultIfDone();

        /**
         * Returns a non-cancelling stage for the terminal result.
         *
         * @return completion stage view
         */
        CompletionStage<FlowExecutionResult> completion();

        /**
         * Returns an immutable snapshot of the execution's events.
         *
         * @return ordered event snapshot
         */
        List<FlowEvent> events();

        /**
         * Returns the events recorded strictly after a sequence cursor.
         *
         * @param sequence exclusive sequence cursor
         * @return ordered event snapshot after the cursor
         */
        List<FlowEvent> eventsAfter(long sequence);

        /**
         * Requests cooperative cancellation.
         *
         * @param reason diagnostic reason, or {@code null}
         */
        void requestCancel(String reason);
    }
}
