package com.bloxbean.cardano.client.watcher.chain;

import com.bloxbean.cardano.client.watcher.api.WatchStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Result of a complete chain execution.
 * 
 * Contains the overall status, individual step results, timing information,
 * and any errors that occurred during chain execution.
 */
public class ChainResult {
    
    private final String chainId;
    private final WatchStatus status;
    private final Map<String, StepResult> stepResults;
    private final Instant startedAt;
    private final Instant completedAt;
    private final Throwable error;
    private final String description;
    
    private ChainResult(Builder builder) {
        this.chainId = builder.chainId;
        this.status = builder.status;
        this.stepResults = Map.copyOf(builder.stepResults);
        this.startedAt = builder.startedAt;
        this.completedAt = builder.completedAt;
        this.error = builder.error;
        this.description = builder.description;
    }
    
    /**
     * Get the chain identifier.
     * 
     * @return the chain ID
     */
    public String getChainId() {
        return chainId;
    }
    
    /**
     * Get the overall chain status.
     * 
     * @return the chain status
     */
    public WatchStatus getStatus() {
        return status;
    }
    
    /**
     * Get the results of individual steps.
     * 
     * @return map of step ID to step result
     */
    public Map<String, StepResult> getStepResults() {
        return stepResults;
    }
    
    /**
     * Get when the chain started execution.
     * 
     * @return the start timestamp
     */
    public Instant getStartedAt() {
        return startedAt;
    }
    
    /**
     * Get when the chain completed execution.
     * 
     * @return the completion timestamp, or null if not completed
     */
    public Optional<Instant> getCompletedAt() {
        return Optional.ofNullable(completedAt);
    }
    
    /**
     * Get the execution duration.
     * 
     * @return the duration, or empty if not completed
     */
    public Optional<Duration> getDuration() {
        return completedAt != null ? 
            Optional.of(Duration.between(startedAt, completedAt)) : 
            Optional.empty();
    }
    
    /**
     * Get any error that occurred during chain execution.
     * 
     * @return the error, or empty if no error
     */
    public Optional<Throwable> getError() {
        return Optional.ofNullable(error);
    }
    
    /**
     * Get the chain description.
     * 
     * @return the description, or empty if none provided
     */
    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }
    
    /**
     * Check if the chain completed successfully.
     * 
     * @return true if the chain completed successfully
     */
    public boolean isSuccessful() {
        return status == WatchStatus.CONFIRMED;
    }
    
    /**
     * Check if the chain failed.
     * 
     * @return true if the chain failed
     */
    public boolean isFailed() {
        return status == WatchStatus.FAILED;
    }
    
    /**
     * Check if the chain was cancelled.
     * 
     * @return true if the chain was cancelled
     */
    public boolean isCancelled() {
        return status == WatchStatus.CANCELLED;
    }
    
    /**
     * Get all transaction hashes from successful steps.
     * 
     * @return list of transaction hashes
     */
    public List<String> getTransactionHashes() {
        return stepResults.values().stream()
            .filter(StepResult::isSuccessful)
            .map(StepResult::getTransactionHash)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    /**
     * Get the number of successful steps.
     * 
     * @return the count of successful steps
     */
    public int getSuccessfulStepCount() {
        return (int) stepResults.values().stream()
            .filter(StepResult::isSuccessful)
            .count();
    }
    
    /**
     * Get the number of failed steps.
     * 
     * @return the count of failed steps
     */
    public int getFailedStepCount() {
        return (int) stepResults.values().stream()
            .filter(result -> result.getStatus() == WatchStatus.FAILED)
            .count();
    }
    
    /**
     * Create a success result.
     * 
     * @param chainId the chain ID
     * @param stepResults the step results
     * @param startedAt when the chain started
     * @param completedAt when the chain completed
     * @return the success result
     */
    public static ChainResult success(String chainId, Map<String, StepResult> stepResults, 
                                      Instant startedAt, Instant completedAt) {
        return new Builder(chainId)
            .status(WatchStatus.CONFIRMED)
            .stepResults(stepResults)
            .startedAt(startedAt)
            .completedAt(completedAt)
            .build();
    }
    
    /**
     * Create a failure result.
     * 
     * @param chainId the chain ID
     * @param error the error that caused the failure
     * @param stepResults the step results so far
     * @param startedAt when the chain started
     * @param completedAt when the chain failed
     * @return the failure result
     */
    public static ChainResult failure(String chainId, Throwable error, 
                                      Map<String, StepResult> stepResults,
                                      Instant startedAt, Instant completedAt) {
        return new Builder(chainId)
            .status(WatchStatus.FAILED)
            .error(error)
            .stepResults(stepResults)
            .startedAt(startedAt)
            .completedAt(completedAt)
            .build();
    }
    
    /**
     * Create a cancelled result.
     * 
     * @param chainId the chain ID
     * @param stepResults the step results so far
     * @param startedAt when the chain started
     * @param completedAt when the chain was cancelled
     * @return the cancelled result
     */
    public static ChainResult cancelled(String chainId, Map<String, StepResult> stepResults,
                                        Instant startedAt, Instant completedAt) {
        return new Builder(chainId)
            .status(WatchStatus.CANCELLED)
            .stepResults(stepResults)
            .startedAt(startedAt)
            .completedAt(completedAt)
            .build();
    }
    
    /**
     * Create a builder for ChainResult.
     * 
     * @param chainId the chain ID
     * @return a new builder
     */
    public static Builder builder(String chainId) {
        return new Builder(chainId);
    }
    
    /**
     * Builder for ChainResult.
     */
    public static class Builder {
        private final String chainId;
        private WatchStatus status = WatchStatus.PENDING;
        private Map<String, StepResult> stepResults = new HashMap<>();
        private Instant startedAt = Instant.now();
        private Instant completedAt;
        private Throwable error;
        private String description;
        
        private Builder(String chainId) {
            this.chainId = chainId;
        }
        
        public Builder status(WatchStatus status) {
            this.status = status;
            return this;
        }
        
        public Builder stepResults(Map<String, StepResult> stepResults) {
            this.stepResults = new HashMap<>(stepResults);
            return this;
        }
        
        public Builder stepResult(String stepId, StepResult result) {
            this.stepResults.put(stepId, result);
            return this;
        }
        
        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }
        
        public Builder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }
        
        public Builder error(Throwable error) {
            this.error = error;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public ChainResult build() {
            return new ChainResult(this);
        }
    }
    
    @Override
    public String toString() {
        return "ChainResult{" +
                "chainId='" + chainId + '\'' +
                ", status=" + status +
                ", successfulSteps=" + getSuccessfulStepCount() +
                ", totalSteps=" + stepResults.size() +
                ", duration=" + getDuration().map(Duration::toString).orElse("ongoing") +
                '}';
    }
}