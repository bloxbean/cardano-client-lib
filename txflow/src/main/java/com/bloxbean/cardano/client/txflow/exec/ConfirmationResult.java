package com.bloxbean.cardano.client.txflow.exec;

import lombok.Builder;
import lombok.Getter;

/**
 * Result of a transaction confirmation status check.
 * <p>
 * Contains the current confirmation status along with additional metadata
 * about the transaction's position in the chain.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ConfirmationResult result = tracker.checkStatus(txHash);
 * if (result.getStatus() == ConfirmationStatus.CONFIRMED) {
 *     System.out.println("Transaction confirmed at depth " + result.getConfirmationDepth());
 * } else if (result.getStatus() == ConfirmationStatus.ROLLED_BACK) {
 *     System.err.println("Transaction rolled back: " + result.getError().getMessage());
 * }
 * }</pre>
 */
@Getter
@Builder
public class ConfirmationResult {

    /**
     * The transaction hash being tracked.
     */
    private final String txHash;

    /**
     * The current confirmation status.
     */
    private final ConfirmationStatus status;

    /**
     * The number of blocks confirming this transaction.
     * <p>
     * This is calculated as: currentChainTip - transactionBlockHeight.
     * A value of 0 means the transaction is in the latest block.
     * A negative value (represented as -1) means the transaction is not found in any block.
     */
    private final int confirmationDepth;

    /**
     * The block height where the transaction was included.
     * <p>
     * Null if the transaction is not yet in a block (SUBMITTED status)
     * or has been rolled back.
     */
    private final Long blockHeight;

    /**
     * The hash of the block where the transaction was included.
     * <p>
     * Null if the transaction is not yet in a block (SUBMITTED status)
     * or has been rolled back.
     */
    private final String blockHash;

    /**
     * The current chain tip height at the time of this check.
     */
    private final Long currentTipHeight;

    /**
     * Error information if the transaction failed or was rolled back.
     * <p>
     * Null for successful status checks.
     */
    private final Throwable error;

    /**
     * Check if the transaction has reached at least the specified status.
     *
     * @param targetStatus the target status to check against
     * @return true if current status is at or beyond the target status
     */
    public boolean hasReached(ConfirmationStatus targetStatus) {
        if (status == ConfirmationStatus.ROLLED_BACK) {
            return false;
        }
        return status.ordinal() >= targetStatus.ordinal();
    }

    /**
     * Check if the transaction is in a terminal state (ROLLED_BACK).
     *
     * @return true if no further status changes are expected
     */
    public boolean isTerminal() {
        return status == ConfirmationStatus.ROLLED_BACK;
    }

    /**
     * Check if the transaction was rolled back.
     *
     * @return true if the transaction was rolled back
     */
    public boolean isRolledBack() {
        return status == ConfirmationStatus.ROLLED_BACK;
    }

    /**
     * Check if the transaction is successfully progressing (not rolled back).
     *
     * @return true if the transaction is in a healthy state
     */
    public boolean isHealthy() {
        return status != ConfirmationStatus.ROLLED_BACK;
    }

    /**
     * Create a result for a submitted but not yet confirmed transaction.
     *
     * @param txHash the transaction hash
     * @param currentTipHeight the current chain tip height
     * @return a new ConfirmationResult with SUBMITTED status
     */
    public static ConfirmationResult submitted(String txHash, Long currentTipHeight) {
        return ConfirmationResult.builder()
                .txHash(txHash)
                .status(ConfirmationStatus.SUBMITTED)
                .confirmationDepth(-1)
                .currentTipHeight(currentTipHeight)
                .build();
    }

    /**
     * Create a result for a rolled back transaction.
     *
     * @param txHash the transaction hash
     * @param previousBlockHeight the block height before rollback (if known)
     * @param currentTipHeight the current chain tip height
     * @param error optional error information
     * @return a new ConfirmationResult with ROLLED_BACK status
     */
    public static ConfirmationResult rolledBack(String txHash, Long previousBlockHeight,
                                                 Long currentTipHeight, Throwable error) {
        return ConfirmationResult.builder()
                .txHash(txHash)
                .status(ConfirmationStatus.ROLLED_BACK)
                .confirmationDepth(-1)
                .blockHeight(previousBlockHeight)
                .currentTipHeight(currentTipHeight)
                .error(error)
                .build();
    }

    @Override
    public String toString() {
        return "ConfirmationResult{" +
                "txHash='" + txHash + '\'' +
                ", status=" + status +
                ", confirmationDepth=" + confirmationDepth +
                ", blockHeight=" + blockHeight +
                ", currentTipHeight=" + currentTipHeight +
                (error != null ? ", error=" + error.getMessage() : "") +
                '}';
    }
}
