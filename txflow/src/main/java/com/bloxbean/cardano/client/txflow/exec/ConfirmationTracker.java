package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.model.TransactionInfo;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;

/**
 * Tracks transaction confirmation status and detects rollbacks.
 * <p>
 * ConfirmationTracker monitors transactions through their lifecycle from submission
 * to finality, detecting chain reorganizations (rollbacks) along the way.
 *
 * <h3>Key Features</h3>
 * <ul>
 *     <li>Calculates confirmation depth based on current chain tip</li>
 *     <li>Tracks previously seen transactions to detect rollbacks</li>
 *     <li>Supports configurable confirmation thresholds</li>
 *     <li>Thread-safe for concurrent access</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * ConfirmationTracker tracker = new ConfirmationTracker(chainDataSupplier, ConfirmationConfig.defaults());
 *
 * // Single status check
 * ConfirmationResult result = tracker.checkStatus(txHash);
 *
 * // Wait for confirmation with callback
 * ConfirmationResult result = tracker.waitForConfirmation(txHash, ConfirmationStatus.CONFIRMED,
 *     (step, result) -> System.out.println("Depth: " + result.getConfirmationDepth()));
 * }</pre>
 */
@Slf4j
public class ConfirmationTracker {

    private final ChainDataSupplier chainDataSupplier;
    private final ConfirmationConfig config;

    /**
     * Tracks last known state of transactions for rollback detection.
     * Key: transaction hash, Value: last known tracking state
     */
    private final ConcurrentMap<String, TrackedTransaction> trackedTransactions = new ConcurrentHashMap<>();

    /**
     * Create a new ConfirmationTracker.
     *
     * @param chainDataSupplier the chain data supplier for chain queries
     * @param config the confirmation configuration
     */
    public ConfirmationTracker(ChainDataSupplier chainDataSupplier, ConfirmationConfig config) {
        this.chainDataSupplier = chainDataSupplier;
        this.config = config != null ? config : ConfirmationConfig.defaults();
    }

    /**
     * Check the current confirmation status of a transaction.
     * <p>
     * This method:
     * <ol>
     *     <li>Gets the current chain tip</li>
     *     <li>Queries the transaction details</li>
     *     <li>Calculates confirmation depth</li>
     *     <li>Detects rollback if transaction was previously tracked but now missing</li>
     *     <li>Returns the appropriate status</li>
     * </ol>
     *
     * @param txHash the transaction hash to check
     * @return the confirmation result
     */
    public ConfirmationResult checkStatus(String txHash) {
        try {
            // Get current chain tip
            long tipHeight;
            try {
                tipHeight = chainDataSupplier.getChainTipHeight();
            } catch (Exception e) {
                log.warn("Failed to get latest block for confirmation check: {}", e.getMessage());
                return ConfirmationResult.builder()
                        .txHash(txHash)
                        .status(ConfirmationStatus.SUBMITTED)
                        .confirmationDepth(-1)
                        .error(new RuntimeException("Failed to get latest block", e))
                        .build();
            }

            // Get transaction details
            var txInfo = chainDataSupplier.getTransactionInfo(txHash);

            TrackedTransaction previousState = trackedTransactions.get(txHash);

            if (txInfo.isEmpty()) {
                // Transaction not found in chain
                if (previousState != null && previousState.getBlockHeight() != null) {
                    // Previously tracked in a block but now missing - ROLLBACK detected
                    log.warn("Rollback detected for tx {}: was in block {} but now not found",
                            txHash, previousState.getBlockHeight());
                    return ConfirmationResult.rolledBack(txHash, previousState.getBlockHeight(), tipHeight,
                            new RuntimeException("Transaction disappeared from chain (rollback detected)"));
                }
                // Not yet in any block
                return ConfirmationResult.submitted(txHash, tipHeight);
            }

            TransactionInfo tx = txInfo.get();
            Long txBlockHeight = tx.getBlockHeight();
            String txBlockHash = tx.getBlockHash();

            if (txBlockHeight == null) {
                // Transaction found but block height not available (unusual)
                return ConfirmationResult.submitted(txHash, tipHeight);
            }

            // Check for block hash change (possible rollback and re-inclusion)
            if (previousState != null && previousState.getBlockHash() != null
                    && !previousState.getBlockHash().equals(txBlockHash)) {
                log.info("Transaction {} was re-included: old block={}, new block={}",
                        txHash, previousState.getBlockHash(), txBlockHash);
            }

            // Calculate confirmation depth
            int depth = (int) (tipHeight - txBlockHeight);

            // Determine status based on depth
            ConfirmationStatus status;
            if (depth < 0) {
                // This shouldn't happen normally (tx in block ahead of tip)
                // Could indicate timing issue or node sync problem
                log.warn("Transaction {} has negative depth: block={}, tip={}",
                        txHash, txBlockHeight, tipHeight);
                status = ConfirmationStatus.IN_BLOCK;
                depth = 0;
            } else if (depth < config.getMinConfirmations()) {
                status = ConfirmationStatus.IN_BLOCK;
            } else if (depth < config.getSafeConfirmations()) {
                status = ConfirmationStatus.CONFIRMED;
            } else {
                status = ConfirmationStatus.FINALIZED;
            }

            // Update tracking state
            TrackedTransaction newState = new TrackedTransaction(
                    txHash, txBlockHeight, txBlockHash, status, Instant.now());
            trackedTransactions.put(txHash, newState);

            return ConfirmationResult.builder()
                    .txHash(txHash)
                    .status(status)
                    .confirmationDepth(depth)
                    .blockHeight(txBlockHeight)
                    .blockHash(txBlockHash)
                    .currentTipHeight(tipHeight)
                    .build();

        } catch (Exception e) {
            log.error("Error checking confirmation status for tx {}", txHash, e);
            return ConfirmationResult.builder()
                    .txHash(txHash)
                    .status(ConfirmationStatus.SUBMITTED)
                    .confirmationDepth(-1)
                    .error(e)
                    .build();
        }
    }

    /**
     * Wait for a transaction to reach the target confirmation status.
     * <p>
     * Polls the transaction status at the configured interval until:
     * <ul>
     *     <li>The target status is reached</li>
     *     <li>The transaction is rolled back</li>
     *     <li>The timeout expires</li>
     * </ul>
     *
     * @param txHash the transaction hash to monitor
     * @param targetStatus the status to wait for
     * @return the final confirmation result
     */
    public ConfirmationResult waitForConfirmation(String txHash, ConfirmationStatus targetStatus) {
        return waitForConfirmation(txHash, targetStatus, null);
    }

    /**
     * Wait for a transaction to reach the target confirmation status with progress callback.
     *
     * @param txHash the transaction hash to monitor
     * @param targetStatus the status to wait for
     * @param onProgress optional callback invoked on each status check (txHash, result)
     * @return the final confirmation result
     */
    public ConfirmationResult waitForConfirmation(String txHash, ConfirmationStatus targetStatus,
                                                   BiConsumer<String, ConfirmationResult> onProgress) {
        long startTime = System.currentTimeMillis();
        long timeoutMs = config.getTimeout().toMillis();
        long checkIntervalMs = config.getCheckInterval().toMillis();

        ConfirmationResult lastResult = null;
        int lastDepth = -2; // Initialize to invalid value to ensure first callback fires

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            lastResult = checkStatus(txHash);

            // Notify progress if callback provided and depth changed
            if (onProgress != null && lastResult.getConfirmationDepth() != lastDepth) {
                onProgress.accept(txHash, lastResult);
                lastDepth = lastResult.getConfirmationDepth();
            }

            // Check for terminal conditions
            if (lastResult.isRolledBack()) {
                log.warn("Transaction {} rolled back during confirmation wait", txHash);
                return lastResult;
            }

            if (lastResult.hasReached(targetStatus)) {
                log.debug("Transaction {} reached target status {}", txHash, targetStatus);
                return lastResult;
            }

            // Wait before next check
            try {
                Thread.sleep(checkIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ConfirmationResult.builder()
                        .txHash(txHash)
                        .status(lastResult != null ? lastResult.getStatus() : ConfirmationStatus.SUBMITTED)
                        .confirmationDepth(lastResult != null ? lastResult.getConfirmationDepth() : -1)
                        .error(e)
                        .build();
            }
        }

        // Timeout reached
        log.warn("Timeout waiting for tx {} to reach status {}", txHash, targetStatus);
        return ConfirmationResult.builder()
                .txHash(txHash)
                .status(lastResult != null ? lastResult.getStatus() : ConfirmationStatus.SUBMITTED)
                .confirmationDepth(lastResult != null ? lastResult.getConfirmationDepth() : -1)
                .currentTipHeight(lastResult != null ? lastResult.getCurrentTipHeight() : null)
                .error(new ConfirmationTimeoutException(txHash))
                .build();
    }

    /**
     * Remove a transaction from tracking.
     * <p>
     * Call this when a transaction is no longer needed for rollback detection,
     * such as after it has been finalized or the flow has completed.
     *
     * @param txHash the transaction hash to stop tracking
     */
    public void stopTracking(String txHash) {
        trackedTransactions.remove(txHash);
    }

    /**
     * Clear all tracked transactions.
     */
    public void clearTracking() {
        trackedTransactions.clear();
    }

    /**
     * Get the number of currently tracked transactions.
     *
     * @return the count of tracked transactions
     */
    public int getTrackedCount() {
        return trackedTransactions.size();
    }

    /**
     * Get the configuration used by this tracker.
     *
     * @return the confirmation config
     */
    public ConfirmationConfig getConfig() {
        return config;
    }

    /**
     * Internal class to track transaction state for rollback detection.
     */
    @Getter
    static class TrackedTransaction {
        private final String txHash;
        private final Long blockHeight;
        private final String blockHash;
        private final ConfirmationStatus lastStatus;
        private final Instant lastChecked;

        TrackedTransaction(String txHash, Long blockHeight, String blockHash,
                          ConfirmationStatus lastStatus, Instant lastChecked) {
            this.txHash = txHash;
            this.blockHeight = blockHeight;
            this.blockHash = blockHash;
            this.lastStatus = lastStatus;
            this.lastChecked = lastChecked;
        }
    }
}
