package com.bloxbean.cardano.statetrees.rocksdb.gc.modern;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable progress information for garbage collection operations.
 * 
 * <p>This class captures the current state of a GC operation, including
 * work completed, time elapsed, current phase, and estimated completion.
 * It's designed to be lightweight and immutable for safe sharing across
 * threads and frequent progress updates.</p>
 * 
 * <p><b>Key Metrics:</b></p>
 * <ul>
 *   <li>Work progress (completed vs total units)</li>
 *   <li>Time tracking (elapsed and estimated remaining)</li>
 *   <li>Phase information (current operation being performed)</li>
 *   <li>Percentage completion with precision handling</li>
 * </ul>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // Create progress tracker
 * Instant startTime = Instant.now();
 * long totalNodes = repository.getTotalNodeCount();
 * 
 * // Update progress as work completes
 * for (int i = 0; i < totalNodes; i++) {
 *     // ... do work ...
 *     
 *     GcProgress progress = GcProgress.builder()
 *         .startTime(startTime)
 *         .currentTime(Instant.now())
 *         .completedWork(i + 1)
 *         .totalWork(totalNodes)
 *         .currentPhase("Marking unreachable nodes")
 *         .build();
 *         
 *     context.reportProgress(progress);
 * }
 * }</pre>
 * 
 * @author Bloxbean Project
 * @since 0.6.0
 */
public final class GcProgress {
    
    private final Instant startTime;
    private final Instant currentTime;
    private final long completedWork;
    private final long totalWork;
    private final String currentPhase;
    private final String additionalInfo;
    
    /**
     * Private constructor - use builder to create instances.
     */
    private GcProgress(Builder builder) {
        this.startTime = Objects.requireNonNull(builder.startTime, "Start time cannot be null");
        this.currentTime = Objects.requireNonNull(builder.currentTime, "Current time cannot be null");
        this.completedWork = Math.max(0, builder.completedWork);
        this.totalWork = Math.max(1, builder.totalWork); // Avoid division by zero
        this.currentPhase = builder.currentPhase != null ? builder.currentPhase : "Processing";
        this.additionalInfo = builder.additionalInfo; // Can be null
        
        // Validation
        if (completedWork > totalWork) {
            throw new IllegalArgumentException("Completed work cannot exceed total work");
        }
        if (currentTime.isBefore(startTime)) {
            throw new IllegalArgumentException("Current time cannot be before start time");
        }
    }
    
    /**
     * Returns the start time of the GC operation.
     * 
     * @return the start time
     */
    public Instant getStartTime() {
        return startTime;
    }
    
    /**
     * Returns the current time when this progress was created.
     * 
     * @return the current time
     */
    public Instant getCurrentTime() {
        return currentTime;
    }
    
    /**
     * Returns the amount of work completed so far.
     * 
     * @return completed work units
     */
    public long getCompletedWork() {
        return completedWork;
    }
    
    /**
     * Returns the total amount of work to be completed.
     * 
     * @return total work units
     */
    public long getTotalWork() {
        return totalWork;
    }
    
    /**
     * Returns the current phase or operation being performed.
     * 
     * @return the current phase description
     */
    public String getCurrentPhase() {
        return currentPhase;
    }
    
    /**
     * Returns additional information about the current state, if any.
     * 
     * @return additional info, or null if none
     */
    public String getAdditionalInfo() {
        return additionalInfo;
    }
    
    /**
     * Returns the percentage of work completed (0.0 to 100.0).
     * 
     * @return completion percentage
     */
    public double getPercentComplete() {
        return (double) completedWork / totalWork * 100.0;
    }
    
    /**
     * Returns the duration elapsed since the start of the operation.
     * 
     * @return elapsed duration
     */
    public Duration getElapsedTime() {
        return Duration.between(startTime, currentTime);
    }
    
    /**
     * Estimates the remaining time based on current progress rate.
     * 
     * <p>This estimate assumes a constant work rate and may not be accurate
     * for operations with variable complexity per unit of work.</p>
     * 
     * @return estimated remaining duration, or null if cannot estimate
     */
    public Duration getEstimatedTimeRemaining() {
        if (completedWork == 0) {
            return null; // Cannot estimate with no completed work
        }
        
        Duration elapsed = getElapsedTime();
        double workRate = (double) completedWork / elapsed.toMillis();
        long remainingWork = totalWork - completedWork;
        
        if (workRate <= 0 || remainingWork <= 0) {
            return Duration.ZERO;
        }
        
        long remainingMillis = (long) (remainingWork / workRate);
        return Duration.ofMillis(remainingMillis);
    }
    
    /**
     * Estimates the total time for the operation to complete.
     * 
     * @return estimated total duration, or null if cannot estimate
     */
    public Duration getEstimatedTotalTime() {
        Duration remaining = getEstimatedTimeRemaining();
        return remaining != null ? getElapsedTime().plus(remaining) : null;
    }
    
    /**
     * Returns the current work rate (units per second).
     * 
     * @return work rate in units per second
     */
    public double getWorkRatePerSecond() {
        Duration elapsed = getElapsedTime();
        if (elapsed.isZero()) {
            return 0.0;
        }
        return (double) completedWork / elapsed.getSeconds();
    }
    
    /**
     * Checks if the operation is complete.
     * 
     * @return true if all work is complete
     */
    public boolean isComplete() {
        return completedWork >= totalWork;
    }
    
    /**
     * Creates a new builder for constructing GcProgress instances.
     * 
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Creates a new builder initialized with values from this progress.
     * 
     * @return a builder with current values
     */
    public Builder toBuilder() {
        return new Builder()
            .startTime(startTime)
            .currentTime(currentTime)
            .completedWork(completedWork)
            .totalWork(totalWork)
            .currentPhase(currentPhase)
            .additionalInfo(additionalInfo);
    }
    
    @Override
    public String toString() {
        return String.format(
            "GcProgress[%.1f%% (%d/%d), phase=%s, elapsed=%s]",
            getPercentComplete(), completedWork, totalWork, 
            currentPhase, getElapsedTime()
        );
    }
    
    /**
     * Builder for creating GcProgress instances.
     */
    public static final class Builder {
        private Instant startTime;
        private Instant currentTime;
        private long completedWork;
        private long totalWork = 1; // Default to avoid division by zero
        private String currentPhase;
        private String additionalInfo;
        
        private Builder() {}
        
        /**
         * Sets the start time of the GC operation.
         * 
         * @param startTime the start time (required)
         * @return this builder
         */
        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }
        
        /**
         * Sets the current time when this progress is created.
         * 
         * @param currentTime the current time (required)
         * @return this builder
         */
        public Builder currentTime(Instant currentTime) {
            this.currentTime = currentTime;
            return this;
        }
        
        /**
         * Sets the amount of work completed.
         * 
         * @param completedWork the completed work units
         * @return this builder
         */
        public Builder completedWork(long completedWork) {
            this.completedWork = completedWork;
            return this;
        }
        
        /**
         * Sets the total amount of work to be completed.
         * 
         * @param totalWork the total work units
         * @return this builder
         */
        public Builder totalWork(long totalWork) {
            this.totalWork = totalWork;
            return this;
        }
        
        /**
         * Sets the current phase or operation being performed.
         * 
         * @param currentPhase the phase description
         * @return this builder
         */
        public Builder currentPhase(String currentPhase) {
            this.currentPhase = currentPhase;
            return this;
        }
        
        /**
         * Sets additional information about the current state.
         * 
         * @param additionalInfo additional information
         * @return this builder
         */
        public Builder additionalInfo(String additionalInfo) {
            this.additionalInfo = additionalInfo;
            return this;
        }
        
        /**
         * Builds the GcProgress instance.
         * 
         * @return a new GcProgress instance
         * @throws IllegalArgumentException if required fields are missing or invalid
         */
        public GcProgress build() {
            return new GcProgress(this);
        }
    }
}