package com.bloxbean.cardano.client.txflow.exec;

/**
 * Exception thrown when a transaction confirmation times out.
 * <p>
 * This exception is NOT retryable because:
 * <ul>
 *     <li>The transaction may still confirm later</li>
 *     <li>Retrying would build a NEW transaction that could cause duplicates</li>
 *     <li>Original UTXOs may already be spent by the pending transaction</li>
 * </ul>
 * <p>
 * Users should handle this exception by monitoring the transaction hash
 * for eventual confirmation rather than retrying the transaction.
 */
public class ConfirmationTimeoutException extends FlowExecutionException {

    private final String transactionHash;

    /**
     * Create a new ConfirmationTimeoutException.
     *
     * @param transactionHash the hash of the transaction that timed out waiting for confirmation
     */
    public ConfirmationTimeoutException(String transactionHash) {
        super("Transaction confirmation timeout: " + transactionHash);
        this.transactionHash = transactionHash;
    }

    /**
     * Get the hash of the transaction that timed out waiting for confirmation.
     * <p>
     * The transaction may still confirm later - users should monitor this hash.
     *
     * @return the transaction hash
     */
    public String getTransactionHash() {
        return transactionHash;
    }
}
