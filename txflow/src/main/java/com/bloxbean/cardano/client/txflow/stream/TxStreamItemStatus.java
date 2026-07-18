package com.bloxbean.cardano.client.txflow.stream;

/**
 * Status for a work item accepted by a {@link TxFlowStream}.
 */
public enum TxStreamItemStatus {
    /**
     * The stream accepted the item and returned a receipt.
     */
    ACCEPTED,
    /**
     * The item has been assigned to a planned batch/flow/step.
     */
    PLANNED,
    /**
     * The generated bounded flow has begun execution for this item.
     */
    SUBMITTED,
    /**
     * The generated step completed successfully and produced a transaction hash.
     */
    CONFIRMED,
    /**
     * The item failed during planning or generated flow execution.
     */
    FAILED,
    /**
     * The item was cancelled before successful completion.
     */
    CANCELLED
}
