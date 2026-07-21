package com.bloxbean.cardano.client.txflow.stream;

/**
 * Status of one planning batch — the items of one closed window.
 * <p>
 * Batch status is a pure derivation over the batch's member item statuses:
 * it is observability metadata, never engine identity. A batch reaches a
 * terminal status only when every member item has reached a <em>final</em>
 * status; a member settled {@link TxStreamItemStatus#RECOVERY_REQUIRED}
 * keeps the batch {@link #RUNNING} until reconciliation repairs it — the
 * honest answer while a member transaction's disposition is uncertain.
 */
public enum TxStreamBatchStatus {
    /** The window closed and its items were handed to the planner. */
    PLANNED,
    /** The plan validated and its executions were dispatched. */
    RUNNING,
    /** Every member item confirmed. */
    COMPLETED,
    /** At least one member confirmed and at least one did not. */
    PARTIALLY_COMPLETED,
    /** No member confirmed and not every member was cancelled. */
    FAILED,
    /** Every member item was cancelled. */
    CANCELLED
}
