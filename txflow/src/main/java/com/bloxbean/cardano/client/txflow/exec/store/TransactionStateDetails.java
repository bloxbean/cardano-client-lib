package com.bloxbean.cardano.client.txflow.exec.store;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Detailed transaction state information for persistence.
 * <p>
 * This class encapsulates all the tracking information available when a
 * transaction state changes, including block height, confirmation depth,
 * timestamps, and error messages.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionStateDetails {

    /**
     * The transaction state.
     */
    private TransactionState state;

    /**
     * Block height where the transaction was included.
     * Available after IN_BLOCK status.
     */
    private Long blockHeight;

    /**
     * Current confirmation depth (number of blocks on top of the transaction).
     * Available after IN_BLOCK status.
     */
    private Integer confirmationDepth;

    /**
     * When this state was reached.
     */
    private Instant timestamp;

    /**
     * Error message if the transaction failed or was rolled back.
     */
    private String errorMessage;

    /**
     * Create details for a submitted transaction.
     *
     * @param timestamp when the transaction was submitted
     * @return TransactionStateDetails for SUBMITTED state
     */
    public static TransactionStateDetails submitted(Instant timestamp) {
        return TransactionStateDetails.builder()
                .state(TransactionState.SUBMITTED)
                .timestamp(timestamp)
                .build();
    }

    /**
     * Create details for a transaction seen in a block.
     *
     * @param blockHeight block height where the transaction was included
     * @param confirmationDepth current confirmation depth
     * @param timestamp when this status was observed
     * @return TransactionStateDetails for IN_BLOCK state
     */
    public static TransactionStateDetails inBlock(long blockHeight, int confirmationDepth, Instant timestamp) {
        return TransactionStateDetails.builder()
                .state(TransactionState.IN_BLOCK)
                .blockHeight(blockHeight)
                .confirmationDepth(confirmationDepth)
                .timestamp(timestamp)
                .build();
    }

    /**
     * Create details for a confirmed transaction.
     *
     * @param blockHeight block height where the transaction was included
     * @param confirmationDepth confirmation depth when confirmed
     * @param timestamp when confirmation was reached
     * @return TransactionStateDetails for CONFIRMED state
     */
    public static TransactionStateDetails confirmed(long blockHeight, int confirmationDepth, Instant timestamp) {
        return TransactionStateDetails.builder()
                .state(TransactionState.CONFIRMED)
                .blockHeight(blockHeight)
                .confirmationDepth(confirmationDepth)
                .timestamp(timestamp)
                .build();
    }

    /**
     * Create details for a rolled back transaction.
     *
     * @param previousBlockHeight block height before rollback (may be null)
     * @param errorMessage description of the rollback cause
     * @param timestamp when the rollback was detected
     * @return TransactionStateDetails for ROLLED_BACK state
     */
    public static TransactionStateDetails rolledBack(Long previousBlockHeight, String errorMessage, Instant timestamp) {
        return TransactionStateDetails.builder()
                .state(TransactionState.ROLLED_BACK)
                .blockHeight(previousBlockHeight)
                .errorMessage(errorMessage)
                .timestamp(timestamp)
                .build();
    }

    /**
     * Create details for a finalized transaction.
     *
     * @param blockHeight block height where the transaction was finalized
     * @param timestamp when finalization was confirmed
     * @return TransactionStateDetails for FINALIZED state
     */
    public static TransactionStateDetails finalized(long blockHeight, Instant timestamp) {
        return TransactionStateDetails.builder()
                .state(TransactionState.FINALIZED)
                .blockHeight(blockHeight)
                .timestamp(timestamp)
                .build();
    }
}
