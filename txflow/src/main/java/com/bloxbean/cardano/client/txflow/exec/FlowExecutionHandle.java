package com.bloxbean.cardano.client.txflow.exec;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe handle for observing and cooperatively cancelling one execution.
 *
 * <p>The handle does not own the task or its executor. Cancellation records a
 * signal that the runtime checks at safe boundaries; it neither interrupts the
 * executing thread nor closes caller-owned resources. Use {@link #await()} to
 * obtain the eventual terminal result.</p>
 *
 * <p>Events are ordered by their per-execution sequence and returned as
 * immutable snapshots, so callers may poll with {@link #getEventsAfter(long)}
 * without holding an internal lock.</p>
 */
public final class FlowExecutionHandle {
    private final String executionId;
    private final CompletableFuture<FlowExecutionResult> completion;
    private final AtomicBoolean cancelled;
    private final List<FlowEvent> events;
    private final AtomicReference<String> cancellationReason = new AtomicReference<>();

    FlowExecutionHandle(String executionId, CompletableFuture<FlowExecutionResult> completion,
                        AtomicBoolean cancelled, List<FlowEvent> events) {
        this.executionId = executionId;
        this.completion = completion;
        this.cancelled = cancelled;
        this.events = events;
    }

    /**
     * Returns the stable identifier used for correlation, idempotency, and
     * durable lookup.
     *
     * @return execution identifier
     */
    public String getExecutionId() { return executionId; }

    /**
     * Requests cooperative cancellation without attaching a reason.
     *
     * @return {@code true} only when this call records the first request
     */
    public boolean cancel() {
        return requestCancel(null) == CancellationResult.REQUESTED;
    }

    /**
     * Requests cooperative cancellation and optionally records an operator
     * reason for the cancellation event.
     *
     * <p>A {@link CancellationResult#REQUESTED} response is an acknowledgement
     * of the signal, not confirmation that execution is already terminal.</p>
     *
     * @param reason diagnostic reason, or {@code null}
     * @return disposition of this cancellation request
     */
    public synchronized CancellationResult requestCancel(String reason) {
        if (completion.isDone()) return CancellationResult.ALREADY_TERMINAL;
        if (!cancelled.get()) {
            cancellationReason.set(reason);
            cancelled.set(true);
            return CancellationResult.REQUESTED;
        }
        return CancellationResult.ALREADY_REQUESTED;
    }

    /**
     * Returns the reason attached to the first accepted cancellation request.
     *
     * @return cancellation reason, or {@code null}
     */
    public String getCancellationReason() { return cancellationReason.get(); }

    /**
     * Reports whether the execution has produced its terminal result.
     *
     * @return {@code true} after completion
     */
    public boolean isDone() { return completion.isDone(); }

    /**
     * Returns an immutable snapshot of events currently visible to this handle.
     *
     * @return ordered event snapshot
     */
    public List<FlowEvent> getEvents() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }

    /**
     * Returns the ordered event tail after an exclusive sequence cursor.
     *
     * @param sequence last sequence already consumed, or zero for all events
     * @return immutable events whose sequence is greater than {@code sequence}
     */
    public List<FlowEvent> getEventsAfter(long sequence) {
        if (sequence < 0) throw new IllegalArgumentException("sequence cannot be negative");
        synchronized (events) {
            return events.stream().filter(event -> event.sequence() > sequence).toList();
        }
    }

    /**
     * Waits for and returns the terminal result.
     *
     * <p>This blocks the calling thread only; it does not change the executor
     * selected for the flow. Interruption restores the interrupt flag and is
     * reported as a {@link FlowExecutionException}.</p>
     *
     * @return terminal execution result
     * @throws FlowExecutionException if waiting is interrupted or the execution
     *                                task completes exceptionally
     */
    public FlowExecutionResult await() {
        try {
            return completion.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FlowExecutionException("Interrupted while awaiting flow execution", e);
        } catch (ExecutionException e) {
            throw new FlowExecutionException("Flow execution failed", e.getCause());
        }
    }
}
