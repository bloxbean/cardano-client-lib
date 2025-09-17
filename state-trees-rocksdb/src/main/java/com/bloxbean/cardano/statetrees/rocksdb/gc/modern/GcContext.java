package com.bloxbean.cardano.statetrees.rocksdb.gc.modern;

import com.bloxbean.cardano.statetrees.rocksdb.gc.GcOptions;
import com.bloxbean.cardano.statetrees.rocksdb.gc.RetentionPolicy;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Execution context for modern garbage collection operations.
 * 
 * <p>This class provides dependency injection for GC strategies, containing
 * all the dependencies and configuration needed for GC execution. It follows
 * the dependency injection pattern to enable better testability, flexibility,
 * and separation of concerns.</p>
 * 
 * <p><b>Key Components:</b></p>
 * <ul>
 *   <li>StorageRepository - abstracted storage operations</li>
 *   <li>RetentionPolicy - rules for what to keep/delete</li>
 *   <li>GcOptions - configuration for GC execution</li>
 *   <li>ProgressReporter - optional progress tracking</li>
 *   <li>Execution metadata - ID, timestamp, etc.</li>
 * </ul>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // Create context with required dependencies
 * GcContext context = GcContext.builder()
 *     .repository(storageRepository)
 *     .retentionPolicy(RetentionPolicy.keepLastVersions(100))
 *     .options(GcOptions.builder().setBatchSize(1000).build())
 *     .progressReporter(progress -> logger.info("GC progress: {}%", progress.getPercentComplete()))
 *     .build();
 * 
 * // Execute GC with injected dependencies
 * ModernGcStrategy strategy = new ModernRefcountGcStrategy();
 * GcMetrics metrics = strategy.execute(context);
 * }</pre>
 * 
 * <p><b>Immutability:</b> This class is immutable after construction,
 * ensuring thread safety and preventing accidental modification during
 * GC execution.</p>
 * 
 * @author Bloxbean Project
 * @since 0.6.0
 */
public final class GcContext {
    
    private final String executionId;
    private final Instant startTime;
    private final StorageRepository repository;
    private final RetentionPolicy retentionPolicy;
    private final GcOptions options;
    private final ProgressReporter progressReporter;
    
    /**
     * Private constructor - use builder to create instances.
     */
    private GcContext(Builder builder) {
        this.executionId = builder.executionId != null ? builder.executionId : UUID.randomUUID().toString();
        this.startTime = builder.startTime != null ? builder.startTime : Instant.now();
        this.repository = Objects.requireNonNull(builder.repository, "Storage repository cannot be null");
        this.retentionPolicy = Objects.requireNonNull(builder.retentionPolicy, "Retention policy cannot be null");
        this.options = Objects.requireNonNull(builder.options, "GC options cannot be null");
        this.progressReporter = builder.progressReporter; // Can be null
    }
    
    /**
     * Returns the unique execution ID for this GC run.
     * 
     * @return the execution ID
     */
    public String getExecutionId() {
        return executionId;
    }
    
    /**
     * Returns the start time of this GC execution.
     * 
     * @return the start time
     */
    public Instant getStartTime() {
        return startTime;
    }
    
    /**
     * Returns the storage repository for this GC execution.
     * 
     * @return the storage repository
     */
    public StorageRepository getRepository() {
        return repository;
    }
    
    /**
     * Returns the retention policy for this GC execution.
     * 
     * @return the retention policy
     */
    public RetentionPolicy getRetentionPolicy() {
        return retentionPolicy;
    }
    
    /**
     * Returns the GC options for this execution.
     * 
     * @return the GC options
     */
    public GcOptions getOptions() {
        return options;
    }
    
    /**
     * Returns the progress reporter for this execution, if any.
     * 
     * @return the progress reporter, or empty if none configured
     */
    public Optional<ProgressReporter> getProgressReporter() {
        return Optional.ofNullable(progressReporter);
    }
    
    /**
     * Reports progress if a progress reporter is configured.
     * 
     * @param progress the progress information to report
     */
    public void reportProgress(GcProgress progress) {
        if (progressReporter != null) {
            progressReporter.reportProgress(progress);
        }
    }
    
    /**
     * Creates a new builder for constructing GcContext instances.
     * 
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Creates a new builder initialized with values from this context.
     * 
     * @return a builder with current values
     */
    public Builder toBuilder() {
        return new Builder()
            .executionId(executionId)
            .startTime(startTime)
            .repository(repository)
            .retentionPolicy(retentionPolicy)
            .options(options)
            .progressReporter(progressReporter);
    }
    
    @Override
    public String toString() {
        return String.format(
            "GcContext[id=%s, start=%s, policy=%s, options=%s, hasReporter=%s]",
            executionId, startTime, retentionPolicy, options, progressReporter != null
        );
    }
    
    /**
     * Builder for creating GcContext instances.
     */
    public static final class Builder {
        private String executionId;
        private Instant startTime;
        private StorageRepository repository;
        private RetentionPolicy retentionPolicy;
        private GcOptions options;
        private ProgressReporter progressReporter;
        
        private Builder() {}
        
        /**
         * Sets the execution ID for this GC run.
         * 
         * @param executionId the execution ID (null for auto-generated UUID)
         * @return this builder
         */
        public Builder executionId(String executionId) {
            this.executionId = executionId;
            return this;
        }
        
        /**
         * Sets the start time for this GC execution.
         * 
         * @param startTime the start time (null for current time)
         * @return this builder
         */
        public Builder startTime(Instant startTime) {
            this.startTime = startTime;
            return this;
        }
        
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
         * Sets the retention policy for this GC execution.
         * 
         * @param retentionPolicy the retention policy (required)
         * @return this builder
         */
        public Builder retentionPolicy(RetentionPolicy retentionPolicy) {
            this.retentionPolicy = retentionPolicy;
            return this;
        }
        
        /**
         * Sets the GC options for this execution.
         * 
         * @param options the GC options (required)
         * @return this builder
         */
        public Builder options(GcOptions options) {
            this.options = options;
            return this;
        }
        
        /**
         * Sets the progress reporter for this execution.
         * 
         * @param progressReporter the progress reporter (optional)
         * @return this builder
         */
        public Builder progressReporter(ProgressReporter progressReporter) {
            this.progressReporter = progressReporter;
            return this;
        }
        
        /**
         * Builds the GcContext instance.
         * 
         * @return a new GcContext instance
         * @throws IllegalArgumentException if required fields are missing
         */
        public GcContext build() {
            return new GcContext(this);
        }
    }
}