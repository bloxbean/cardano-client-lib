package com.bloxbean.cardano.client.txflow.result;

/**
 * Status of a transaction flow or individual step execution.
 */
public enum FlowStatus {
    /**
     * Flow or step is pending execution.
     */
    PENDING,

    /**
     * Flow or step is currently in progress.
     */
    IN_PROGRESS,

    /**
     * Flow or step completed successfully.
     */
    COMPLETED,

    /**
     * Flow or step failed.
     */
    FAILED,

    /**
     * Flow was cancelled.
     */
    CANCELLED
}
