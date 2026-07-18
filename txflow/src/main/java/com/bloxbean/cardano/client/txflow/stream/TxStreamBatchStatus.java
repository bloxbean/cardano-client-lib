package com.bloxbean.cardano.client.txflow.stream;

/**
 * Status for a planned stream batch/window.
 */
public enum TxStreamBatchStatus {
    /**
     * The batch has been planned into one or more generated flows.
     */
    PLANNED,
    /**
     * The generated flow execution phase has started.
     */
    SUBMITTED,
    /**
     * All affected items in the batch reached successful terminal status.
     */
    COMPLETED,
    /**
     * At least one affected item failed during planning or execution.
     */
    FAILED
}
