package com.bloxbean.cardano.client.watcher.chain;

import com.bloxbean.cardano.client.watcher.api.WatchHandle;
import com.bloxbean.cardano.client.watcher.api.WatchResult;
import com.bloxbean.cardano.client.watcher.api.WatchStatus;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Basic implementation of WatchHandle for managing chain execution status.
 * 
 * This implementation provides simple status tracking and step result management
 * for transaction chains.
 */
public class BasicWatchHandle extends WatchHandle {
    private final String chainId;
    private final int totalSteps;
    private final Instant startedAt;
    private final Map<String, WatchStatus> stepStatuses;
    private final Map<String, StepResult> stepResults;
    
    private volatile WatchStatus status;
    private volatile Throwable lastError;
    private volatile Instant completedAt;
    
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
        this.status = WatchStatus.PENDING;
    }
    
    public String getChainId() {
        return chainId;
    }
    
    public WatchStatus getStatus() {
        return status;
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
            super.cancel(); // Cancel the underlying future
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
    }
    
    /**
     * Mark the chain as completed successfully.
     */
    public void markCompleted() {
        this.status = WatchStatus.CONFIRMED;
        this.completedAt = Instant.now();
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
            return;
        }
        
        // Check if all steps completed
        long completedSteps = stepStatuses.values().stream()
            .mapToLong(s -> s == WatchStatus.CONFIRMED ? 1 : 0)
            .sum();
        
        if (completedSteps == totalSteps) {
            this.status = WatchStatus.CONFIRMED;
            this.completedAt = Instant.now();
        } else if (!stepStatuses.isEmpty()) {
            this.status = WatchStatus.WATCHING;
        }
    }
    
    @Override
    public String toString() {
        return "BasicWatchHandle{" +
                "chainId='" + chainId + '\'' +
                ", status=" + status +
                ", totalSteps=" + totalSteps +
                ", completedSteps=" + stepResults.size() +
                ", startedAt=" + startedAt +
                ", completedAt=" + completedAt +
                '}';
    }
}