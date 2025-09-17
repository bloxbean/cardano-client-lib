package com.bloxbean.cardano.statetrees.rocksdb.gc.modern;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Immutable summary of garbage collection metrics.
 * 
 * <p>This class provides a comprehensive, read-only view of GC execution
 * results including timing, work performed, storage impact, and quality
 * metrics. It's designed for reporting, monitoring, and historical analysis
 * of GC performance.</p>
 * 
 * <p><b>Summary Categories:</b></p>
 * <ul>
 *   <li><b>Execution:</b> Strategy, timing, completion status</li>
 *   <li><b>Work Performed:</b> Nodes processed/deleted, bytes reclaimed</li>
 *   <li><b>Performance:</b> Processing rates, throughput metrics</li>
 *   <li><b>Storage Impact:</b> Space savings, efficiency improvements</li>
 *   <li><b>Quality:</b> Warnings, errors, phase breakdowns</li>
 * </ul>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * GcMetricsSummary summary = metrics.createSummary();
 * 
 * // Performance monitoring
 * if (summary.getProcessingRateNodesPerSecond() < 1000) {
 *     logger.warn("GC performance below threshold: {} nodes/sec", 
 *                 summary.getProcessingRateNodesPerSecond());
 * }
 * 
 * // Storage efficiency
 * if (summary.getStorageEfficiencyImprovement() > 0) {
 *     logger.info("GC freed {:.1}% of storage space", 
 *                 summary.getStorageEfficiencyImprovement());
 * }
 * 
 * // Generate report
 * System.out.println(summary.formatSummary());
 * }</pre>
 * 
 * @author Bloxbean Project
 * @since 0.6.0
 */
public final class GcMetricsSummary {
    
    private final String executionId;
    private final String strategyName;
    private final String description;
    private final Instant startTime;
    private final Instant endTime;
    private final Duration executionDuration;
    private final long nodesProcessed;
    private final long nodesDeleted;
    private final long bytesReclaimed;
    private final long batchOperations;
    private final long errorCount;
    private final long initialStorageSize;
    private final long finalStorageSize;
    private final double storageEfficiencyImprovement;
    private final double processingRateNodesPerSecond;
    private final List<GcMetrics.PhaseMetrics> phaseMetrics;
    private final List<String> warnings;
    private final List<String> errors;
    private final boolean completed;
    
    /**
     * Creates a new GcMetricsSummary.
     * 
     * @param executionId unique identifier for this GC execution
     * @param strategyName name of the GC strategy used
     * @param description description of the GC operation
     * @param startTime when the GC execution started
     * @param endTime when the GC execution ended (may be null)
     * @param executionDuration total execution time
     * @param nodesProcessed total number of nodes processed
     * @param nodesDeleted number of nodes deleted
     * @param bytesReclaimed total bytes reclaimed
     * @param batchOperations number of batch operations performed
     * @param errorCount number of errors encountered
     * @param initialStorageSize storage size before GC
     * @param finalStorageSize storage size after GC
     * @param storageEfficiencyImprovement efficiency improvement percentage
     * @param processingRateNodesPerSecond processing rate in nodes per second
     * @param phaseMetrics metrics for individual phases
     * @param warnings list of warning messages
     * @param errors list of error messages
     * @param completed whether the operation completed
     */
    public GcMetricsSummary(
            String executionId,
            String strategyName,
            String description,
            Instant startTime,
            Instant endTime,
            Duration executionDuration,
            long nodesProcessed,
            long nodesDeleted,
            long bytesReclaimed,
            long batchOperations,
            long errorCount,
            long initialStorageSize,
            long finalStorageSize,
            double storageEfficiencyImprovement,
            double processingRateNodesPerSecond,
            List<GcMetrics.PhaseMetrics> phaseMetrics,
            List<String> warnings,
            List<String> errors,
            boolean completed) {
        
        this.executionId = Objects.requireNonNull(executionId);
        this.strategyName = Objects.requireNonNull(strategyName);
        this.description = Objects.requireNonNull(description);
        this.startTime = Objects.requireNonNull(startTime);
        this.endTime = endTime; // Can be null if not completed
        this.executionDuration = Objects.requireNonNull(executionDuration);
        this.nodesProcessed = nodesProcessed;
        this.nodesDeleted = nodesDeleted;
        this.bytesReclaimed = bytesReclaimed;
        this.batchOperations = batchOperations;
        this.errorCount = errorCount;
        this.initialStorageSize = initialStorageSize;
        this.finalStorageSize = finalStorageSize;
        this.storageEfficiencyImprovement = storageEfficiencyImprovement;
        this.processingRateNodesPerSecond = processingRateNodesPerSecond;
        this.phaseMetrics = List.copyOf(phaseMetrics);
        this.warnings = List.copyOf(warnings);
        this.errors = List.copyOf(errors);
        this.completed = completed;
    }
    
    public String getExecutionId() {
        return executionId;
    }
    
    public String getStrategyName() {
        return strategyName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public Instant getStartTime() {
        return startTime;
    }
    
    public Instant getEndTime() {
        return endTime;
    }
    
    public Duration getExecutionDuration() {
        return executionDuration;
    }
    
    public long getNodesProcessed() {
        return nodesProcessed;
    }
    
    public long getNodesDeleted() {
        return nodesDeleted;
    }
    
    public long getBytesReclaimed() {
        return bytesReclaimed;
    }
    
    public long getBatchOperations() {
        return batchOperations;
    }
    
    public long getErrorCount() {
        return errorCount;
    }
    
    public long getInitialStorageSize() {
        return initialStorageSize;
    }
    
    public long getFinalStorageSize() {
        return finalStorageSize;
    }
    
    public double getStorageEfficiencyImprovement() {
        return storageEfficiencyImprovement;
    }
    
    public double getProcessingRateNodesPerSecond() {
        return processingRateNodesPerSecond;
    }
    
    public List<GcMetrics.PhaseMetrics> getPhaseMetrics() {
        return phaseMetrics;
    }
    
    public List<String> getWarnings() {
        return warnings;
    }
    
    public List<String> getErrors() {
        return errors;
    }
    
    public boolean isCompleted() {
        return completed;
    }
    
    /**
     * Checks if the GC operation was successful (completed without errors).
     * 
     * @return true if successful
     */
    public boolean isSuccessful() {
        return completed && errorCount == 0;
    }
    
    /**
     * Gets the deletion efficiency (percentage of processed nodes that were deleted).
     * 
     * @return deletion efficiency percentage
     */
    public double getDeletionEfficiency() {
        return nodesProcessed > 0 ? ((double) nodesDeleted / nodesProcessed) * 100.0 : 0.0;
    }
    
    /**
     * Gets the average bytes reclaimed per deleted node.
     * 
     * @return average bytes per deleted node
     */
    public double getAverageBytesPerDeletedNode() {
        return nodesDeleted > 0 ? (double) bytesReclaimed / nodesDeleted : 0.0;
    }
    
    /**
     * Formats the summary as a human-readable string.
     * 
     * @return formatted summary string
     */
    public String formatSummary() {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        sb.append("=== Garbage Collection Summary ===\n");
        sb.append(String.format("Strategy: %s\n", strategyName));
        sb.append(String.format("Description: %s\n", description));
        sb.append(String.format("Execution ID: %s\n", executionId));
        sb.append(String.format("Start Time: %s\n", startTime.atZone(java.time.ZoneId.systemDefault()).format(formatter)));
        
        if (endTime != null) {
            sb.append(String.format("End Time: %s\n", endTime.atZone(java.time.ZoneId.systemDefault()).format(formatter)));
        }
        
        sb.append(String.format("Duration: %s\n", formatDuration(executionDuration)));
        sb.append(String.format("Status: %s\n", isSuccessful() ? "SUCCESS" : (completed ? "COMPLETED WITH ERRORS" : "IN PROGRESS")));
        
        sb.append("\n=== Work Performed ===\n");
        sb.append(String.format("Nodes Processed: %,d\n", nodesProcessed));
        sb.append(String.format("Nodes Deleted: %,d (%.1f%%)\n", nodesDeleted, getDeletionEfficiency()));
        sb.append(String.format("Bytes Reclaimed: %,d (%s)\n", bytesReclaimed, formatBytes(bytesReclaimed)));
        sb.append(String.format("Batch Operations: %,d\n", batchOperations));
        
        sb.append("\n=== Performance ===\n");
        sb.append(String.format("Processing Rate: %.1f nodes/sec\n", processingRateNodesPerSecond));
        sb.append(String.format("Data Reclaim Rate: %s/sec\n", formatBytes((long) (bytesReclaimed / Math.max(1, executionDuration.getSeconds())))));
        
        if (getAverageBytesPerDeletedNode() > 0) {
            sb.append(String.format("Avg Bytes/Deleted Node: %.0f bytes\n", getAverageBytesPerDeletedNode()));
        }
        
        sb.append("\n=== Storage Impact ===\n");
        if (initialStorageSize > 0) {
            sb.append(String.format("Initial Storage: %s\n", formatBytes(initialStorageSize)));
            sb.append(String.format("Final Storage: %s\n", formatBytes(finalStorageSize)));
            sb.append(String.format("Space Savings: %.1f%%\n", storageEfficiencyImprovement));
        } else {
            sb.append("Storage size information not available\n");
        }
        
        if (!phaseMetrics.isEmpty()) {
            sb.append("\n=== Phase Breakdown ===\n");
            for (GcMetrics.PhaseMetrics phase : phaseMetrics) {
                double percentage = executionDuration.toNanos() > 0 ? 
                    (phase.getDuration().toNanos() * 100.0) / executionDuration.toNanos() : 0.0;
                sb.append(String.format("%s: %s (%.1f%%)\n", 
                    phase.getPhaseName(), formatDuration(phase.getDuration()), percentage));
            }
        }
        
        if (!warnings.isEmpty()) {
            sb.append("\n=== Warnings ===\n");
            warnings.forEach(warning -> sb.append("• ").append(warning).append("\n"));
        }
        
        if (!errors.isEmpty()) {
            sb.append("\n=== Errors ===\n");
            errors.forEach(error -> sb.append("• ").append(error).append("\n"));
        }
        
        return sb.toString();
    }
    
    /**
     * Formats a duration as a human-readable string.
     */
    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return String.format("%.1fs", seconds + duration.getNano() / 1_000_000_000.0);
        } else if (seconds < 3600) {
            return String.format("%dm %.1fs", seconds / 60, (seconds % 60) + duration.getNano() / 1_000_000_000.0);
        } else {
            long hours = seconds / 3600;
            long remainingMinutes = (seconds % 3600) / 60;
            return String.format("%dh %dm %.1fs", hours, remainingMinutes, 
                (seconds % 60) + duration.getNano() / 1_000_000_000.0);
        }
    }
    
    /**
     * Formats bytes as a human-readable string.
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    @Override
    public String toString() {
        return String.format(
            "GcMetricsSummary[%s: %d processed, %d deleted, %s duration, %s]",
            strategyName, nodesProcessed, nodesDeleted, 
            formatDuration(executionDuration), 
            isSuccessful() ? "SUCCESS" : "FAILED"
        );
    }
}