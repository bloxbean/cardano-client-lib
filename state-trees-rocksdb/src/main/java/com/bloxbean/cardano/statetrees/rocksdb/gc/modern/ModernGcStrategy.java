package com.bloxbean.cardano.statetrees.rocksdb.gc.modern;

import com.bloxbean.cardano.statetrees.rocksdb.gc.GcOptions;
import com.bloxbean.cardano.statetrees.rocksdb.gc.RetentionPolicy;
import com.bloxbean.cardano.statetrees.rocksdb.exceptions.RocksDbStorageException;

import java.util.concurrent.CompletableFuture;

/**
 * Modern garbage collection strategy interface with dependency injection support.
 *
 * <p>This interface represents a significant evolution from the original GcStrategy,
 * providing better separation of concerns, dependency injection, and modern async
 * patterns. Strategies focus purely on GC algorithms while storage operations
 * are delegated to the injected StorageRepository.</p>
 *
 * <p><b>Key Improvements:</b></p>
 * <ul>
 *   <li>Clean separation between algorithm and storage operations</li>
 *   <li>Dependency injection for better testability and flexibility</li>
 *   <li>Type-safe operations using modern key classes</li>
 *   <li>Support for both synchronous and asynchronous execution</li>
 *   <li>Structured metrics and reporting</li>
 * </ul>
 *
 * <p><b>Implementation Example:</b></p>
 * <pre>{@code
 * public class ModernRefcountGcStrategy implements ModernGcStrategy {
 *     public GcMetrics execute(GcContext context) throws RocksDbStorageException {
 *         StorageRepository repo = context.getRepository();
 *         RetentionPolicy policy = context.getRetentionPolicy();
 *
 *         // Algorithm focuses on GC logic, not storage details
 *         Stream<RootHashKey> rootsToDelete = repo.getAllRoots()
 *             .filter(root -> policy.shouldDelete(root));
 *
 *         // Process deletions and return metrics
 *         return processRootDeletions(rootsToDelete, context);
 *     }
 * }
 * }</pre>
 *
 * @author Bloxbean Project
 * @since 0.6.0
 */
public interface ModernGcStrategy {

    /**
     * Executes the garbage collection strategy synchronously.
     *
     * <p>Implementations should focus on the GC algorithm logic and delegate
     * all storage operations to the provided repository. The method should
     * return comprehensive metrics about the GC execution.</p>
     *
     * @param context the GC execution context containing all dependencies
     * @return detailed metrics about the GC execution
     * @throws RocksDbStorageException if a storage error occurs during GC
     */
    GcMetrics execute(GcContext context) throws RocksDbStorageException;

    /**
     * Executes the garbage collection strategy asynchronously.
     *
     * <p>The default implementation wraps the synchronous execute method
     * in a CompletableFuture. Strategies can override this for more
     * sophisticated async behavior.</p>
     *
     * @param context the GC execution context containing all dependencies
     * @return a CompletableFuture that will complete with GC metrics
     */
    default CompletableFuture<GcMetrics> executeAsync(GcContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return execute(context);
            } catch (RocksDbStorageException e) {
                throw new RuntimeException("Async GC execution failed", e);
            }
        });
    }

    /**
     * Returns a human-readable name for this GC strategy.
     *
     * @return the strategy name
     */
    default String getStrategyName() {
        return this.getClass().getSimpleName();
    }

    /**
     * Returns a description of what this GC strategy does.
     *
     * @return the strategy description
     */
    default String getDescription() {
        return "Modern garbage collection strategy";
    }

    /**
     * Estimates the amount of work this strategy will perform.
     *
     * <p>This method can be used by progress reporting systems to provide
     * better estimates of completion time. The default implementation
     * returns an unknown estimate.</p>
     *
     * @param context the GC execution context
     * @return estimated work units, or -1 if unknown
     */
    default long estimateWorkUnits(GcContext context) {
        return -1; // Unknown
    }

    /**
     * Validates that this strategy can run with the provided context.
     *
     * <p>This method allows strategies to perform pre-flight checks
     * and fail fast if the context is incompatible. The default
     * implementation performs basic validation.</p>
     *
     * @param context the GC execution context
     * @throws IllegalArgumentException if the context is invalid
     * @throws RocksDbStorageException  if storage validation fails
     */
    default void validateContext(GcContext context) throws RocksDbStorageException {
        if (context == null) {
            throw new IllegalArgumentException("GC context cannot be null");
        }
        if (context.getRepository() == null) {
            throw new IllegalArgumentException("Storage repository cannot be null");
        }
        if (context.getRetentionPolicy() == null) {
            throw new IllegalArgumentException("Retention policy cannot be null");
        }
        if (context.getOptions() == null) {
            throw new IllegalArgumentException("GC options cannot be null");
        }
    }
}
