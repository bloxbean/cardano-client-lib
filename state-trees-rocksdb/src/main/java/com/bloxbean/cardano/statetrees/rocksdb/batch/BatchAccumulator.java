package com.bloxbean.cardano.statetrees.rocksdb.batch;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Accumulator for efficient batch processing with automatic size and time-based flushing.
 * 
 * <p>This class enables high-throughput batch processing by accumulating operations
 * and executing them when either a size threshold is reached or a timeout expires.
 * This approach reduces the overhead of frequent small batches while ensuring
 * operations don't wait indefinitely.</p>
 * 
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>Automatic batching based on operation count threshold</li>
 *   <li>Time-based flushing to prevent operations from waiting too long</li>
 *   <li>Thread-safe operation accumulation</li>
 *   <li>Asynchronous batch execution with CompletableFuture results</li>
 *   <li>Proper resource cleanup and lifecycle management</li>
 * </ul>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * BatchAccumulator accumulator = executor.createBatchAccumulator(100, Duration.ofMillis(50));
 * 
 * // Submit operations - they'll be batched automatically
 * CompletableFuture<Void> result1 = accumulator.submit(batch -> {
 *     batch.put(cfNodes, key1, data1);
 *     return null;
 * });
 * 
 * CompletableFuture<Integer> result2 = accumulator.submit(batch -> {
 *     batch.put(cfNodes, key2, data2);
 *     return 1;
 * });
 * 
 * // Operations are automatically batched and executed when threshold is reached
 * // or timeout expires
 * 
 * // Clean up when done
 * accumulator.close();
 * }</pre>
 * 
 * <p><b>Thread Safety:</b> This class is thread-safe and can accept operations
 * from multiple threads concurrently. Batch execution is serialized to maintain
 * consistency.</p>
 * 
 * @author Bloxbean Project
 * @since 0.6.0
 */
public final class BatchAccumulator implements AutoCloseable {
    
    private final RocksDbBatchExecutor batchExecutor;
    private final int targetOperationCount;
    private final Duration timeoutDuration;
    private final ScheduledExecutorService scheduler;
    
    private final Object lock = new Object();
    private final List<PendingOperation<?>> pendingOperations = new ArrayList<>();
    private final AtomicInteger currentSize = new AtomicInteger(0);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    private ScheduledFuture<?> timeoutTask;
    
    /**
     * Creates a new BatchAccumulator.
     * 
     * @param batchExecutor the executor to use for batch operations
     * @param targetOperationCount threshold for automatic batch execution
     * @param timeoutDuration maximum time to wait before executing partial batch
     */
    BatchAccumulator(RocksDbBatchExecutor batchExecutor, int targetOperationCount, Duration timeoutDuration) {
        this.batchExecutor = batchExecutor;
        this.targetOperationCount = Math.max(1, targetOperationCount);
        this.timeoutDuration = timeoutDuration;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BatchAccumulator-Timer");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * Submits a batch operation to be accumulated and executed.
     * 
     * <p>Operations are collected until either the target operation count
     * is reached or the timeout expires, at which point they are executed
     * as a batch.</p>
     * 
     * @param <T> the return type of the operation
     * @param operation the batch operation to execute
     * @return a CompletableFuture that will complete with the operation result
     * @throws IllegalStateException if the accumulator is closed
     */
    public <T> CompletableFuture<T> submit(BatchOperation<T> operation) {
        if (closed.get()) {
            throw new IllegalStateException("BatchAccumulator is closed");
        }
        
        PendingOperation<T> pendingOp = new PendingOperation<>(operation);
        
        synchronized (lock) {
            pendingOperations.add(pendingOp);
            int size = currentSize.incrementAndGet();
            
            // Schedule timeout if this is the first operation
            if (size == 1) {
                scheduleTimeout();
            }
            
            // Execute batch if threshold is reached
            if (size >= targetOperationCount) {
                executePendingBatch();
            }
        }
        
        return pendingOp.future;
    }
    
    /**
     * Forces execution of any pending operations immediately.
     * 
     * @return a CompletableFuture that completes when all pending operations are done
     */
    public CompletableFuture<Void> flush() {
        synchronized (lock) {
            if (currentSize.get() > 0) {
                return executePendingBatch().thenApply(results -> null);
            } else {
                return CompletableFuture.completedFuture(null);
            }
        }
    }
    
    /**
     * Executes the currently pending operations as a batch.
     * Must be called while holding the lock.
     * 
     * @return a CompletableFuture that completes when the batch is executed
     */
    private CompletableFuture<Object[]> executePendingBatch() {
        if (pendingOperations.isEmpty()) {
            return CompletableFuture.completedFuture(new Object[0]);
        }
        
        // Capture current operations and reset state
        List<PendingOperation<?>> opsToExecute = new ArrayList<>(pendingOperations);
        pendingOperations.clear();
        currentSize.set(0);
        
        // Cancel timeout since we're executing now
        if (timeoutTask != null) {
            timeoutTask.cancel(false);
            timeoutTask = null;
        }
        
        // Create combined batch operation
        BatchOperation<Object[]> combinedOperation = batch -> {
            Object[] results = new Object[opsToExecute.size()];
            for (int i = 0; i < opsToExecute.size(); i++) {
                results[i] = opsToExecute.get(i).operation.execute(batch);
            }
            return results;
        };
        
        // Execute asynchronously and distribute results
        return batchExecutor.executeBatchAsync(combinedOperation)
            .whenComplete((results, throwable) -> {
                for (int i = 0; i < opsToExecute.size(); i++) {
                    PendingOperation<?> pendingOp = opsToExecute.get(i);
                    if (throwable != null) {
                        pendingOp.future.completeExceptionally(throwable);
                    } else {
                        @SuppressWarnings("unchecked")
                        var typedResult = (Object) results[i];
                        pendingOp.completeWithResult(typedResult);
                    }
                }
            });
    }
    
    /**
     * Schedules a timeout task to execute pending operations.
     */
    private void scheduleTimeout() {
        if (timeoutTask != null) {
            timeoutTask.cancel(false);
        }
        
        timeoutTask = scheduler.schedule(() -> {
            synchronized (lock) {
                if (currentSize.get() > 0) {
                    executePendingBatch();
                }
            }
        }, timeoutDuration.toMillis(), TimeUnit.MILLISECONDS);
    }
    
    /**
     * Returns the current number of pending operations.
     * 
     * @return pending operation count
     */
    public int getPendingCount() {
        return currentSize.get();
    }
    
    /**
     * Returns the target operation count for automatic batch execution.
     * 
     * @return target operation count
     */
    public int getTargetOperationCount() {
        return targetOperationCount;
    }
    
    /**
     * Returns the timeout duration for partial batch execution.
     * 
     * @return timeout duration
     */
    public Duration getTimeoutDuration() {
        return timeoutDuration;
    }
    
    /**
     * Checks if this accumulator is closed.
     * 
     * @return true if closed, false otherwise
     */
    public boolean isClosed() {
        return closed.get();
    }
    
    /**
     * Closes this accumulator and flushes any pending operations.
     * 
     * <p>This method will execute any remaining pending operations before
     * shutting down. After calling close(), no new operations can be submitted.</p>
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                // Flush any remaining operations
                flush().get(timeoutDuration.toMillis() * 2, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                // Log warning but continue with cleanup
                System.err.println("Warning: Failed to flush pending operations during close: " + e.getMessage());
            }
            
            // Cancel timeout task
            if (timeoutTask != null) {
                timeoutTask.cancel(true);
            }
            
            // Shutdown scheduler
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Container for a pending batch operation and its future result.
     */
    private static class PendingOperation<T> {
        final BatchOperation<T> operation;
        final CompletableFuture<T> future;
        
        PendingOperation(BatchOperation<T> operation) {
            this.operation = operation;
            this.future = new CompletableFuture<>();
        }
        
        @SuppressWarnings("unchecked")
        void completeWithResult(Object result) {
            future.complete((T) result);
        }
    }
}