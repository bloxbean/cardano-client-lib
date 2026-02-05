package com.bloxbean.cardano.vds.mpf.rocksdb.batch;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable snapshot of batch operation metrics.
 *
 * <p>This class provides a comprehensive summary of batch operation performance
 * and statistics. It's designed to be a lightweight, immutable data transfer
 * object that can be safely shared across threads and used for monitoring,
 * logging, and performance analysis.</p>
 *
 * <p><b>Metric Categories:</b></p>
 * <ul>
 *   <li><b>Counts:</b> Total, successful, and failed batch operations</li>
 *   <li><b>Timing:</b> Average, minimum, and maximum execution times</li>
 *   <li><b>Throughput:</b> Operations per second and data transfer rates</li>
 *   <li><b>Efficiency:</b> Success rates and average batch sizes</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * BatchMetricsSummary summary = batchMetrics.getSummary();
 *
 * // Performance monitoring
 * if (summary.getAverageExecutionTimeMs() > 100) {
 *     logger.warn("Batch operations are running slowly: {}ms average",
 *                 summary.getAverageExecutionTimeMs());
 * }
 *
 * // Throughput analysis
 * System.out.printf("Throughput: %.1f ops/sec, %.2f MB/sec%n",
 *     summary.getThroughputOpsPerSecond(),
 *     summary.getThroughputMBPerSecond());
 *
 * // Success rate monitoring
 * if (summary.getSuccessRate() < 0.95) {
 *     logger.error("High failure rate: {:.1%}", summary.getSuccessRate());
 * }
 * }</pre>
 *
 * @since 0.8.0
 */
public final class BatchMetricsSummary {

    private final long totalBatches;
    private final long successfulBatches;
    private final long failedBatches;
    private final long totalOperations;
    private final long totalDataSizeBytes;
    private final double averageExecutionTimeMs;
    private final double minExecutionTimeMs;
    private final double maxExecutionTimeMs;
    private final double averageOperationsPerBatch;
    private final double averageDataSizePerBatch;
    private final double successRate;
    private final double throughputOpsPerSecond;
    private final double throughputMBPerSecond;
    private final Duration collectionDuration;

    /**
     * Creates a new batch metrics summary.
     *
     * @param totalBatches              total number of batch operations
     * @param successfulBatches         number of successful batch operations
     * @param failedBatches             number of failed batch operations
     * @param totalOperations           total individual operations across all batches
     * @param totalDataSizeBytes        total data processed in bytes
     * @param averageExecutionTimeMs    average execution time in milliseconds
     * @param minExecutionTimeMs        minimum execution time in milliseconds
     * @param maxExecutionTimeMs        maximum execution time in milliseconds
     * @param averageOperationsPerBatch average operations per batch
     * @param averageDataSizePerBatch   average data size per batch in bytes
     * @param successRate               success rate as a ratio (0.0 to 1.0)
     * @param throughputOpsPerSecond    operations per second throughput
     * @param throughputMBPerSecond     data throughput in MB per second
     * @param collectionDuration        duration over which metrics were collected
     */
    public BatchMetricsSummary(
            long totalBatches,
            long successfulBatches,
            long failedBatches,
            long totalOperations,
            long totalDataSizeBytes,
            double averageExecutionTimeMs,
            double minExecutionTimeMs,
            double maxExecutionTimeMs,
            double averageOperationsPerBatch,
            double averageDataSizePerBatch,
            double successRate,
            double throughputOpsPerSecond,
            double throughputMBPerSecond,
            Duration collectionDuration) {

        this.totalBatches = totalBatches;
        this.successfulBatches = successfulBatches;
        this.failedBatches = failedBatches;
        this.totalOperations = totalOperations;
        this.totalDataSizeBytes = totalDataSizeBytes;
        this.averageExecutionTimeMs = averageExecutionTimeMs;
        this.minExecutionTimeMs = minExecutionTimeMs;
        this.maxExecutionTimeMs = maxExecutionTimeMs;
        this.averageOperationsPerBatch = averageOperationsPerBatch;
        this.averageDataSizePerBatch = averageDataSizePerBatch;
        this.successRate = successRate;
        this.throughputOpsPerSecond = throughputOpsPerSecond;
        this.throughputMBPerSecond = throughputMBPerSecond;
        this.collectionDuration = Objects.requireNonNull(collectionDuration);
    }

    /**
     * Returns the total number of batch operations attempted.
     *
     * @return total batch count
     */
    public long getTotalBatches() {
        return totalBatches;
    }

    /**
     * Returns the number of successful batch operations.
     *
     * @return successful batch count
     */
    public long getSuccessfulBatches() {
        return successfulBatches;
    }

    /**
     * Returns the number of failed batch operations.
     *
     * @return failed batch count
     */
    public long getFailedBatches() {
        return failedBatches;
    }

    /**
     * Returns the total number of individual operations across all batches.
     *
     * @return total operation count
     */
    public long getTotalOperations() {
        return totalOperations;
    }

    /**
     * Returns the total data size processed across all batches.
     *
     * @return total data size in bytes
     */
    public long getTotalDataSizeBytes() {
        return totalDataSizeBytes;
    }

    /**
     * Returns the average execution time per batch in milliseconds.
     *
     * @return average execution time in milliseconds
     */
    public double getAverageExecutionTimeMs() {
        return averageExecutionTimeMs;
    }

    /**
     * Returns the minimum execution time observed in milliseconds.
     *
     * @return minimum execution time in milliseconds
     */
    public double getMinExecutionTimeMs() {
        return minExecutionTimeMs;
    }

    /**
     * Returns the maximum execution time observed in milliseconds.
     *
     * @return maximum execution time in milliseconds
     */
    public double getMaxExecutionTimeMs() {
        return maxExecutionTimeMs;
    }

    /**
     * Returns the average number of operations per batch.
     *
     * @return average operations per batch
     */
    public double getAverageOperationsPerBatch() {
        return averageOperationsPerBatch;
    }

    /**
     * Returns the average data size per batch in bytes.
     *
     * @return average data size per batch in bytes
     */
    public double getAverageDataSizePerBatch() {
        return averageDataSizePerBatch;
    }

    /**
     * Returns the success rate as a ratio between 0.0 and 1.0.
     *
     * @return success rate (0.0 = 0%, 1.0 = 100%)
     */
    public double getSuccessRate() {
        return successRate;
    }

    /**
     * Returns the success rate as a percentage between 0 and 100.
     *
     * @return success rate as percentage
     */
    public double getSuccessRatePercent() {
        return successRate * 100.0;
    }

    /**
     * Returns the throughput in operations per second.
     *
     * @return operations per second
     */
    public double getThroughputOpsPerSecond() {
        return throughputOpsPerSecond;
    }

    /**
     * Returns the throughput in megabytes per second.
     *
     * @return throughput in MB/s
     */
    public double getThroughputMBPerSecond() {
        return throughputMBPerSecond;
    }

    /**
     * Returns the duration over which these metrics were collected.
     *
     * @return collection duration
     */
    public Duration getCollectionDuration() {
        return collectionDuration;
    }

    /**
     * Checks if any batch operations have been recorded.
     *
     * @return true if metrics contain data, false if empty
     */
    public boolean hasData() {
        return totalBatches > 0;
    }

    /**
     * Formats the summary as a human-readable string.
     *
     * @return formatted summary string
     */
    public String formatSummary() {
        if (!hasData()) {
            return "No batch operations recorded";
        }

        return String.format(
                "Batch Operations Summary:%n" +
                        "  Total: %d (Success: %d, Failed: %d)%n" +
                        "  Success Rate: %.1f%%%n" +
                        "  Execution Time: Avg=%.1fms, Min=%.1fms, Max=%.1fms%n" +
                        "  Throughput: %.1f ops/sec, %.2f MB/sec%n" +
                        "  Batch Size: Avg=%.1f ops, %.0f bytes%n" +
                        "  Duration: %s",
                totalBatches, successfulBatches, failedBatches,
                getSuccessRatePercent(),
                averageExecutionTimeMs, minExecutionTimeMs, maxExecutionTimeMs,
                throughputOpsPerSecond, throughputMBPerSecond,
                averageOperationsPerBatch, averageDataSizePerBatch,
                formatDuration(collectionDuration)
        );
    }

    /**
     * Formats a duration as a human-readable string.
     *
     * @param duration the duration to format
     * @return formatted duration string
     */
    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        } else {
            long hours = seconds / 3600;
            long remainingMinutes = (seconds % 3600) / 60;
            return hours + "h " + remainingMinutes + "m";
        }
    }

    @Override
    public String toString() {
        return String.format(
                "BatchMetricsSummary[batches=%d, success=%.1f%%, avgTime=%.1fms, throughput=%.1f ops/sec]",
                totalBatches, getSuccessRatePercent(), averageExecutionTimeMs, throughputOpsPerSecond
        );
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        BatchMetricsSummary that = (BatchMetricsSummary) obj;
        return totalBatches == that.totalBatches &&
                successfulBatches == that.successfulBatches &&
                failedBatches == that.failedBatches &&
                totalOperations == that.totalOperations &&
                totalDataSizeBytes == that.totalDataSizeBytes &&
                Double.compare(that.averageExecutionTimeMs, averageExecutionTimeMs) == 0 &&
                Double.compare(that.successRate, successRate) == 0 &&
                Objects.equals(collectionDuration, that.collectionDuration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(totalBatches, successfulBatches, failedBatches,
                totalOperations, totalDataSizeBytes, averageExecutionTimeMs,
                successRate, collectionDuration);
    }
}
