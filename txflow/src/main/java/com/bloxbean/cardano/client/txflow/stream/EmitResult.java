package com.bloxbean.cardano.client.txflow.stream;

import lombok.Getter;

/**
 * Result from a non-blocking stream submission.
 */
@Getter
public final class EmitResult {
    /**
     * Non-blocking submission outcome.
     */
    public enum Status {
        /**
         * Item was accepted and a receipt is available.
         */
        OK,
        /**
         * The bounded stream buffer is full.
         */
        FULL,
        /**
         * The stream is temporarily paused.
         */
        PAUSED,
        /**
         * The stream is closed, shutting down, or draining.
         */
        CLOSED
    }

    private final Status status;
    private final TxStreamReceipt receipt;

    private EmitResult(Status status, TxStreamReceipt receipt) {
        this.status = status;
        this.receipt = receipt;
    }

    /**
     * Check whether the item was accepted.
     *
     * @return true when status is {@link Status#OK}
     */
    public boolean isAccepted() {
        return status == Status.OK;
    }

    /**
     * Create an accepted result.
     *
     * @param receipt receipt for the accepted item
     * @return emit result
     */
    public static EmitResult ok(TxStreamReceipt receipt) {
        return new EmitResult(Status.OK, receipt);
    }

    /**
     * Create a result indicating that the bounded buffer is full.
     *
     * @return emit result
     */
    public static EmitResult full() {
        return new EmitResult(Status.FULL, null);
    }

    /**
     * Create a result indicating that the stream is paused.
     *
     * @return emit result
     */
    public static EmitResult paused() {
        return new EmitResult(Status.PAUSED, null);
    }

    /**
     * Create a result indicating that the stream cannot accept more work.
     *
     * @return emit result
     */
    public static EmitResult closed() {
        return new EmitResult(Status.CLOSED, null);
    }
}
