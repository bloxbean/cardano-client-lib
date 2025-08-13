package com.bloxbean.cardano.client.watcher.chain;

import com.bloxbean.cardano.client.watcher.api.WatchHandle;
import com.bloxbean.cardano.client.watcher.api.WatchResult;
import com.bloxbean.cardano.client.watcher.api.WatchStatus;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Basic implementation of WatchHandle for managing chain execution status.
 * 
 * This implementation provides comprehensive status tracking, step result management,
 * async completion monitoring, and progress callbacks for transaction chains.
 */
@Slf4j
public class BasicWatchHandle extends WatchHandle {
    private final String chainId;
    private final int totalSteps;
    private final Instant startedAt;
    private final Map<String, WatchStatus> stepStatuses;
    private final Map<String, StepResult> stepResults;
    private final CompletableFuture<ChainResult> chainFuture;
    private final List<Consumer<StepResult>> stepListeners;
    private final List<Consumer<ChainResult>> chainListeners;
    private final List<String> stepOrder; // Maintains the original execution order
    
    private volatile WatchStatus status;
    private volatile Throwable lastError;
    private volatile Instant completedAt;
    private volatile String description;
    
    /**
     * Create a new BasicWatchHandle.
     * 
     * @param chainId the chain identifier
     * @param totalSteps the total number of steps in the chain
     */
    public BasicWatchHandle(String chainId, int totalSteps) {
        super(chainId, new CompletableFuture<>());
        this.chainId = chainId;
        this.totalSteps = totalSteps;
        this.startedAt = Instant.now();
        this.stepStatuses = new ConcurrentHashMap<>();
        this.stepResults = new ConcurrentHashMap<>();
        this.chainFuture = new CompletableFuture<>();
        this.stepListeners = new CopyOnWriteArrayList<>();
        this.chainListeners = new CopyOnWriteArrayList<>();
        this.stepOrder = new ArrayList<>();
        this.status = WatchStatus.PENDING;
    }
    
    /**
     * Create a new BasicWatchHandle with description.
     * 
     * @param chainId the chain identifier
     * @param totalSteps the total number of steps in the chain
     * @param description the chain description
     */
    public BasicWatchHandle(String chainId, int totalSteps, String description) {
        this(chainId, totalSteps);
        this.description = description;
    }
    
    public String getChainId() {
        return chainId;
    }
    
    public WatchStatus getStatus() {
        return status;
    }
    
    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }
    
    /**
     * Get the CompletableFuture for chain completion.
     * 
     * @return the chain completion future
     */
    public CompletableFuture<ChainResult> getChainFuture() {
        return chainFuture;
    }
    
    public boolean isCompleted() {
        return status == WatchStatus.CONFIRMED || status == WatchStatus.FAILED || status == WatchStatus.CANCELLED;
    }
    
    public boolean isSuccessful() {
        return status == WatchStatus.CONFIRMED;
    }
    
    public Optional<Throwable> getError() {
        return Optional.ofNullable(lastError);
    }
    
    public Optional<String> getTransactionHash() {
        // For chains, we could return the hash of the last successful step
        // For now, return empty since chains contain multiple transactions
        return Optional.empty();
    }
    
    public List<String> getTransactionHashes() {
        return stepResults.values().stream()
            .filter(StepResult::isSuccessful)
            .map(StepResult::getTransactionHash)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    public void cancelChain() {
        if (!isCompleted()) {
            this.status = WatchStatus.CANCELLED;
            this.completedAt = Instant.now();
            
            // Cancel the parent future first before completing our chain future
            super.cancel();
            
            // Complete our chain future with cancelled result
            if (!chainFuture.isDone()) {
                ChainResult result = buildChainResult();
                chainFuture.complete(result);
                notifyChainListeners(result);
            }
        }
    }
    
    /**
     * Update the status of a specific step.
     * 
     * @param stepId the step ID
     * @param stepStatus the new status
     */
    public void updateStepStatus(String stepId, WatchStatus stepStatus) {
        stepStatuses.put(stepId, stepStatus);
        updateOverallStatus();
    }
    
    /**
     * Record the result of a step execution.
     * 
     * @param stepId the step ID
     * @param result the step result
     */
    public void recordStepResult(String stepId, StepResult result) {
        stepResults.put(stepId, result);
        stepStatuses.put(stepId, result.getStatus());
        
        // Notify step listeners
        notifyStepListeners(result);
        
        updateOverallStatus();
    }
    
    /**
     * Mark the chain as failed.
     * 
     * @param error the error that caused the failure
     */
    public void markFailed(Throwable error) {
        this.status = WatchStatus.FAILED;
        this.lastError = error;
        this.completedAt = Instant.now();
        completeChainFuture();
    }
    
    /**
     * Mark the chain as completed successfully.
     */
    public void markCompleted() {
        this.status = WatchStatus.CONFIRMED;
        this.completedAt = Instant.now();
        completeChainFuture();
    }
    
    /**
     * Initialize all planned steps with PENDING status.
     * This ensures all steps are visible in visualization even if chain fails early.
     * 
     * @param stepIds the list of all planned step IDs in execution order
     */
    public void initializePlannedSteps(List<String> stepIds) {
        // Store the original step order
        stepOrder.clear();
        stepOrder.addAll(stepIds);
        
        // Initialize all steps with PENDING status
        for (String stepId : stepIds) {
            stepStatuses.putIfAbsent(stepId, WatchStatus.PENDING);
        }
        updateOverallStatus();
    }
    
    /**
     * Get the step statuses.
     * 
     * @return map of step ID to status
     */
    public Map<String, WatchStatus> getStepStatuses() {
        return Map.copyOf(stepStatuses);
    }
    
    /**
     * Get the step results.
     * 
     * @return map of step ID to result
     */
    public Map<String, StepResult> getStepResults() {
        return Map.copyOf(stepResults);
    }
    
    /**
     * Get the step IDs in their original execution order.
     * 
     * @return list of step IDs in execution order
     */
    public List<String> getStepOrder() {
        return List.copyOf(stepOrder);
    }
    
    /**
     * Get when the chain started.
     * 
     * @return the start timestamp
     */
    public Instant getStartedAt() {
        return startedAt;
    }
    
    /**
     * Get when the chain completed.
     * 
     * @return the completion timestamp, or null if not completed
     */
    public Optional<Instant> getCompletedAt() {
        return Optional.ofNullable(completedAt);
    }
    
    /**
     * Update the overall chain status based on step statuses.
     */
    private void updateOverallStatus() {
        if (status == WatchStatus.FAILED || status == WatchStatus.CANCELLED || status == WatchStatus.CONFIRMED) {
            return; // Don't update if already in final state
        }
        
        // Check if any step failed
        boolean hasFailure = stepStatuses.values().stream()
            .anyMatch(s -> s == WatchStatus.FAILED);
        
        if (hasFailure) {
            this.status = WatchStatus.FAILED;
            this.completedAt = Instant.now();
            completeChainFuture();
            return;
        }
        
        // Check if all steps completed
        long completedSteps = stepStatuses.values().stream()
            .mapToLong(s -> s == WatchStatus.CONFIRMED ? 1 : 0)
            .sum();
        
        if (completedSteps == totalSteps) {
            this.status = WatchStatus.CONFIRMED;
            this.completedAt = Instant.now();
            completeChainFuture();
        } else if (!stepStatuses.isEmpty()) {
            this.status = WatchStatus.WATCHING;
        }
    }
    
    // ========== Monitoring and Callback Methods ==========
    
    /**
     * Add a listener for step completion events.
     * 
     * @param listener the step completion listener
     */
    public void onStepComplete(Consumer<StepResult> listener) {
        if (log.isDebugEnabled()) {
            log.debug("Adding step completion listener. Total listeners: {}", stepListeners.size() + 1);
        }
        stepListeners.add(listener);
    }
    
    /**
     * Add a listener for chain completion events.
     * 
     * @param listener the chain completion listener
     */
    public void onChainComplete(Consumer<ChainResult> listener) {
        chainListeners.add(listener);
        
        // If already completed, notify immediately
        if (isCompleted()) {
            listener.accept(buildChainResult());
        }
    }
    
    /**
     * Wait for chain completion with timeout.
     * 
     * @param timeout the maximum time to wait
     * @return the chain result
     * @throws TimeoutException if the timeout is exceeded
     * @throws InterruptedException if the current thread is interrupted
     * @throws ExecutionException if the chain execution failed
     */
    public ChainResult await(Duration timeout) throws TimeoutException, InterruptedException, ExecutionException {
        try {
            return chainFuture.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new TimeoutException("Chain execution timeout after " + timeout + ": " + chainId);
        }
    }
    
    /**
     * Wait for chain completion indefinitely.
     * 
     * @return the chain result
     * @throws InterruptedException if the current thread is interrupted
     * @throws ExecutionException if the chain execution failed
     */
    public ChainResult await() throws InterruptedException, ExecutionException {
        return chainFuture.get();
    }
    
    /**
     * Get the current chain result (may be incomplete).
     * 
     * @return the current chain result
     */
    public ChainResult getCurrentResult() {
        return buildChainResult();
    }
    
    /**
     * Get progress as a percentage (0.0 to 1.0).
     * 
     * @return the completion progress
     */
    public double getProgress() {
        if (totalSteps == 0) return 1.0;
        
        long completedSteps = stepStatuses.values().stream()
            .mapToLong(s -> s == WatchStatus.CONFIRMED ? 1 : 0)
            .sum();
            
        return (double) completedSteps / totalSteps;
    }
    
    // ========== Private Helper Methods ==========
    
    /**
     * Notify all step listeners of a step completion.
     */
    private void notifyStepListeners(StepResult stepResult) {
        if (log.isDebugEnabled()) {
            log.debug("Notifying {} step listeners for step: {} - {}", 
                stepListeners.size(), stepResult.getStepId(), stepResult.getStatus());
        }
        
        for (Consumer<StepResult> listener : stepListeners) {
            try {
                if (log.isTraceEnabled()) {
                    log.trace("Calling step listener...");
                }
                listener.accept(stepResult);
                if (log.isTraceEnabled()) {
                    log.trace("Step listener called successfully");
                }
            } catch (Exception e) {
                // Log error but don't fail the chain
                log.error("Error in step listener", e);
            }
        }
    }
    
    /**
     * Notify all chain listeners of chain completion.
     */
    private void notifyChainListeners(ChainResult chainResult) {
        for (Consumer<ChainResult> listener : chainListeners) {
            try {
                listener.accept(chainResult);
            } catch (Exception e) {
                // Log error but don't fail the completion
                log.error("Error in chain listener", e);
            }
        }
    }
    
    /**
     * Complete the chain future with the current result.
     */
    private void completeChainFuture() {
        if (!chainFuture.isDone()) {
            ChainResult result = buildChainResult();
            chainFuture.complete(result);
            notifyChainListeners(result);
            
            // Also complete the parent WatchHandle's future
            // Convert ChainResult to WatchResult for compatibility
            if (!super.getFuture().isDone()) {
                if (result.isSuccessful()) {
                    // For successful chains, create a success WatchResult
                    // We don't have a single transaction, so we pass null for transaction
                    // and empty list for outputs (chain-level outputs would need aggregation)
                    super.getFuture().complete(WatchResult.success(null, Collections.emptyList()));
                } else {
                    // For failed chains, create a failure WatchResult
                    if (result.getError().isPresent()) {
                        super.getFuture().complete(WatchResult.failure(result.getError().get().getMessage()));
                    } else {
                        super.getFuture().complete(WatchResult.failure("Chain execution failed"));
                    }
                }
            }
        }
    }
    
    /**
     * Build a ChainResult from the current state.
     */
    private ChainResult buildChainResult() {
        ChainResult.Builder builder = ChainResult.builder(chainId)
            .status(status)
            .stepResults(stepResults)
            .startedAt(startedAt)
            .description(description);
            
        if (completedAt != null) {
            builder.completedAt(completedAt);
        }
        
        if (lastError != null) {
            builder.error(lastError);
        }
        
        return builder.build();
    }
    
    @Override
    public String toString() {
        return "BasicWatchHandle{" +
                "chainId='" + chainId + '\'' +
                ", status=" + status +
                ", progress=" + String.format("%.1f%%", getProgress() * 100) +
                ", totalSteps=" + totalSteps +
                ", completedSteps=" + stepResults.size() +
                ", startedAt=" + startedAt +
                ", completedAt=" + completedAt +
                '}';
    }
}