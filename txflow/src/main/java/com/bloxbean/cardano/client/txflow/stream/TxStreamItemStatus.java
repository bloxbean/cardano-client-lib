package com.bloxbean.cardano.client.txflow.stream;

/**
 * Status of a work item accepted by a {@link TxFlowStream}.
 * <p>
 * Every status is a projection of engine truth except {@link #ACCEPTED}, which
 * is the only stream-owned state. There is deliberately no in-progress item
 * status: a submitted-but-unconfirmed transaction inside a terminally completed
 * flow is reported as {@link #RECOVERY_REQUIRED} with its transaction hash
 * retained, never as failed and never left non-terminal.
 */
public enum TxStreamItemStatus {
    /** The stream accepted the item and returned a receipt. */
    ACCEPTED,
    /** The item's execution binding was recorded and dispatch began. */
    PLANNED,
    /**
     * The engine observed a {@code TRANSACTION_SUBMITTED} event for the item's
     * step. Submission is never asserted in advance of the backend.
     */
    SUBMITTED,
    /** The item's transaction confirmed on chain. */
    CONFIRMED,
    /**
     * The item failed conclusively. When a transaction was submitted before the
     * failure, its hash is retained on the result.
     */
    FAILED,
    /** The item was cancelled before it produced a confirmed transaction. */
    CANCELLED,
    /**
     * The transaction's disposition is uncertain — typically submitted but
     * unconfirmed inside a terminal flow — and must be reconciled. Repair is
     * read-through: {@link TxFlowStream#getItemStatus(String)} and
     * {@link TxFlowStream#reconcile(String)} consult the engine snapshot and
     * advance the projection when the engine has an authoritative answer.
     */
    RECOVERY_REQUIRED
}
