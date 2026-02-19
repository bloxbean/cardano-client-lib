package com.bloxbean.cardano.client.txflow.exec.store;

import com.bloxbean.cardano.client.txflow.result.FlowStatus;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory implementation of {@link FlowStateStore}.
 * <p>
 * This implementation is suitable for:
 * <ul>
 *     <li>Testing and development environments</li>
 *     <li>Single-instance applications that don't need durable persistence</li>
 *     <li>Reference implementation for understanding the interface contract</li>
 * </ul>
 * <p>
 * Features:
 * <ul>
 *     <li>Thread-safe using ConcurrentHashMap</li>
 *     <li>Optional auto-cleanup of completed flows</li>
 *     <li>Deep-copy on retrieval to prevent external modification</li>
 *     <li>Utility methods for inspection and debugging</li>
 * </ul>
 * <p>
 * Example:
 * <pre>{@code
 * // Basic usage
 * FlowStateStore store = new InMemoryFlowStateStore();
 *
 * // With auto-cleanup (remove completed flows after 5 minutes)
 * FlowStateStore store = InMemoryFlowStateStore.builder()
 *     .withAutoCleanup(Duration.ofMinutes(5))
 *     .build();
 * }</pre>
 * <p>
 * <b>Note:</b> This implementation does not survive application restarts.
 * For production environments requiring durability, implement FlowStateStore
 * using a persistent storage mechanism (database, Redis, etc.).
 */
@Slf4j
public class InMemoryFlowStateStore implements FlowStateStore {

    private final ConcurrentMap<String, FlowStateSnapshot> flows = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor;
    private final Duration autoCleanupDelay;

    /**
     * Create a new InMemoryFlowStateStore with no auto-cleanup.
     */
    public InMemoryFlowStateStore() {
        this(null);
    }

    /**
     * Create a new InMemoryFlowStateStore with optional auto-cleanup.
     *
     * @param autoCleanupDelay delay after completion before removing flows, or null to disable
     */
    public InMemoryFlowStateStore(Duration autoCleanupDelay) {
        this.autoCleanupDelay = autoCleanupDelay;
        if (autoCleanupDelay != null && !autoCleanupDelay.isZero()) {
            this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "flow-state-store-cleanup");
                t.setDaemon(true);
                return t;
            });
        } else {
            this.cleanupExecutor = null;
        }
    }

    @Override
    public void saveFlowState(FlowStateSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot cannot be null");
        Objects.requireNonNull(snapshot.getFlowId(), "flowId cannot be null");

        // Store a deep copy to prevent external modification
        FlowStateSnapshot copy = deepCopy(snapshot);
        FlowStateSnapshot previous = flows.put(copy.getFlowId(), copy);

        if (previous != null) {
            log.debug("Replaced existing flow state: {}", copy.getFlowId());
        } else {
            log.debug("Saved new flow state: {} (status: {})", copy.getFlowId(), copy.getStatus());
        }
    }

    @Override
    public List<FlowStateSnapshot> loadPendingFlows() {
        return flows.values().stream()
                .filter(this::isPending)
                .map(this::deepCopy)
                .collect(Collectors.toList());
    }

    @Override
    public void updateTransactionState(String flowId, String stepId, String txHash, TransactionStateDetails details) {
        Objects.requireNonNull(flowId, "flowId cannot be null");
        Objects.requireNonNull(stepId, "stepId cannot be null");
        Objects.requireNonNull(details, "details cannot be null");

        flows.compute(flowId, (key, snapshot) -> {
            if (snapshot == null) {
                log.warn("Cannot update transaction state for unknown flow: {}", flowId);
                return null;
            }

            // Find and update the step
            StepStateSnapshot step = snapshot.getStep(stepId);
            if (step == null) {
                // Create new step if not exists
                step = StepStateSnapshot.builder()
                        .stepId(stepId)
                        .transactionHash(txHash)
                        .build();
                snapshot.addStep(step);
            }

            // Update step with transaction details
            step.setTransactionHash(txHash);
            step.setState(details.getState());
            step.setBlockHeight(details.getBlockHeight());
            step.setConfirmationDepth(details.getConfirmationDepth());
            step.setLastChecked(details.getTimestamp());
            step.setErrorMessage(details.getErrorMessage());

            if (details.getState() == TransactionState.SUBMITTED) {
                step.setSubmittedAt(details.getTimestamp());
            } else if (details.getState() == TransactionState.CONFIRMED) {
                step.setConfirmedAt(details.getTimestamp());
            }

            log.debug("Updated transaction state for flow {} step {}: {} (tx: {})",
                    flowId, stepId, details.getState(), txHash);

            return snapshot;
        });
    }

    @Override
    public void markFlowComplete(String flowId, FlowStatus status) {
        Objects.requireNonNull(flowId, "flowId cannot be null");
        Objects.requireNonNull(status, "status cannot be null");

        flows.compute(flowId, (key, snapshot) -> {
            if (snapshot == null) {
                log.warn("Cannot mark completion for unknown flow: {}", flowId);
                return null;
            }

            snapshot.setStatus(status);
            snapshot.setCompletedAt(Instant.now());

            // Update completed step count
            if (snapshot.getSteps() != null) {
                int completedSteps = (int) snapshot.getSteps().stream()
                        .filter(s -> s.getState() != null && s.getState().isSuccessful())
                        .count();
                snapshot.setCompletedSteps(completedSteps);
            }

            log.debug("Marked flow {} as {} (completed steps: {}/{})",
                    flowId, status, snapshot.getCompletedSteps(), snapshot.getTotalSteps());

            // Schedule auto-cleanup if enabled
            if (autoCleanupDelay != null && cleanupExecutor != null) {
                scheduleCleanup(flowId);
            }

            return snapshot;
        });
    }

    @Override
    public Optional<FlowStateSnapshot> getFlowState(String flowId) {
        Objects.requireNonNull(flowId, "flowId cannot be null");

        FlowStateSnapshot snapshot = flows.get(flowId);
        if (snapshot == null) {
            return Optional.empty();
        }
        // Return a deep copy to prevent external modification
        return Optional.of(deepCopy(snapshot));
    }

    @Override
    public boolean deleteFlow(String flowId) {
        Objects.requireNonNull(flowId, "flowId cannot be null");

        FlowStateSnapshot removed = flows.remove(flowId);
        if (removed != null) {
            log.debug("Deleted flow: {}", flowId);
            return true;
        }
        return false;
    }

    // ========== Additional utility methods ==========

    /**
     * Get all flow states currently stored.
     * <p>
     * This method is useful for debugging and inspection.
     *
     * @return list of all flow snapshots (deep copies)
     */
    public List<FlowStateSnapshot> getAllFlows() {
        return flows.values().stream()
                .map(this::deepCopy)
                .collect(Collectors.toList());
    }

    /**
     * Get the count of flows currently stored.
     *
     * @return the number of flows in the store
     */
    public int getFlowCount() {
        return flows.size();
    }

    /**
     * Get flows by status.
     *
     * @param status the status to filter by
     * @return list of flows with the specified status (deep copies)
     */
    public List<FlowStateSnapshot> getFlowsByStatus(FlowStatus status) {
        Objects.requireNonNull(status, "status cannot be null");

        return flows.values().stream()
                .filter(s -> s.getStatus() == status)
                .map(this::deepCopy)
                .collect(Collectors.toList());
    }

    /**
     * Check if a flow exists in the store.
     *
     * @param flowId the flow identifier
     * @return true if the flow exists
     */
    public boolean contains(String flowId) {
        Objects.requireNonNull(flowId, "flowId cannot be null");
        return flows.containsKey(flowId);
    }

    /**
     * Clear all flows from the store.
     * <p>
     * This method is useful for testing and cleanup.
     */
    public void clear() {
        flows.clear();
        log.debug("Cleared all flows from store");
    }

    /**
     * Shutdown the store and release resources.
     * <p>
     * This stops the auto-cleanup executor if enabled.
     */
    public void shutdown() {
        if (cleanupExecutor != null) {
            cleanupExecutor.shutdown();
            try {
                if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        clear();
    }

    // ========== Private helper methods ==========

    private boolean isPending(FlowStateSnapshot snapshot) {
        if (snapshot == null) return false;

        // Flow is pending if it's in progress or has pending transactions
        return snapshot.isInProgress() || snapshot.hasPendingTransactions();
    }

    private void scheduleCleanup(String flowId) {
        cleanupExecutor.schedule(() -> {
            FlowStateSnapshot snapshot = flows.get(flowId);
            if (snapshot != null && !snapshot.isInProgress()) {
                flows.remove(flowId);
                log.debug("Auto-cleaned flow after completion: {}", flowId);
            }
        }, autoCleanupDelay.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Create a deep copy of a FlowStateSnapshot.
     * <p>
     * This ensures that modifications to the returned snapshot
     * do not affect the stored snapshot.
     */
    private FlowStateSnapshot deepCopy(FlowStateSnapshot original) {
        if (original == null) return null;

        // Copy steps
        List<StepStateSnapshot> stepsCopy = null;
        if (original.getSteps() != null) {
            stepsCopy = original.getSteps().stream()
                    .map(this::deepCopyStep)
                    .collect(Collectors.toList());
        }

        // Copy variables
        Map<String, Object> variablesCopy = null;
        if (original.getVariables() != null) {
            variablesCopy = new HashMap<>(original.getVariables());
        }

        // Copy metadata
        Map<String, String> metadataCopy = null;
        if (original.getMetadata() != null) {
            metadataCopy = new HashMap<>(original.getMetadata());
        }

        return FlowStateSnapshot.builder()
                .flowId(original.getFlowId())
                .status(original.getStatus())
                .startedAt(original.getStartedAt())
                .completedAt(original.getCompletedAt())
                .steps(stepsCopy != null ? stepsCopy : new ArrayList<>())
                .variables(variablesCopy != null ? variablesCopy : new HashMap<>())
                .description(original.getDescription())
                .totalSteps(original.getTotalSteps())
                .completedSteps(original.getCompletedSteps())
                .metadata(metadataCopy != null ? metadataCopy : new HashMap<>())
                .build();
    }

    private StepStateSnapshot deepCopyStep(StepStateSnapshot original) {
        if (original == null) return null;

        return StepStateSnapshot.builder()
                .stepId(original.getStepId())
                .transactionHash(original.getTransactionHash())
                .state(original.getState())
                .submittedAt(original.getSubmittedAt())
                .blockHeight(original.getBlockHeight())
                .confirmationDepth(original.getConfirmationDepth())
                .lastChecked(original.getLastChecked())
                .confirmedAt(original.getConfirmedAt())
                .errorMessage(original.getErrorMessage())
                .build();
    }

    /**
     * Create a builder for configuring InMemoryFlowStateStore.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for InMemoryFlowStateStore.
     */
    public static class Builder {
        private Duration autoCleanupDelay;

        /**
         * Enable auto-cleanup of completed flows after the specified delay.
         *
         * @param delay time to wait after completion before removing
         * @return this builder
         */
        public Builder withAutoCleanup(Duration delay) {
            this.autoCleanupDelay = delay;
            return this;
        }

        /**
         * Build the store.
         *
         * @return a new InMemoryFlowStateStore
         */
        public InMemoryFlowStateStore build() {
            return new InMemoryFlowStateStore(autoCleanupDelay);
        }
    }
}
