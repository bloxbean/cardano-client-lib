package com.bloxbean.cardano.client.txflow.stream;

import java.util.Objects;

/**
 * Outcome of a non-blocking {@link TxFlowStream#trySubmit(TxWorkItem)}.
 */
public final class EmitResult {
    /** Non-blocking submission outcome. */
    public enum Status {
        /**
         * Item was accepted into stream work and a receipt is available.
         */
        OK,
        /** The bounded stream buffer is full. */
        FULL,
        /** The stream is not started, draining, closed, or unhealthy. */
        CLOSED,
        /**
         * The stream is <em>temporarily</em> not accepting: this instance is an
         * ownership {@code STANDBY} (fenced or never-elected) that may reclaim
         * and accept again. Unlike {@link #CLOSED} this is not terminal — a
         * source adapter should park the item and retry later (see
         * {@link FlowWorkSource}), never tear down.
         */
        PAUSED,
        /**
         * A live redelivery matched an existing item's fingerprint; the
         * existing receipt is returned and no new work was created.
         */
        DUPLICATE_ATTACHED,
        /**
         * The item id was redelivered with different content; the typed
         * conflict is available via {@link #getConflict()}.
         */
        CONFLICT,
        /**
         * The stream rejected the item before any work was created — for
         * example an authoritative registration write failed. The typed cause
         * is available via {@link #getRejection()}; unlike
         * {@link TxFlowStream#submit(TxWorkItem)}, {@code trySubmit} reports
         * this disposition instead of throwing.
         */
        REJECTED
    }

    private final Status status;
    private final TxStreamReceipt receipt;
    private final TxStreamDuplicateItemException conflict;
    private final TxStreamException rejection;

    private EmitResult(Status status, TxStreamReceipt receipt,
                       TxStreamDuplicateItemException conflict,
                       TxStreamException rejection) {
        this.status = status;
        this.receipt = receipt;
        this.conflict = conflict;
        this.rejection = rejection;
    }

    /**
     * Returns the submission outcome.
     *
     * @return emit status
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Returns the receipt for {@link Status#OK} and
     * {@link Status#DUPLICATE_ATTACHED} outcomes.
     *
     * @return receipt, or {@code null} when no receipt is associated
     */
    public TxStreamReceipt getReceipt() {
        return receipt;
    }

    /**
     * Returns the typed conflict for {@link Status#CONFLICT} outcomes.
     *
     * @return duplicate-item conflict, or {@code null}
     */
    public TxStreamDuplicateItemException getConflict() {
        return conflict;
    }

    /**
     * Returns the typed rejection cause for {@link Status#REJECTED} outcomes.
     *
     * @return stream exception describing the rejection, or {@code null}
     */
    public TxStreamException getRejection() {
        return rejection;
    }

    /**
     * Reports whether a receipt is available for tracking.
     *
     * @return {@code true} for {@link Status#OK} and {@link Status#DUPLICATE_ATTACHED}
     */
    public boolean isAccepted() {
        return status == Status.OK || status == Status.DUPLICATE_ATTACHED;
    }

    /**
     * Creates an accepted result.
     *
     * @param receipt receipt for the accepted item
     * @return emit result
     */
    public static EmitResult ok(TxStreamReceipt receipt) {
        return new EmitResult(Status.OK, Objects.requireNonNull(receipt, "receipt"), null, null);
    }

    /**
     * Creates a result indicating that the bounded buffer is full.
     *
     * @return emit result
     */
    public static EmitResult full() {
        return new EmitResult(Status.FULL, null, null, null);
    }

    /**
     * Creates a result indicating that the stream cannot accept work.
     *
     * @return emit result
     */
    public static EmitResult closed() {
        return new EmitResult(Status.CLOSED, null, null, null);
    }

    /**
     * Creates a result indicating that the stream is temporarily not accepting
     * (ownership standby) — retry later, do not tear down.
     *
     * @return emit result
     */
    public static EmitResult paused() {
        return new EmitResult(Status.PAUSED, null, null, null);
    }

    /**
     * Creates a result attaching a redelivery to an existing item.
     *
     * @param receipt existing item's receipt
     * @return emit result
     */
    public static EmitResult duplicateAttached(TxStreamReceipt receipt) {
        return new EmitResult(Status.DUPLICATE_ATTACHED,
                Objects.requireNonNull(receipt, "receipt"), null, null);
    }

    /**
     * Creates a typed duplicate-content conflict result.
     *
     * @param conflict duplicate-item conflict
     * @return emit result
     */
    public static EmitResult conflict(TxStreamDuplicateItemException conflict) {
        return new EmitResult(Status.CONFLICT, null,
                Objects.requireNonNull(conflict, "conflict"), null);
    }

    /**
     * Creates a typed rejection result. Used by
     * {@link TxFlowStream#trySubmit(TxWorkItem)} where
     * {@link TxFlowStream#submit(TxWorkItem)} would throw the same cause.
     *
     * @param cause typed rejection cause
     * @return emit result
     */
    public static EmitResult rejected(TxStreamException cause) {
        return new EmitResult(Status.REJECTED, null, null,
                Objects.requireNonNull(cause, "cause"));
    }
}
