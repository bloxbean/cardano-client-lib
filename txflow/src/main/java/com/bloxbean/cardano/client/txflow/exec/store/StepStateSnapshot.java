package com.bloxbean.cardano.client.txflow.exec.store;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Snapshot of a flow step's execution state for persistence.
 * <p>
 * This class captures the essential state of a step that needs to be
 * persisted for recovery after application restart.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepStateSnapshot {

    /**
     * The unique identifier of this step within the flow.
     */
    private String stepId;

    /**
     * The transaction hash if the step has been submitted.
     * Null if the step hasn't been submitted yet.
     */
    private String transactionHash;

    /**
     * Current state of the transaction.
     */
    private TransactionState state;

    /**
     * When the transaction was submitted to the network.
     * Null if not yet submitted.
     */
    private Instant submittedAt;

    /**
     * Block height where the transaction was included.
     * Null until the transaction is in a block.
     */
    private Long blockHeight;

    /**
     * Current confirmation depth (number of blocks on top of the transaction).
     * Null until the transaction is in a block.
     */
    private Integer confirmationDepth;

    /**
     * When the step state was last checked/updated.
     */
    private Instant lastChecked;

    /**
     * When the transaction reached CONFIRMED status.
     * Null until confirmed.
     */
    private Instant confirmedAt;

    /**
     * Error message if the transaction failed or was rolled back.
     * Null for successful transactions.
     */
    private String errorMessage;

    /**
     * Create a snapshot for a pending step.
     *
     * @param stepId the step identifier
     * @return a new snapshot in PENDING state
     */
    public static StepStateSnapshot pending(String stepId) {
        return StepStateSnapshot.builder()
                .stepId(stepId)
                .state(TransactionState.PENDING)
                .build();
    }

    /**
     * Create a snapshot for a submitted step.
     *
     * @param stepId the step identifier
     * @param txHash the transaction hash
     * @return a new snapshot in SUBMITTED state
     */
    public static StepStateSnapshot submitted(String stepId, String txHash) {
        return StepStateSnapshot.builder()
                .stepId(stepId)
                .transactionHash(txHash)
                .state(TransactionState.SUBMITTED)
                .submittedAt(Instant.now())
                .build();
    }

    /**
     * Check if this step has been submitted.
     *
     * @return true if the step has a transaction hash
     */
    public boolean isSubmitted() {
        return transactionHash != null;
    }

    /**
     * Check if this step needs tracking (submitted but not finalized).
     *
     * @return true if the step should be monitored for confirmation
     */
    public boolean needsTracking() {
        return isSubmitted() && state != null && state.isInProgress();
    }
}
