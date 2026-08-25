package com.bloxbean.cardano.client.txflow.stream;

/**
 * Structured stream event callbacks.
 * <p>
 * Listener callbacks are best-effort observers: the stream isolates every
 * invocation, so a throwing listener can never fail a submission, kill the
 * dispatcher, or wedge {@link TxFlowStream#drain()}.
 */
public interface TxStreamEventListener {
    /** Listener instance that ignores every callback. */
    TxStreamEventListener NOOP = new TxStreamEventListener() {
    };

    /**
     * Called after an item has been accepted and its receipt created.
     *
     * @param item accepted work item
     * @param receipt receipt associated with the item
     */
    default void onItemAccepted(TxWorkItem item, TxStreamReceipt receipt) {
    }

    /**
     * Called when submission is rejected before an item record or receipt is
     * created. The callback is observational only: listener failures are
     * isolated and cannot change the rejection outcome.
     *
     * @param itemId rejected caller-visible item id
     * @param cause typed rejection cause
     */
    default void onItemRejected(String itemId, TxStreamException cause) {
    }

    /**
     * Called whenever the item projection advances, including read-through
     * recovery repairs.
     *
     * @param result latest item result snapshot
     */
    default void onItemUpdated(TxStreamItemResult result) {
    }

    /**
     * Called whenever a batch projection advances: window closed
     * ({@code PLANNED}), plan dispatched ({@code RUNNING}), and the terminal
     * status derived from the member items.
     *
     * @param batch latest batch snapshot
     */
    default void onBatchUpdated(TxStreamBatchResult batch) {
    }

    /**
     * Called after the stream has started.
     *
     * @param streamId stream id
     */
    default void onStreamStarted(String streamId) {
    }

    /**
     * Called after an orderly drain has completed.
     *
     * @param streamId stream id
     */
    default void onStreamDrained(String streamId) {
    }

    /**
     * Called exactly once after an abort report is published and before
     * {@link #onStreamClosed(String)}. The report is immutable, but its
     * {@link AbortReport#quiescence()} stage may still be incomplete because
     * cancellation is cooperative. Reentrant calls to
     * {@link TxFlowStream#abort(String)} return this same report and do not
     * trigger another callback.
     *
     * @param streamId stream id
     * @param report published abort report
     */
    default void onStreamAborted(String streamId, AbortReport report) {
    }

    /**
     * Called after the stream has released its source and resources.
     *
     * @param streamId stream id
     */
    default void onStreamClosed(String streamId) {
    }

    /**
     * Called when this instance becomes the {@link OwnershipStatus.State#ACTIVE
     * ACTIVE} single-owner of the stream — at {@code start()} if it acquires the
     * lease, or later when a standby takes over after the previous owner's crash
     * or expiry (ADR 0004 iteration 3d). Only ever fired for a stream with
     * ownership opted in.
     *
     * @param status the acquired ownership state (ACTIVE, with the held epoch)
     */
    default void onOwnershipAcquired(OwnershipStatus status) {
    }

    /**
     * Called when this instance loses ACTIVE ownership — its lease renewal was
     * fenced (a different instance took over) and it steps down to
     * {@link OwnershipStatus.State#STANDBY STANDBY}, or it released ownership on
     * close/abort. It stops dispatching immediately; in-flight engine executions
     * it already started continue and are reconciled by the new owner.
     *
     * @param status the ownership state after the loss (STANDBY or RELEASED)
     */
    default void onOwnershipLost(OwnershipStatus status) {
    }
}
