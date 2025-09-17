package com.bloxbean.cardano.statetrees.rocksdb.batch;

import com.bloxbean.cardano.statetrees.rocksdb.exceptions.RocksDbBatchException;
import com.bloxbean.cardano.statetrees.rocksdb.exceptions.RocksDbStorageException;
import org.rocksdb.RocksDB;
import org.rocksdb.WriteOptions;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Functional executor for RocksDB batch operations.
 * 
 * <p>This class provides a high-level, functional interface for executing
 * batch operations on RocksDB. It handles all the infrastructure concerns
 * such as batch context management, resource cleanup, error handling, and
 * provides both synchronous and asynchronous execution modes.</p>
 * 
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>Functional programming model for batch operations</li>
 *   <li>Automatic resource management and cleanup</li>
 *   <li>Comprehensive error handling with context</li>
 *   <li>Synchronous and asynchronous execution modes</li>
 *   <li>Read-your-writes consistency within batches</li>
 *   <li>Configurable write options and execution context</li>
 * </ul>
 * 
 * <p><b>Usage Examples:</b></p>
 * <pre>{@code
 * RocksDbBatchExecutor executor = new RocksDbBatchExecutor(database);
 * 
 * // Synchronous batch execution
 * Integer result = executor.executeBatch(batch -> {
 *     batch.put(cfNodes, nodeKey1, nodeData1);
 *     batch.put(cfNodes, nodeKey2, nodeData2);
 *     return 2; // Number of nodes stored
 * });
 * 
 * // Asynchronous batch execution
 * CompletableFuture<byte[]> future = executor.executeBatchAsync(batch -> {
 *     batch.put(cfRoots, versionKey, rootHash);
 *     return batch.get(cfRoots, versionKey); // Verify storage
 * });
 * 
 * // With custom write options
 * executor.withWriteOptions(opts -> opts.setSync(true))
 *         .executeBatch(batch -> {
 *             batch.put(cfNodes, criticalKey, criticalData);
 *             return null;
 *         });
 * }</pre>
 * 
 * <p><b>Error Handling:</b> Failed operations result in RocksDbBatchException
 * with detailed context about the failure. The batch context is automatically
 * cleaned up even in case of errors.</p>
 * 
 * @author Bloxbean Project
 * @since 0.6.0
 */
public final class RocksDbBatchExecutor {
    
    /**
     * The RocksDB instance for batch operations.
     */
    private final RocksDB db;
    
    /**
     * Default write options for batch commits.
     */
    private WriteOptions defaultWriteOptions;
    
    /**
     * Default executor for asynchronous operations.
     */
    private Executor defaultAsyncExecutor;
    
    /**
     * Metrics collector for batch operations.
     */
    private BatchMetrics metrics;
    
    /**
     * Creates a new batch executor for the specified database.
     * 
     * @param db the RocksDB instance (must not be null)
     * @throws IllegalArgumentException if db is null
     */
    public RocksDbBatchExecutor(RocksDB db) {
        this.db = Objects.requireNonNull(db, "RocksDB instance cannot be null");
        this.defaultWriteOptions = new WriteOptions();
        this.defaultAsyncExecutor = ForkJoinPool.commonPool();
        this.metrics = new BatchMetrics();
    }
    
    /**
     * Configures the default write options for batch operations.
     * 
     * <p>These options will be used for all batch commits unless overridden
     * in specific operations. The executor takes ownership of the provided
     * WriteOptions and will close them when the executor is closed.</p>
     * 
     * @param writeOptions the write options to use (null for RocksDB defaults)
     * @return this executor for method chaining
     */
    public RocksDbBatchExecutor withWriteOptions(WriteOptions writeOptions) {
        // Close the old options if we created them
        if (this.defaultWriteOptions != null) {
            this.defaultWriteOptions.close();
        }
        this.defaultWriteOptions = writeOptions != null ? writeOptions : new WriteOptions();
        return this;
    }
    
    /**
     * Configures the default executor for asynchronous operations.
     * 
     * @param executor the executor to use for async operations (null for common pool)
     * @return this executor for method chaining
     */
    public RocksDbBatchExecutor withAsyncExecutor(Executor executor) {
        this.defaultAsyncExecutor = executor != null ? executor : ForkJoinPool.commonPool();
        return this;
    }
    
    /**
     * Returns the metrics collector for this batch executor.
     * 
     * @return the batch metrics collector
     */
    public BatchMetrics getMetrics() {
        return metrics;
    }
    
    /**
     * Executes a batch operation synchronously.
     * 
     * <p>This method creates a batch context, executes the operation, commits
     * the batch, and handles cleanup automatically. If the operation throws
     * an exception, the batch is rolled back and resources are cleaned up.</p>
     * 
     * @param <T> the return type of the operation
     * @param operation the batch operation to execute (must not be null)
     * @return the result of the operation
     * @throws RocksDbStorageException if a storage error occurs
     * @throws RuntimeException if the operation throws a non-storage exception
     */
    public <T> T executeBatch(BatchOperation<T> operation) throws RocksDbStorageException {
        return executeBatch(operation, defaultWriteOptions);
    }
    
    /**
     * Executes a batch operation synchronously with custom write options.
     * 
     * @param <T> the return type of the operation
     * @param operation the batch operation to execute (must not be null)
     * @param writeOptions the write options for this specific operation
     * @return the result of the operation
     * @throws RocksDbStorageException if a storage error occurs
     * @throws RuntimeException if the operation throws a non-storage exception
     */
    public <T> T executeBatch(BatchOperation<T> operation, WriteOptions writeOptions) throws RocksDbStorageException {
        Objects.requireNonNull(operation, "Batch operation cannot be null");
        
        try (var timer = metrics.startBatchTimer();
             RocksDbBatchContext batch = RocksDbBatchContext.create(db, writeOptions)) {
            
            T result = operation.execute(batch);
            batch.commit();
            
            // Record successful operation metrics
            timer.recordSuccess(batch.getOperationCount(), batch.getBatchSizeBytes());
            return result;
            
        } catch (RocksDbStorageException e) {
            // Storage exceptions are passed through as-is
            throw e;
        } catch (Exception e) {
            // Wrap other exceptions in a RuntimeException
            throw new RuntimeException("Batch operation failed unexpectedly", e);
        }
    }
    
    /**
     * Executes a batch operation asynchronously.
     * 
     * <p>The operation is executed on the configured async executor. The
     * returned CompletableFuture will complete normally with the operation
     * result, or complete exceptionally if an error occurs.</p>
     * 
     * @param <T> the return type of the operation
     * @param operation the batch operation to execute (must not be null)
     * @return a CompletableFuture that will complete with the operation result
     */
    public <T> CompletableFuture<T> executeBatchAsync(BatchOperation<T> operation) {
        return executeBatchAsync(operation, defaultAsyncExecutor);
    }
    
    /**
     * Executes a batch operation asynchronously on a specific executor.
     * 
     * @param <T> the return type of the operation
     * @param operation the batch operation to execute (must not be null)
     * @param executor the executor to run the operation on (must not be null)
     * @return a CompletableFuture that will complete with the operation result
     */
    public <T> CompletableFuture<T> executeBatchAsync(BatchOperation<T> operation, Executor executor) {
        Objects.requireNonNull(operation, "Batch operation cannot be null");
        Objects.requireNonNull(executor, "Executor cannot be null");
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeBatch(operation);
            } catch (RocksDbStorageException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }
    
    /**
     * Executes a batch operation asynchronously with custom write options.
     * 
     * @param <T> the return type of the operation
     * @param operation the batch operation to execute (must not be null)
     * @param writeOptions the write options for this specific operation
     * @return a CompletableFuture that will complete with the operation result
     */
    public <T> CompletableFuture<T> executeBatchAsync(BatchOperation<T> operation, WriteOptions writeOptions) {
        return executeBatchAsync(operation, writeOptions, defaultAsyncExecutor);
    }
    
    /**
     * Executes a batch operation asynchronously with custom write options and executor.
     * 
     * @param <T> the return type of the operation
     * @param operation the batch operation to execute (must not be null)
     * @param writeOptions the write options for this specific operation
     * @param executor the executor to run the operation on (must not be null)
     * @return a CompletableFuture that will complete with the operation result
     */
    public <T> CompletableFuture<T> executeBatchAsync(BatchOperation<T> operation, WriteOptions writeOptions, Executor executor) {
        Objects.requireNonNull(operation, "Batch operation cannot be null");
        Objects.requireNonNull(executor, "Executor cannot be null");
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeBatch(operation, writeOptions);
            } catch (RocksDbStorageException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }
    
    /**
     * Executes multiple batch operations asynchronously in parallel.
     * 
     * <p>Each operation runs on its own task and they execute concurrently.
     * This is different from {@link #executeBatchSequence} which executes
     * operations sequentially within a single batch.</p>
     * 
     * @param operations the batch operations to execute in parallel
     * @return a CompletableFuture containing an array of results
     */
    @SafeVarargs
    public final CompletableFuture<Object[]> executeBatchParallel(BatchOperation<?>... operations) {
        Objects.requireNonNull(operations, "Operations array cannot be null");
        
        if (operations.length == 0) {
            return CompletableFuture.completedFuture(new Object[0]);
        }
        
        @SuppressWarnings("unchecked")
        CompletableFuture<Object>[] futures = new CompletableFuture[operations.length];
        
        for (int i = 0; i < operations.length; i++) {
            final BatchOperation<?> op = operations[i];
            if (op == null) {
                futures[i] = CompletableFuture.failedFuture(
                    new IllegalArgumentException("Operation at index " + i + " is null"));
            } else {
                futures[i] = executeBatchAsync(op).thenApply(result -> (Object) result);
            }
        }
        
        return CompletableFuture.allOf(futures)
            .thenApply(v -> {
                Object[] results = new Object[futures.length];
                for (int i = 0; i < futures.length; i++) {
                    results[i] = futures[i].join();
                }
                return results;
            });
    }
    
    /**
     * Creates a CompletableFuture that will complete when a batch reaches a certain size.
     * 
     * <p>This method enables efficient batch processing by allowing operations to be
     * accumulated and executed when a threshold is reached, reducing the overhead
     * of frequent small batches.</p>
     * 
     * @param targetOperationCount the number of operations to accumulate before execution
     * @param timeoutDuration maximum time to wait before executing a partial batch
     * @return a BatchAccumulator for collecting operations
     */
    public BatchAccumulator createBatchAccumulator(int targetOperationCount, Duration timeoutDuration) {
        return new BatchAccumulator(this, targetOperationCount, timeoutDuration);
    }
    
    /**
     * Executes multiple batch operations in sequence within a single batch.
     * 
     * <p>This method is useful for composing multiple operations that should
     * all succeed or all fail together. All operations share the same batch
     * context and can benefit from read-your-writes consistency.</p>
     * 
     * @param operations the batch operations to execute in sequence
     * @return an array of results in the same order as the operations
     * @throws RocksDbStorageException if any storage error occurs
     */
    @SafeVarargs
    public final Object[] executeBatchSequence(BatchOperation<?>... operations) throws RocksDbStorageException {
        Objects.requireNonNull(operations, "Operations array cannot be null");
        
        if (operations.length == 0) {
            return new Object[0];
        }
        
        try (var timer = metrics.startBatchTimer();
             RocksDbBatchContext batch = RocksDbBatchContext.create(db, defaultWriteOptions)) {
            
            Object[] results = new Object[operations.length];
            
            for (int i = 0; i < operations.length; i++) {
                if (operations[i] == null) {
                    throw new IllegalArgumentException("Operation at index " + i + " is null");
                }
                results[i] = operations[i].execute(batch);
            }
            
            batch.commit();
            
            // Record successful sequence operation metrics
            timer.recordSuccess(batch.getOperationCount(), batch.getBatchSizeBytes());
            return results;
            
        } catch (RocksDbStorageException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Batch sequence operation failed", e);
        }
    }
    
    /**
     * Creates a batch operation that performs no operations.
     * 
     * <p>This is useful for testing or as a null object pattern.</p>
     * 
     * @param <T> the return type
     * @param result the result to return
     * @return a batch operation that returns the specified result
     */
    public static <T> BatchOperation<T> noop(T result) {
        return batch -> result;
    }
    
    /**
     * Creates a batch operation that combines multiple operations.
     * 
     * <p>All operations are executed in sequence within the same batch context.
     * This is useful for building complex operations from simpler ones.</p>
     * 
     * @param <T> the final return type
     * @param operations the operations to combine
     * @param resultSelector function to select the final result from all operation results
     * @return a combined batch operation
     */
    @SafeVarargs
    public static <T> BatchOperation<T> combine(java.util.function.Function<Object[], T> resultSelector, 
                                                BatchOperation<?>... operations) {
        Objects.requireNonNull(resultSelector, "Result selector cannot be null");
        Objects.requireNonNull(operations, "Operations array cannot be null");
        
        return batch -> {
            Object[] results = new Object[operations.length];
            for (int i = 0; i < operations.length; i++) {
                results[i] = operations[i].execute(batch);
            }
            return resultSelector.apply(results);
        };
    }
    
    /**
     * Closes this batch executor and releases associated resources.
     * 
     * <p>After calling this method, the executor should not be used for
     * further operations. This method is idempotent.</p>
     */
    public void close() {
        if (defaultWriteOptions != null) {
            defaultWriteOptions.close();
            defaultWriteOptions = null;
        }
    }
}