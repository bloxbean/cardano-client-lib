package com.bloxbean.cardano.statetrees.rocksdb.mpt.batch;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Comprehensive metrics tracking for RocksDB batch operations.
 *
 * <p>This class provides detailed metrics collection for batch operations,
 * enabling performance monitoring, debugging, and optimization. Metrics are
 * collected with minimal overhead using atomic counters and efficient
 * timing mechanisms.</p>
 *
 * <p><b>Collected Metrics:</b></p>
 * <ul>
 *   <li>Operation counts (total, successful, failed)</li>
 *   <li>Timing statistics (min, max, average execution time)</li>
 *   <li>Batch size distributions (operation count, data size)</li>
 *   <li>Resource usage patterns</li>
 *   <li>Error rates and patterns</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe and can be used
 * concurrently from multiple threads without external synchronization.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * BatchMetrics metrics = new BatchMetrics();
 *
 * // Track a batch operation
 * try (var timer = metrics.startBatchTimer()) {
 *     // Execute batch operations
 *     batchExecutor.executeBatch(batch -> {
 *         batch.put(cf, key, value);
 *         return null;
 *     });
 *     timer.recordSuccess(1, 1024); // 1 operation, 1024 bytes
 * } catch (Exception e) {
 *     timer.recordFailure(e);
 * }
 *
 * // Get metrics summary
 * BatchMetricsSummary summary = metrics.getSummary();
 * System.out.println("Average batch time: " + summary.getAverageExecutionTimeMs() + "ms");
 * }</pre>
 *
 * @since 0.8.0
 */
public final class BatchMetrics {

    /**
     * Total number of batch operations attempted.
     */
    private final LongAdder totalBatches = new LongAdder();

    /**
     * Number of successful batch operations.
     */
    private final LongAdder successfulBatches = new LongAdder();

    /**
     * Number of failed batch operations.
     */
    private final LongAdder failedBatches = new LongAdder();

    /**
     * Total number of individual operations across all batches.
     */
    private final LongAdder totalOperations = new LongAdder();

    /**
     * Total data size processed across all batches (in bytes).
     */
    private final LongAdder totalDataSize = new LongAdder();

    /**
     * Total execution time across all batches (in nanoseconds).
     */
    private final LongAdder totalExecutionTimeNanos = new LongAdder();

    /**
     * Minimum execution time observed (in nanoseconds).
     */
    private final AtomicLong minExecutionTimeNanos = new AtomicLong(Long.MAX_VALUE);

    /**
     * Maximum execution time observed (in nanoseconds).
     */
    private final AtomicLong maxExecutionTimeNanos = new AtomicLong(Long.MIN_VALUE);

    /**
     * Timestamp when metrics collection started.
     */
    private final Instant startTime = Instant.now();

    /**
     * Creates a new batch timer for tracking operation metrics.
     *
     * @return a new BatchTimer instance
     */
    public BatchTimer startBatchTimer() {
        return new BatchTimer();
    }

    /**
     * Records metrics for a successful batch operation.
     *
     * @param operationCount     number of operations in the batch
     * @param dataSizeBytes      total data size in bytes
     * @param executionTimeNanos execution time in nanoseconds
     */
    void recordSuccess(int operationCount, long dataSizeBytes, long executionTimeNanos) {
        totalBatches.increment();
        successfulBatches.increment();
        totalOperations.add(operationCount);
        totalDataSize.add(dataSizeBytes);
        totalExecutionTimeNanos.add(executionTimeNanos);

        updateMinMaxExecutionTime(executionTimeNanos);
    }

    /**
     * Records metrics for a failed batch operation.
     *
     * @param executionTimeNanos execution time in nanoseconds
     * @param error              the error that occurred
     */
    void recordFailure(long executionTimeNanos, Throwable error) {
        totalBatches.increment();
        failedBatches.increment();
        totalExecutionTimeNanos.add(executionTimeNanos);

        updateMinMaxExecutionTime(executionTimeNanos);
    }

    /**
     * Updates the minimum and maximum execution time tracking.
     *
     * @param executionTimeNanos the execution time to consider
     */
    private void updateMinMaxExecutionTime(long executionTimeNanos) {
        minExecutionTimeNanos.accumulateAndGet(executionTimeNanos, Long::min);
        maxExecutionTimeNanos.accumulateAndGet(executionTimeNanos, Long::max);
    }

    /**
     * Creates a comprehensive metrics summary.
     *
     * @return a snapshot of current metrics
     */
    public BatchMetricsSummary getSummary() {
        long totalBatchCount = totalBatches.sum();
        long successfulBatchCount = successfulBatches.sum();
        long failedBatchCount = failedBatches.sum();
        long totalOpCount = totalOperations.sum();
        long totalDataBytes = totalDataSize.sum();
        long totalTimeNanos = totalExecutionTimeNanos.sum();

        double averageExecutionTimeMs = totalBatchCount > 0
                ? (totalTimeNanos / 1_000_000.0) / totalBatchCount
                : 0.0;

        double averageOperationsPerBatch = totalBatchCount > 0
                ? (double) totalOpCount / totalBatchCount
                : 0.0;

        double averageDataSizePerBatch = totalBatchCount > 0
                ? (double) totalDataBytes / totalBatchCount
                : 0.0;

        double successRate = totalBatchCount > 0
                ? (double) successfulBatchCount / totalBatchCount
                : 0.0;

        double throughputOpsPerSecond = calculateThroughput(totalOpCount);
        double throughputMBPerSecond = calculateThroughput(totalDataBytes) / (1024 * 1024);

        return new BatchMetricsSummary(
                totalBatchCount,
                successfulBatchCount,
                failedBatchCount,
                totalOpCount,
                totalDataBytes,
                averageExecutionTimeMs,
                minExecutionTimeNanos.get() == Long.MAX_VALUE ? 0.0 : minExecutionTimeNanos.get() / 1_000_000.0,
                maxExecutionTimeNanos.get() == Long.MIN_VALUE ? 0.0 : maxExecutionTimeNanos.get() / 1_000_000.0,
                averageOperationsPerBatch,
                averageDataSizePerBatch,
                successRate,
                throughputOpsPerSecond,
                throughputMBPerSecond,
                Duration.between(startTime, Instant.now())
        );
    }

    /**
     * Calculates throughput based on total count and elapsed time.
     *
     * @param totalCount the total count to calculate throughput for
     * @return throughput per second
     */
    private double calculateThroughput(long totalCount) {
        Duration elapsed = Duration.between(startTime, Instant.now());
        double elapsedSeconds = elapsed.toNanos() / 1_000_000_000.0;
        return elapsedSeconds > 0 ? totalCount / elapsedSeconds : 0.0;
    }

    /**
     * Resets all metrics to their initial state.
     *
     * <p>This method is primarily intended for testing and should be used
     * with caution in production environments as it will lose all historical data.</p>
     */
    public void reset() {
        totalBatches.reset();
        successfulBatches.reset();
        failedBatches.reset();
        totalOperations.reset();
        totalDataSize.reset();
        totalExecutionTimeNanos.reset();
        minExecutionTimeNanos.set(Long.MAX_VALUE);
        maxExecutionTimeNanos.set(Long.MIN_VALUE);
    }

    /**
     * Timer for tracking individual batch operation metrics.
     *
     * <p>This class implements AutoCloseable to support try-with-resources
     * patterns, ensuring that timing measurements are always completed even
     * if exceptions occur during batch operations.</p>
     */
    public final class BatchTimer implements AutoCloseable {
        private final long startTimeNanos;
        private boolean completed = false;

        /**
         * Creates a new batch timer that starts timing immediately.
         */
        private BatchTimer() {
            this.startTimeNanos = System.nanoTime();
        }

        /**
         * Records a successful batch operation with the specified metrics.
         *
         * @param operationCount number of operations in the batch
         * @param dataSizeBytes  total data size in bytes
         */
        public void recordSuccess(int operationCount, long dataSizeBytes) {
            if (completed) {
                throw new IllegalStateException("Timer already completed");
            }

            long executionTimeNanos = System.nanoTime() - startTimeNanos;
            BatchMetrics.this.recordSuccess(operationCount, dataSizeBytes, executionTimeNanos);
            completed = true;
        }

        /**
         * Records a failed batch operation.
         *
         * @param error the error that occurred during the operation
         */
        public void recordFailure(Throwable error) {
            if (completed) {
                throw new IllegalStateException("Timer already completed");
            }

            long executionTimeNanos = System.nanoTime() - startTimeNanos;
            BatchMetrics.this.recordFailure(executionTimeNanos, error);
            completed = true;
        }

        /**
         * Automatically records the operation as failed if not already completed.
         *
         * <p>This ensures that timing is always recorded even if the user
         * forgets to explicitly call recordSuccess() or recordFailure().</p>
         */
        @Override
        public void close() {
            if (!completed) {
                recordFailure(new IllegalStateException("Batch timer closed without explicit completion"));
            }
        }
    }
}
