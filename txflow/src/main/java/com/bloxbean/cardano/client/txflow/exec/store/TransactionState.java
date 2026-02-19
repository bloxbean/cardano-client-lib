package com.bloxbean.cardano.client.txflow.exec.store;

/**
 * Represents the lifecycle state of a transaction in a flow.
 * <p>
 * States progress in order:
 * <pre>
 * PENDING -&gt; SUBMITTED -&gt; IN_BLOCK -&gt; CONFIRMED
 *                            |
 *                            v
 *                       ROLLED_BACK
 * </pre>
 */
public enum TransactionState {

    /**
     * Transaction is being built or queued for submission.
     */
    PENDING,

    /**
     * Transaction has been submitted to the network but not yet seen in a block.
     */
    SUBMITTED,

    /**
     * Transaction has been included in a block but not yet reached confirmation depth.
     */
    IN_BLOCK,

    /**
     * Transaction has reached the required confirmation depth.
     */
    CONFIRMED,

    /**
     * Transaction was rolled back after being in a block (chain reorganization).
     */
    ROLLED_BACK;

    /**
     * Check if this state indicates the transaction is still in progress.
     *
     * @return true if the transaction is still pending confirmation
     */
    public boolean isInProgress() {
        return this == PENDING || this == SUBMITTED || this == IN_BLOCK;
    }

    /**
     * Check if this state indicates the transaction completed successfully.
     *
     * @return true if the transaction is confirmed
     */
    public boolean isSuccessful() {
        return this == CONFIRMED;
    }

    /**
     * Check if this state indicates a failure.
     *
     * @return true if the transaction was rolled back
     */
    public boolean isFailed() {
        return this == ROLLED_BACK;
    }
}
