package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.model.TransactionInfo;
import com.bloxbean.cardano.client.txflow.config.ConfirmationConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/**
 * Tracks transaction confirmation status and detects rollbacks.
 * <p>
 * ConfirmationTracker monitors transactions through their lifecycle from submission
 * to finality, detecting chain reorganizations (rollbacks) along the way.
 *
 * <h2>Key Features</h2>
 * <ul>
 *     <li>Calculates confirmation depth based on current chain tip</li>
 *     <li>Tracks previously seen transactions to detect rollbacks</li>
 *     <li>Supports configurable confirmation thresholds</li>
 *     <li>Thread-safe for concurrent access</li>
 * </ul>
 *
 * <h2>Usage</h2>
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
    private final FlowScheduler scheduler;

    /**
     * Tracks last known state of transactions for rollback detection.
     * Key: transaction hash, Value: last known tracking state
     */
    private final ConcurrentMap<String, TrackedTransaction> trackedTransactions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Integer> authoritativeAbsenceCounts = new ConcurrentHashMap<>();

    /**
     * Create a new ConfirmationTracker.
     *
     * @param chainDataSupplier the chain data supplier for chain queries
     * @param config the confirmation configuration
     */
    public ConfirmationTracker(ChainDataSupplier chainDataSupplier, ConfirmationConfig config) {
        this(chainDataSupplier, config, FlowScheduler.system());
    }

    ConfirmationTracker(ChainDataSupplier chainDataSupplier, ConfirmationConfig config,
                        FlowScheduler scheduler) {
        this.chainDataSupplier = Objects.requireNonNull(chainDataSupplier, "chainDataSupplier");
        this.config = config != null ? config : ConfirmationConfig.defaults();
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
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
            return checkStatusAtTip(txHash, chainDataSupplier.getChainTipHeight());
        } catch (Exception e) {
            log.warn("Failed to get latest block for confirmation check: {}", e.getMessage());
            return observationFailure(txHash, new RuntimeException("Failed to get latest block", e));
        }
    }

    /** Observe several transaction hashes against one chain-tip snapshot. */
    Map<String, ConfirmationResult> checkStatuses(List<String> txHashes) {
        Map<String, ConfirmationResult> results = new LinkedHashMap<>();
        final long tipHeight;
        try {
            tipHeight = chainDataSupplier.getChainTipHeight();
        } catch (Exception e) {
            RuntimeException failure = new RuntimeException("Failed to get latest block", e);
            for (String txHash : txHashes) results.put(txHash, observationFailure(txHash, failure));
            return results;
        }
        for (String txHash : txHashes) results.put(txHash, checkStatusAtTip(txHash, tipHeight));
        return results;
    }

    private ConfirmationResult checkStatusAtTip(String txHash, long tipHeight) {
        try {
            var txInfo = chainDataSupplier.getTransactionInfo(txHash);
            TrackedTransaction previousState = trackedTransactions.get(txHash);

            if (txInfo.isEmpty()) {
                if (previousState != null && previousState.getBlockHeight() != null) {
                    if (supportsAuthoritativeAbsence() && tipHeight >= previousState.getBlockHeight()) {
                        int count = authoritativeAbsenceCounts.merge(txHash, 1, Integer::sum);
                        if (count >= config.getRequiredAuthoritativeAbsences()) {
                            log.warn("Rollback detected for tx {} after {} authoritative absence observations",
                                    txHash, count);
                            return ConfirmationResult.rolledBack(txHash, previousState.getBlockHeight(), tipHeight,
                                    new RuntimeException("Transaction authoritatively absent after prior inclusion"));
                        }
                        log.debug("Authoritative absence {}/{} for tx {}",
                                count, config.getRequiredAuthoritativeAbsences(), txHash);
                    } else {
                        log.debug("Ambiguous absence for previously included tx {}; continuing reconciliation", txHash);
                    }
                }
                return ConfirmationResult.submitted(txHash, tipHeight);
            }

            authoritativeAbsenceCounts.remove(txHash);
            TransactionInfo tx = txInfo.get();
            Long txBlockHeight = tx.getBlockHeight();
            String txBlockHash = tx.getBlockHash();
            if (txBlockHeight == null) return ConfirmationResult.submitted(txHash, tipHeight);

            if (previousState != null && previousState.getBlockHash() != null
                    && !previousState.getBlockHash().equals(txBlockHash)) {
                log.info("Transaction {} was re-included: old block={}, new block={}",
                        txHash, previousState.getBlockHash(), txBlockHash);
            }

            int depth = (int) (tipHeight - txBlockHeight);
            ConfirmationStatus status;
            if (depth < 0) {
                log.warn("Transaction {} has negative depth: block={}, tip={}",
                        txHash, txBlockHeight, tipHeight);
                status = ConfirmationStatus.IN_BLOCK;
                depth = 0;
            } else if (depth < config.getMinConfirmations()) {
                status = ConfirmationStatus.IN_BLOCK;
            } else {
                status = ConfirmationStatus.CONFIRMED;
            }

            trackedTransactions.put(txHash, new TrackedTransaction(
                    txHash, txBlockHeight, txBlockHash, status, scheduler.now()));
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
            return observationFailure(txHash, e);
        }
    }

    private ConfirmationResult observationFailure(String txHash, Throwable error) {
        return ConfirmationResult.builder()
                .txHash(txHash)
                .status(ConfirmationStatus.SUBMITTED)
                .confirmationDepth(-1)
                .error(error)
                .build();
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
        return waitForConfirmation(txHash, targetStatus, onProgress, () -> false);
    }

    /**
     * Wait for a transaction to reach the target confirmation status with progress callback
     * and cancellation support.
     *
     * @param txHash the transaction hash to monitor
     * @param targetStatus the status to wait for
     * @param onProgress optional callback invoked on each status check (txHash, result)
     * @param isCancelledCheck supplier that returns true when the flow has been cancelled
     * @return the final confirmation result
     */
    public ConfirmationResult waitForConfirmation(String txHash, ConfirmationStatus targetStatus,
                                                   BiConsumer<String, ConfirmationResult> onProgress,
                                                   BooleanSupplier isCancelledCheck) {
        return waitForConfirmation(txHash, targetStatus, onProgress, isCancelledCheck,
                scheduler.now().plus(config.getTimeout()), false);
    }

    /**
     * Wait using a caller-owned absolute deadline. When {@code continueAfterRollback}
     * is true, authoritative rollback observations remain part of the same tracking
     * attempt and polling continues so the same hash can be observed re-included.
     *
     * @param txHash transaction hash to monitor
     * @param targetStatus confirmation status that completes the wait successfully
     * @param onProgress optional callback invoked when the observed confirmation depth changes
     * @param isCancelledCheck cooperative cancellation signal
     * @param deadline absolute deadline shared with the owning execution budget
     * @param continueAfterRollback whether to keep observing the same hash after authoritative rollback
     * @return final confirmation, rollback, cancellation, or timeout result
     */
    public ConfirmationResult waitForConfirmation(
            String txHash, ConfirmationStatus targetStatus,
            BiConsumer<String, ConfirmationResult> onProgress,
            BooleanSupplier isCancelledCheck, Instant deadline,
            boolean continueAfterRollback) {
        Objects.requireNonNull(deadline, "deadline");

        ConfirmationResult lastResult = null;
        int lastDepth = -2; // Initialize to invalid value to ensure first callback fires

        while (scheduler.now().isBefore(deadline)) {
            // Check for cancellation
            if (isCancelledCheck.getAsBoolean()) {
                log.info("Cancellation detected while waiting for confirmation of tx {}", txHash);
                return ConfirmationResult.builder()
                        .txHash(txHash)
                        .status(lastResult != null ? lastResult.getStatus() : ConfirmationStatus.SUBMITTED)
                        .confirmationDepth(lastResult != null ? lastResult.getConfirmationDepth() : -1)
                        .error(new CancellationException("Flow cancelled"))
                        .build();
            }

            lastResult = checkStatus(txHash);

            // Notify progress if callback provided and depth changed
            if (onProgress != null && lastResult.getConfirmationDepth() != lastDepth) {
                onProgress.accept(txHash, lastResult);
                lastDepth = lastResult.getConfirmationDepth();
            }

            // Check for terminal conditions
            if (lastResult.isRolledBack() && !continueAfterRollback) {
                log.warn("Transaction {} rolled back during confirmation wait", txHash);
                return lastResult;
            }

            if (lastResult.hasReached(targetStatus)) {
                log.debug("Transaction {} reached target status {}", txHash, targetStatus);
                return lastResult;
            }

            // Wait before next check
            try {
                sleepUntilNextCheck(deadline, isCancelledCheck);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                CancellationException cancellation = new CancellationException(
                        "Flow interrupted while waiting for confirmation");
                cancellation.initCause(e);
                return ConfirmationResult.builder()
                        .txHash(txHash)
                        .status(lastResult != null ? lastResult.getStatus() : ConfirmationStatus.SUBMITTED)
                        .confirmationDepth(lastResult != null ? lastResult.getConfirmationDepth() : -1)
                        .error(cancellation)
                        .build();
            }
        }

        // Timeout reached
        log.warn("Timeout waiting for tx {} to reach status {}", txHash, targetStatus);
        if (continueAfterRollback && lastResult != null && lastResult.isRolledBack()) {
            return lastResult;
        }
        return timeoutResult(txHash, lastResult);
    }

    /** Wait for a set of hashes under one overall timeout and one tip read per pass. */
    Map<String, ConfirmationResult> waitForConfirmations(
            List<String> txHashes, ConfirmationStatus targetStatus,
            BooleanSupplier isCancelledCheck) {
        Instant deadline = scheduler.now().plus(config.getTimeout());
        Map<String, ConfirmationResult> lastResults = new LinkedHashMap<>();

        while (scheduler.now().isBefore(deadline)) {
            if (isCancelledCheck.getAsBoolean()) {
                CancellationException cancelled = new CancellationException("Flow cancelled");
                for (String txHash : txHashes) {
                    ConfirmationResult last = lastResults.get(txHash);
                    lastResults.put(txHash, ConfirmationResult.builder()
                            .txHash(txHash)
                            .status(last != null ? last.getStatus() : ConfirmationStatus.SUBMITTED)
                            .confirmationDepth(last != null ? last.getConfirmationDepth() : -1)
                            .error(cancelled)
                            .build());
                }
                return lastResults;
            }

            lastResults = checkStatuses(txHashes);
            boolean allReached = true;
            for (ConfirmationResult result : lastResults.values()) {
                if (result.isRolledBack()) return lastResults;
                if (!result.hasReached(targetStatus)) allReached = false;
            }
            if (allReached) return lastResults;

            try {
                sleepUntilNextCheck(deadline, isCancelledCheck);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                CancellationException cancellation = new CancellationException(
                        "Flow interrupted while monitoring confirmations");
                cancellation.initCause(e);
                for (String txHash : txHashes) {
                    ConfirmationResult last = lastResults.get(txHash);
                    lastResults.put(txHash, ConfirmationResult.builder()
                            .txHash(txHash)
                            .status(last != null ? last.getStatus() : ConfirmationStatus.SUBMITTED)
                            .confirmationDepth(last != null ? last.getConfirmationDepth() : -1)
                            .error(cancellation)
                            .build());
                }
                return lastResults;
            }
        }

        Map<String, ConfirmationResult> terminal = new LinkedHashMap<>();
        for (String txHash : txHashes) {
            ConfirmationResult last = lastResults.get(txHash);
            terminal.put(txHash, last != null && last.hasReached(targetStatus)
                    ? last : timeoutResult(txHash, last));
        }
        return terminal;
    }

    private void sleepUntilNextCheck(Instant deadline, BooleanSupplier cancelled)
            throws InterruptedException {
        Duration remaining = Duration.between(scheduler.now(), deadline);
        if (remaining.isNegative() || remaining.isZero()) return;
        Duration delay = config.getCheckInterval().compareTo(remaining) <= 0
                ? config.getCheckInterval() : remaining;
        scheduler.sleep(delay, cancelled);
    }

    private ConfirmationResult timeoutResult(String txHash, ConfirmationResult lastResult) {
        Throwable terminalError = trackedTransactions.containsKey(txHash)
                ? new ReconciliationUncertainException(txHash)
                : new ConfirmationTimeoutException(txHash);
        return ConfirmationResult.builder()
                .txHash(txHash)
                .status(lastResult != null ? lastResult.getStatus() : ConfirmationStatus.SUBMITTED)
                .confirmationDepth(lastResult != null ? lastResult.getConfirmationDepth() : -1)
                .blockHeight(lastResult != null ? lastResult.getBlockHeight() : null)
                .blockHash(lastResult != null ? lastResult.getBlockHash() : null)
                .currentTipHeight(lastResult != null ? lastResult.getCurrentTipHeight() : null)
                .error(terminalError)
                .build();
    }

    /**
     * Remove a transaction from tracking.
     * <p>
     * Call this when a transaction is no longer needed for rollback detection,
     * such as after it has been confirmed or the flow has completed.
     *
     * @param txHash the transaction hash to stop tracking
     */
    public void stopTracking(String txHash) {
        trackedTransactions.remove(txHash);
        authoritativeAbsenceCounts.remove(txHash);
    }

    /**
     * Clear all tracked transactions.
     */
    public void clearTracking() {
        trackedTransactions.clear();
        authoritativeAbsenceCounts.clear();
    }

    private boolean supportsAuthoritativeAbsence() {
        return chainDataSupplier instanceof TransactionObservationCapabilities
                && ((TransactionObservationCapabilities) chainDataSupplier).supportsAuthoritativeAbsence();
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
