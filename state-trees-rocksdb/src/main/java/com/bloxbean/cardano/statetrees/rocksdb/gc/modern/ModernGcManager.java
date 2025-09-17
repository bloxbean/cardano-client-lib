package com.bloxbean.cardano.statetrees.rocksdb.gc.modern;

import com.bloxbean.cardano.statetrees.rocksdb.gc.GcOptions;
import com.bloxbean.cardano.statetrees.rocksdb.gc.RetentionPolicy;
import com.bloxbean.cardano.statetrees.rocksdb.exceptions.RocksDbStorageException;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Modern garbage collection manager with dependency injection and builder patterns.
 * 
 * <p>This class represents the evolution of the original GcManager, providing
 * a clean, modern API with dependency injection, builder patterns, and
 * comprehensive async support. It orchestrates GC execution using the modern
 * strategy interface while maintaining backwards compatibility where needed.</p>
 * 
 * <p><b>Key Improvements:</b></p>
 * <ul>
 *   <li>Builder pattern for flexible configuration</li>
 *   <li>Dependency injection for better testability</li>
 *   <li>Modern async patterns with CompletableFuture</li>
 *   <li>Comprehensive metrics and progress reporting</li>
 *   <li>Type-safe operations with structured error handling</li>
 * </ul>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // Create GC manager with builder pattern
 * ModernGcManager gcManager = ModernGcManager.builder()
 *     .repository(storageRepository)
 *     .defaultRetentionPolicy(RetentionPolicy.keepLastVersions(100))
 *     .defaultOptions(GcOptions.builder().setBatchSize(1000).build())
 *     .progressReporter(ProgressReporter.console())
 *     .asyncExecutor(customExecutor)
 *     .build();
 * 
 * // Execute GC synchronously
 * GcMetricsSummary summary = gcManager.executeSync(new ModernRefcountGcStrategy());
 * System.out.println(summary.formatSummary());
 * 
 * // Execute GC asynchronously
 * CompletableFuture<GcMetricsSummary> future = gcManager.executeAsync(strategy)
 *     .thenApply(summary -> {
 *         logger.info("GC completed: {}", summary);
 *         return summary;
 *     });
 * }</pre>
 * 
 * @author Bloxbean Project
 * @since 0.6.0
 */
public final class ModernGcManager {
    
    private final StorageRepository repository;
    private final RetentionPolicy defaultRetentionPolicy;
    private final GcOptions defaultOptions;
    private final ProgressReporter defaultProgressReporter;
    private final Executor defaultAsyncExecutor;
    
    /**
     * Private constructor - use builder to create instances.
     */
    private ModernGcManager(Builder builder) {
        this.repository = Objects.requireNonNull(builder.repository, "Storage repository cannot be null");
        this.defaultRetentionPolicy = Objects.requireNonNull(builder.defaultRetentionPolicy, "Default retention policy cannot be null");
        this.defaultOptions = Objects.requireNonNull(builder.defaultOptions, "Default GC options cannot be null");
        this.defaultProgressReporter = builder.defaultProgressReporter; // Can be null
        this.defaultAsyncExecutor = builder.defaultAsyncExecutor != null ? 
            builder.defaultAsyncExecutor : ForkJoinPool.commonPool();
    }
    
    /**
     * Executes GC synchronously using the configured defaults.
     * 
     * @param strategy the GC strategy to execute
     * @return comprehensive metrics summary
     * @throws RocksDbStorageException if GC execution fails
     */
    public GcMetricsSummary executeSync(ModernGcStrategy strategy) throws RocksDbStorageException {
        return executeSync(strategy, defaultRetentionPolicy, defaultOptions, defaultProgressReporter);
    }
    
    /**
     * Executes GC synchronously with custom retention policy.
     * 
     * @param strategy the GC strategy to execute
     * @param retentionPolicy the retention policy to use
     * @return comprehensive metrics summary
     * @throws RocksDbStorageException if GC execution fails
     */
    public GcMetricsSummary executeSync(ModernGcStrategy strategy, RetentionPolicy retentionPolicy) throws RocksDbStorageException {
        return executeSync(strategy, retentionPolicy, defaultOptions, defaultProgressReporter);
    }
    
    /**
     * Executes GC synchronously with custom configuration.
     * 
     * @param strategy the GC strategy to execute
     * @param retentionPolicy the retention policy to use
     * @param options the GC options to use
     * @param progressReporter the progress reporter to use (null for none)
     * @return comprehensive metrics summary
     * @throws RocksDbStorageException if GC execution fails
     */
    public GcMetricsSummary executeSync(
            ModernGcStrategy strategy, 
            RetentionPolicy retentionPolicy, 
            GcOptions options, 
            ProgressReporter progressReporter) throws RocksDbStorageException {
        
        Objects.requireNonNull(strategy, "GC strategy cannot be null");
        Objects.requireNonNull(retentionPolicy, "Retention policy cannot be null");
        Objects.requireNonNull(options, "GC options cannot be null");
        
        // Create execution context
        GcContext context = GcContext.builder()
            .repository(repository)
            .retentionPolicy(retentionPolicy)
            .options(options)
            .progressReporter(progressReporter)
            .build();
        
        // Validate context
        strategy.validateContext(context);
        
        // Execute strategy
        GcMetrics metrics = strategy.execute(context);
        
        // Mark as completed and return summary
        metrics.markCompleted();
        return metrics.createSummary();
    }
    
    /**
     * Executes GC asynchronously using the configured defaults.
     * 
     * @param strategy the GC strategy to execute
     * @return CompletableFuture with metrics summary
     */
    public CompletableFuture<GcMetricsSummary> executeAsync(ModernGcStrategy strategy) {
        return executeAsync(strategy, defaultRetentionPolicy, defaultOptions, defaultProgressReporter);
    }
    
    /**
     * Executes GC asynchronously with custom retention policy.
     * 
     * @param strategy the GC strategy to execute
     * @param retentionPolicy the retention policy to use
     * @return CompletableFuture with metrics summary
     */
    public CompletableFuture<GcMetricsSummary> executeAsync(ModernGcStrategy strategy, RetentionPolicy retentionPolicy) {
        return executeAsync(strategy, retentionPolicy, defaultOptions, defaultProgressReporter);
    }
    
    /**
     * Executes GC asynchronously with custom configuration.
     * 
     * @param strategy the GC strategy to execute
     * @param retentionPolicy the retention policy to use
     * @param options the GC options to use
     * @param progressReporter the progress reporter to use (null for none)
     * @return CompletableFuture with metrics summary
     */
    public CompletableFuture<GcMetricsSummary> executeAsync(
            ModernGcStrategy strategy, 
            RetentionPolicy retentionPolicy, 
            GcOptions options, 
            ProgressReporter progressReporter) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeSync(strategy, retentionPolicy, options, progressReporter);
            } catch (RocksDbStorageException e) {
                throw new RuntimeException("Async GC execution failed", e);
            }
        }, defaultAsyncExecutor);
    }
    
    /**
     * Executes GC asynchronously on a custom executor.
     * 
     * @param strategy the GC strategy to execute
     * @param executor the executor to run on
     * @return CompletableFuture with metrics summary
     */
    public CompletableFuture<GcMetricsSummary> executeAsync(ModernGcStrategy strategy, Executor executor) {
        Objects.requireNonNull(executor, "Executor cannot be null");
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeSync(strategy);
            } catch (RocksDbStorageException e) {
                throw new RuntimeException("Async GC execution failed", e);
            }
        }, executor);
    }
    
    /**
     * Creates a GC execution context with the manager's defaults.
     * 
     * @return a pre-configured GC context
     */
    public GcContext createDefaultContext() {
        return GcContext.builder()
            .repository(repository)
            .retentionPolicy(defaultRetentionPolicy)
            .options(defaultOptions)
            .progressReporter(defaultProgressReporter)
            .build();
    }
    
    /**
     * Creates a GC execution context with custom retention policy.
     * 
     * @param retentionPolicy the retention policy to use
     * @return a pre-configured GC context
     */
    public GcContext createContext(RetentionPolicy retentionPolicy) {
        return GcContext.builder()
            .repository(repository)
            .retentionPolicy(retentionPolicy)
            .options(defaultOptions)
            .progressReporter(defaultProgressReporter)
            .build();
    }
    
    /**
     * Gets the storage repository used by this manager.
     * 
     * @return the storage repository
     */
    public StorageRepository getRepository() {
        return repository;
    }
    
    /**
     * Gets the default retention policy.
     * 
     * @return the default retention policy
     */
    public RetentionPolicy getDefaultRetentionPolicy() {
        return defaultRetentionPolicy;
    }
    
    /**
     * Gets the default GC options.
     * 
     * @return the default GC options
     */
    public GcOptions getDefaultOptions() {
        return defaultOptions;
    }
    
    /**
     * Creates a new builder for constructing ModernGcManager instances.
     * 
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for creating ModernGcManager instances.
     */
    public static final class Builder {
        private StorageRepository repository;
        private RetentionPolicy defaultRetentionPolicy;
        private GcOptions defaultOptions;
        private ProgressReporter defaultProgressReporter;
        private Executor defaultAsyncExecutor;
        
        private Builder() {}
        
        /**
         * Sets the storage repository for GC operations.
         * 
         * @param repository the storage repository (required)
         * @return this builder
         */
        public Builder repository(StorageRepository repository) {
            this.repository = repository;
            return this;
        }
        
        /**
         * Sets the default retention policy.
         * 
         * @param retentionPolicy the default retention policy (required)
         * @return this builder
         */
        public Builder defaultRetentionPolicy(RetentionPolicy retentionPolicy) {
            this.defaultRetentionPolicy = retentionPolicy;
            return this;
        }
        
        /**
         * Sets the default GC options.
         * 
         * @param options the default GC options (required)
         * @return this builder
         */
        public Builder defaultOptions(GcOptions options) {
            this.defaultOptions = options;
            return this;
        }
        
        /**
         * Sets the default progress reporter.
         * 
         * @param progressReporter the default progress reporter (optional)
         * @return this builder
         */
        public Builder progressReporter(ProgressReporter progressReporter) {
            this.defaultProgressReporter = progressReporter;
            return this;
        }
        
        /**
         * Sets the default executor for async operations.
         * 
         * @param executor the executor for async operations (optional, defaults to common pool)
         * @return this builder
         */
        public Builder asyncExecutor(Executor executor) {
            this.defaultAsyncExecutor = executor;
            return this;
        }
        
        /**
         * Builds the ModernGcManager instance.
         * 
         * @return a new ModernGcManager instance
         * @throws IllegalArgumentException if required fields are missing
         */
        public ModernGcManager build() {
            return new ModernGcManager(this);
        }
    }
}