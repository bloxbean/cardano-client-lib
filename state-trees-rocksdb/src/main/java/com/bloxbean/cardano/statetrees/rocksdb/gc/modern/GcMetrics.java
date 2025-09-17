package com.bloxbean.cardano.statetrees.rocksdb.gc.modern;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Comprehensive metrics for garbage collection operations with real-time tracking.
 * 
 * <p>This class provides detailed metrics collection for GC operations, offering
 * both real-time updates during execution and final summary statistics. It's
 * designed to be thread-safe and efficient for high-frequency updates during
 * GC execution.</p>
 * 
 * <p><b>Metric Categories:</b></p>
 * <ul>
 *   <li><b>Timing:</b> Execution duration, phase timings, rate calculations</li>
 *   <li><b>Work Units:</b> Nodes processed, bytes reclaimed, operations performed</li>
 *   <li><b>Storage Impact:</b> Space freed, storage efficiency improvements</li>
 *   <li><b>Performance:</b> Throughput rates, batch sizes, memory usage</li>
 *   <li><b>Quality:</b> Error counts, consistency checks, validation results</li>
 * </ul>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // Create metrics collector
 * GcMetrics metrics = new GcMetrics("RefcountGC", "Incremental reference counting");
 * 
 * // Track execution
 * metrics.recordPhaseStart("Mark Phase");
 * for (Node node : nodesToProcess) {
 *     metrics.incrementNodesProcessed();
 *     // ... process node ...
 *     metrics.addBytesReclaimed(node.getSize());
 * }
 * metrics.recordPhaseEnd("Mark Phase");
 * 
 * // Get final summary
 * GcMetricsSummary summary = metrics.createSummary();
 * System.out.println("GC completed: " + summary.formatSummary());
 * }</pre>
 * 
 * @author Bloxbean Project
 * @since 0.6.0
 */
public final class GcMetrics {
    
    private final String strategyName;
    private final String description;
    private final String executionId;
    private final Instant startTime;
    
    // Atomic counters for thread-safe updates
    private final LongAdder nodesProcessed = new LongAdder();
    private final LongAdder nodesDeleted = new LongAdder();
    private final LongAdder bytesReclaimed = new LongAdder();
    private final LongAdder batchOperations = new LongAdder();
    private final LongAdder errorCount = new LongAdder();
    
    // Timing tracking
    private final AtomicLong totalExecutionTimeNanos = new AtomicLong();
    private final Map<String, PhaseMetrics> phaseMetrics = Collections.synchronizedMap(new LinkedHashMap<>());
    private volatile String currentPhase;
    private volatile Instant currentPhaseStart;
    
    // Storage impact tracking
    private final LongAdder initialStorageSize = new LongAdder();
    private final LongAdder finalStorageSize = new LongAdder();
    
    // Quality metrics
    private final Set<String> warnings = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Set<String> errors = Collections.synchronizedSet(new LinkedHashSet<>());
    
    // Configuration
    private volatile boolean completed = false;
    private volatile Instant endTime;
    
    /**
     * Creates a new GcMetrics instance.
     * 
     * @param strategyName name of the GC strategy
     * @param description description of the GC operation
     * @param executionId unique identifier for this execution
     */
    public GcMetrics(String strategyName, String description, String executionId) {
        this.strategyName = Objects.requireNonNull(strategyName, "Strategy name cannot be null");
        this.description = Objects.requireNonNull(description, "Description cannot be null");
        this.executionId = Objects.requireNonNull(executionId, "Execution ID cannot be null");
        this.startTime = Instant.now();
    }
    
    /**
     * Creates a new GcMetrics instance with auto-generated execution ID.
     * 
     * @param strategyName name of the GC strategy
     * @param description description of the GC operation
     */
    public GcMetrics(String strategyName, String description) {
        this(strategyName, description, UUID.randomUUID().toString());
    }
    
    /**
     * Records the start of a new phase.
     * 
     * @param phaseName name of the phase being started
     */
    public void recordPhaseStart(String phaseName) {
        // End previous phase if any
        if (currentPhase != null && currentPhaseStart != null) {
            recordPhaseEnd(currentPhase);
        }
        
        currentPhase = phaseName;
        currentPhaseStart = Instant.now();
    }
    
    /**
     * Records the end of the specified phase.
     * 
     * @param phaseName name of the phase being ended
     */
    public void recordPhaseEnd(String phaseName) {
        if (currentPhaseStart == null) {
            addWarning("Phase end recorded without start: " + phaseName);
            return;
        }
        
        Duration phaseDuration = Duration.between(currentPhaseStart, Instant.now());
        phaseMetrics.put(phaseName, new PhaseMetrics(phaseName, currentPhaseStart, phaseDuration));
        
        if (Objects.equals(currentPhase, phaseName)) {
            currentPhase = null;
            currentPhaseStart = null;
        }
    }
    
    /**
     * Increments the count of nodes processed.
     */
    public void incrementNodesProcessed() {
        nodesProcessed.increment();
    }
    
    /**
     * Adds to the count of nodes processed.
     * 
     * @param count number of nodes to add
     */
    public void addNodesProcessed(long count) {
        nodesProcessed.add(count);
    }
    
    /**
     * Increments the count of nodes deleted.
     */
    public void incrementNodesDeleted() {
        nodesDeleted.increment();
    }
    
    /**
     * Adds to the count of nodes deleted.
     * 
     * @param count number of deleted nodes to add
     */
    public void addNodesDeleted(long count) {
        nodesDeleted.add(count);
    }
    
    /**
     * Adds to the total bytes reclaimed.
     * 
     * @param bytes number of bytes reclaimed
     */
    public void addBytesReclaimed(long bytes) {
        bytesReclaimed.add(Math.max(0, bytes));
    }
    
    /**
     * Increments the count of batch operations performed.
     */
    public void incrementBatchOperations() {
        batchOperations.increment();
    }
    
    /**
     * Records the initial storage size before GC.
     * 
     * @param sizeBytes initial storage size in bytes
     */
    public void setInitialStorageSize(long sizeBytes) {
        initialStorageSize.add(sizeBytes);
    }
    
    /**
     * Records the final storage size after GC.
     * 
     * @param sizeBytes final storage size in bytes
     */
    public void setFinalStorageSize(long sizeBytes) {
        finalStorageSize.add(sizeBytes);
    }
    
    /**
     * Adds a warning message to the metrics.
     * 
     * @param warning the warning message
     */
    public void addWarning(String warning) {
        warnings.add(warning);
    }
    
    /**
     * Adds an error message to the metrics and increments error count.
     * 
     * @param error the error message
     */
    public void addError(String error) {
        errors.add(error);
        errorCount.increment();
    }
    
    /**
     * Marks the GC operation as completed.
     */
    public void markCompleted() {
        if (!completed) {
            // End current phase if any
            if (currentPhase != null) {
                recordPhaseEnd(currentPhase);
            }
            
            endTime = Instant.now();
            totalExecutionTimeNanos.set(Duration.between(startTime, endTime).toNanos());
            completed = true;
        }
    }
    
    /**
     * Gets the current count of nodes processed.
     * 
     * @return nodes processed count
     */
    public long getNodesProcessed() {
        return nodesProcessed.sum();
    }
    
    /**
     * Gets the current count of nodes deleted.
     * 
     * @return nodes deleted count
     */
    public long getNodesDeleted() {
        return nodesDeleted.sum();
    }
    
    /**
     * Gets the total bytes reclaimed.
     * 
     * @return bytes reclaimed
     */
    public long getBytesReclaimed() {
        return bytesReclaimed.sum();
    }
    
    /**
     * Gets the count of batch operations performed.
     * 
     * @return batch operations count
     */
    public long getBatchOperations() {
        return batchOperations.sum();
    }
    
    /**
     * Gets the error count.
     * 
     * @return error count
     */
    public long getErrorCount() {
        return errorCount.sum();
    }
    
    /**
     * Gets the execution duration so far or total if completed.
     * 
     * @return execution duration
     */
    public Duration getExecutionDuration() {
        if (completed && endTime != null) {
            return Duration.between(startTime, endTime);
        } else {
            return Duration.between(startTime, Instant.now());
        }
    }
    
    /**
     * Gets the current processing rate in nodes per second.
     * 
     * @return processing rate
     */
    public double getProcessingRateNodesPerSecond() {
        Duration elapsed = getExecutionDuration();
        if (elapsed.isZero()) {
            return 0.0;
        }
        return (double) getNodesProcessed() / elapsed.getSeconds();
    }
    
    /**
     * Gets the storage efficiency improvement as a percentage.
     * 
     * @return efficiency improvement percentage, or 0 if not available
     */
    public double getStorageEfficiencyImprovement() {
        long initial = initialStorageSize.sum();
        long final_ = finalStorageSize.sum();
        
        if (initial <= 0) {
            return 0.0;
        }
        
        return ((double) (initial - final_) / initial) * 100.0;
    }
    
    /**
     * Checks if the GC operation completed successfully.
     * 
     * @return true if completed without errors
     */
    public boolean isSuccessful() {
        return completed && getErrorCount() == 0;
    }
    
    /**
     * Creates a comprehensive summary of the metrics.
     * 
     * @return a detailed metrics summary
     */
    public GcMetricsSummary createSummary() {
        return new GcMetricsSummary(
            executionId,
            strategyName,
            description,
            startTime,
            endTime,
            getExecutionDuration(),
            getNodesProcessed(),
            getNodesDeleted(),
            getBytesReclaimed(),
            getBatchOperations(),
            getErrorCount(),
            initialStorageSize.sum(),
            finalStorageSize.sum(),
            getStorageEfficiencyImprovement(),
            getProcessingRateNodesPerSecond(),
            new ArrayList<>(phaseMetrics.values()),
            new ArrayList<>(warnings),
            new ArrayList<>(errors),
            completed
        );
    }
    
    /**
     * Gets the strategy name.
     * 
     * @return strategy name
     */
    public String getStrategyName() {
        return strategyName;
    }
    
    /**
     * Gets the execution ID.
     * 
     * @return execution ID
     */
    public String getExecutionId() {
        return executionId;
    }
    
    /**
     * Gets the start time.
     * 
     * @return start time
     */
    public Instant getStartTime() {
        return startTime;
    }
    
    /**
     * Checks if the operation has completed.
     * 
     * @return true if completed
     */
    public boolean isCompleted() {
        return completed;
    }
    
    @Override
    public String toString() {
        return String.format(
            "GcMetrics[strategy=%s, id=%s, processed=%d, deleted=%d, duration=%s, completed=%s]",
            strategyName, executionId.substring(0, 8), 
            getNodesProcessed(), getNodesDeleted(), getExecutionDuration(), completed
        );
    }
    
    /**
     * Metrics for individual GC phases.
     */
    public static final class PhaseMetrics {
        private final String phaseName;
        private final Instant startTime;
        private final Duration duration;
        
        PhaseMetrics(String phaseName, Instant startTime, Duration duration) {
            this.phaseName = phaseName;
            this.startTime = startTime;
            this.duration = duration;
        }
        
        public String getPhaseName() {
            return phaseName;
        }
        
        public Instant getStartTime() {
            return startTime;
        }
        
        public Duration getDuration() {
            return duration;
        }
        
        @Override
        public String toString() {
            return String.format("PhaseMetrics[%s: %s]", phaseName, duration);
        }
    }
}