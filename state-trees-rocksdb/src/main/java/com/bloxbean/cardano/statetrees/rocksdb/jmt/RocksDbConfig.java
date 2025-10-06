package com.bloxbean.cardano.statetrees.rocksdb.jmt;

import org.rocksdb.*;

import java.util.function.Consumer;

/**
 * Configurable RocksDB settings for JMT storage backend.
 *
 * <p>This class provides preset profiles optimized for different workloads while allowing
 * full customization for advanced use cases. The configuration affects both DBOptions and
 * ColumnFamilyOptions used by {@link RocksDbJmtStore}.
 *
 * <h3>Usage Examples:</h3>
 * <pre>
 * // Use preset profile for high-throughput write workload
 * RocksDbConfig config = RocksDbConfig.highThroughput();
 * RocksDbJmtStore store = RocksDbJmtStore.open(dbPath,
 *     RocksDbJmtStore.Options.builder()
 *         .rocksDbConfig(config)
 *         .build());
 *
 * // Customize specific settings
 * RocksDbConfig config = RocksDbConfig.builder()
 *     .writeBufferSize(512 * 1024 * 1024)  // 512MB
 *     .maxBackgroundJobs(16)
 *     .blockCacheSize(1024 * 1024 * 1024)  // 1GB
 *     .build();
 *
 * // Full control with custom configurators
 * RocksDbConfig config = RocksDbConfig.builder()
 *     .profile(RocksDbConfig.Profile.BALANCED)
 *     .customDbOptions(dbOpts -&gt; {
 *         dbOpts.setMaxOpenFiles(1000);
 *         dbOpts.setStatsDumpPeriodSec(600);
 *     })
 *     .customCfOptions(cfOpts -&gt; {
 *         cfOpts.setCompressionType(CompressionType.LZ4_COMPRESSION);
 *     })
 *     .build();
 * </pre>
 *
 * @see RocksDbJmtStore.Options
 */
public final class RocksDbConfig {

    /**
     * Preset configuration profiles optimized for different workloads.
     */
    public enum Profile {
        /**
         * High-throughput write-heavy workload (production blockchain nodes).
         * - Large write buffers (256MB per CF)
         * - More background compaction threads (8 jobs)
         * - Large block cache (512MB)
         * - Optimized for sustained write performance
         *
         * <p><b>Use when:</b> High insert/update rate, plenty of RAM available
         */
        HIGH_THROUGHPUT,

        /**
         * Balanced configuration for general-purpose usage.
         * - Moderate write buffers (128MB per CF)
         * - Standard compaction (4 jobs)
         * - Medium block cache (256MB)
         * - Good mix of read/write performance
         *
         * <p><b>Use when:</b> Mixed workload, moderate resource constraints
         */
        BALANCED,

        /**
         * Memory-constrained environment (development, testing, edge devices).
         * - Small write buffers (64MB per CF)
         * - Fewer background jobs (2 jobs)
         * - Minimal block cache (128MB)
         * - Trades performance for lower memory footprint
         *
         * <p><b>Use when:</b> Limited RAM, development/testing environments
         */
        LOW_MEMORY,

        /**
         * No preset applied - uses RocksDB defaults.
         * Suitable for custom configuration or minimal overhead.
         */
        DEFAULT
    }

    private final Profile profile;
    private final Long writeBufferSize;
    private final Integer maxWriteBufferNumber;
    private final Integer minWriteBufferNumberToMerge;
    private final Integer maxBackgroundJobs;
    private final Integer maxSubcompactions;
    private final Long blockCacheSize;
    private final Integer bloomFilterBits;
    private final Long bytesPerSync;
    private final Boolean levelCompactionDynamicLevelBytes;
    private final CompressionType compressionType;
    private final Consumer<DBOptions> customDbOptions;
    private final Consumer<ColumnFamilyOptions> customCfOptions;

    private RocksDbConfig(Builder builder) {
        this.profile = builder.profile;
        this.writeBufferSize = builder.writeBufferSize;
        this.maxWriteBufferNumber = builder.maxWriteBufferNumber;
        this.minWriteBufferNumberToMerge = builder.minWriteBufferNumberToMerge;
        this.maxBackgroundJobs = builder.maxBackgroundJobs;
        this.maxSubcompactions = builder.maxSubcompactions;
        this.blockCacheSize = builder.blockCacheSize;
        this.bloomFilterBits = builder.bloomFilterBits;
        this.bytesPerSync = builder.bytesPerSync;
        this.levelCompactionDynamicLevelBytes = builder.levelCompactionDynamicLevelBytes;
        this.compressionType = builder.compressionType;
        this.customDbOptions = builder.customDbOptions;
        this.customCfOptions = builder.customCfOptions;
    }

    /**
     * Creates a HIGH_THROUGHPUT profile configuration.
     * Optimized for production blockchain nodes with high write rates.
     *
     * @return high-throughput configuration
     */
    public static RocksDbConfig highThroughput() {
        return builder().profile(Profile.HIGH_THROUGHPUT).build();
    }

    /**
     * Creates a BALANCED profile configuration.
     * Good default for most use cases.
     *
     * @return balanced configuration
     */
    public static RocksDbConfig balanced() {
        return builder().profile(Profile.BALANCED).build();
    }

    /**
     * Creates a LOW_MEMORY profile configuration.
     * Optimized for memory-constrained environments.
     *
     * @return low-memory configuration
     */
    public static RocksDbConfig lowMemory() {
        return builder().profile(Profile.LOW_MEMORY).build();
    }

    /**
     * Creates a configuration using RocksDB defaults.
     * No preset optimizations applied.
     *
     * @return default configuration
     */
    public static RocksDbConfig defaults() {
        return builder().profile(Profile.DEFAULT).build();
    }

    /**
     * Creates a new builder for custom configuration.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Applies this configuration to a DBOptions instance.
     *
     * @param dbOptions the DBOptions to configure
     */
    public void applyToDbOptions(DBOptions dbOptions) {
        // Apply profile defaults first
        switch (profile) {
            case HIGH_THROUGHPUT:
                dbOptions.setMaxBackgroundJobs(8);
                dbOptions.setMaxSubcompactions(4);
                dbOptions.setBytesPerSync(1024 * 1024); // 1MB
                break;
            case BALANCED:
                dbOptions.setMaxBackgroundJobs(4);
                dbOptions.setMaxSubcompactions(2);
                dbOptions.setBytesPerSync(1024 * 1024);
                break;
            case LOW_MEMORY:
                dbOptions.setMaxBackgroundJobs(2);
                dbOptions.setMaxSubcompactions(1);
                dbOptions.setBytesPerSync(512 * 1024); // 512KB
                break;
            case DEFAULT:
                // No changes
                break;
        }

        // Apply explicit overrides
        if (maxBackgroundJobs != null) {
            dbOptions.setMaxBackgroundJobs(maxBackgroundJobs);
        }
        if (maxSubcompactions != null) {
            dbOptions.setMaxSubcompactions(maxSubcompactions);
        }
        if (bytesPerSync != null) {
            dbOptions.setBytesPerSync(bytesPerSync);
        }

        // Apply custom configurator last (highest priority)
        if (customDbOptions != null) {
            customDbOptions.accept(dbOptions);
        }
    }

    /**
     * Applies this configuration to a ColumnFamilyOptions instance.
     *
     * @param cfOptions the ColumnFamilyOptions to configure
     * @param blockCache shared block cache (may be null)
     * @return configured BlockBasedTableConfig if block cache settings were applied
     */
    public BlockBasedTableConfig applyToCfOptions(ColumnFamilyOptions cfOptions, Cache blockCache) {
        // Apply profile defaults first
        switch (profile) {
            case HIGH_THROUGHPUT:
                cfOptions.setWriteBufferSize(256 * 1024 * 1024); // 256MB
                cfOptions.setMaxWriteBufferNumber(4);
                cfOptions.setMinWriteBufferNumberToMerge(2);
                cfOptions.setLevelCompactionDynamicLevelBytes(true);
                break;
            case BALANCED:
                cfOptions.setWriteBufferSize(128 * 1024 * 1024); // 128MB
                cfOptions.setMaxWriteBufferNumber(3);
                cfOptions.setMinWriteBufferNumberToMerge(1);
                cfOptions.setLevelCompactionDynamicLevelBytes(true);
                break;
            case LOW_MEMORY:
                cfOptions.setWriteBufferSize(64 * 1024 * 1024); // 64MB
                cfOptions.setMaxWriteBufferNumber(2);
                cfOptions.setMinWriteBufferNumberToMerge(1);
                cfOptions.setLevelCompactionDynamicLevelBytes(false);
                break;
            case DEFAULT:
                // No changes
                break;
        }

        // Apply explicit overrides
        if (writeBufferSize != null) {
            cfOptions.setWriteBufferSize(writeBufferSize);
        }
        if (maxWriteBufferNumber != null) {
            cfOptions.setMaxWriteBufferNumber(maxWriteBufferNumber);
        }
        if (minWriteBufferNumberToMerge != null) {
            cfOptions.setMinWriteBufferNumberToMerge(minWriteBufferNumberToMerge);
        }
        if (levelCompactionDynamicLevelBytes != null) {
            cfOptions.setLevelCompactionDynamicLevelBytes(levelCompactionDynamicLevelBytes);
        }
        if (compressionType != null) {
            cfOptions.setCompressionType(compressionType);
        }

        // Configure block-based table options (cache and bloom filter)
        BlockBasedTableConfig tableConfig = null;
        if (blockCache != null || bloomFilterBits != null) {
            tableConfig = new BlockBasedTableConfig();

            if (blockCache != null) {
                tableConfig.setBlockCache(blockCache);
            }

            if (bloomFilterBits != null) {
                tableConfig.setFilterPolicy(new BloomFilter(bloomFilterBits));
            } else if (profile != Profile.DEFAULT) {
                // Apply default bloom filter for non-DEFAULT profiles
                tableConfig.setFilterPolicy(new BloomFilter(10));
            }

            cfOptions.setTableFormatConfig(tableConfig);
        }

        // Apply custom configurator last (highest priority)
        if (customCfOptions != null) {
            customCfOptions.accept(cfOptions);
        }

        return tableConfig;
    }

    /**
     * Creates a shared block cache based on this configuration.
     *
     * @return LRU cache instance, or null if no cache configured
     */
    public Cache createBlockCache() {
        long cacheSize = getBlockCacheSize();
        return cacheSize > 0 ? new LRUCache(cacheSize) : null;
    }

    /**
     * Gets the effective block cache size based on profile and overrides.
     *
     * @return cache size in bytes, or 0 if no cache configured
     */
    public long getBlockCacheSize() {
        if (blockCacheSize != null) {
            return blockCacheSize;
        }

        // Profile defaults
        switch (profile) {
            case HIGH_THROUGHPUT:
                return 512 * 1024 * 1024L; // 512MB
            case BALANCED:
                return 256 * 1024 * 1024L; // 256MB
            case LOW_MEMORY:
                return 128 * 1024 * 1024L; // 128MB
            case DEFAULT:
            default:
                return 0; // No cache
        }
    }

    public Profile profile() {
        return profile;
    }

    public static final class Builder {
        private Profile profile = Profile.BALANCED; // Default to balanced
        private Long writeBufferSize;
        private Integer maxWriteBufferNumber;
        private Integer minWriteBufferNumberToMerge;
        private Integer maxBackgroundJobs;
        private Integer maxSubcompactions;
        private Long blockCacheSize;
        private Integer bloomFilterBits;
        private Long bytesPerSync;
        private Boolean levelCompactionDynamicLevelBytes;
        private CompressionType compressionType;
        private Consumer<DBOptions> customDbOptions;
        private Consumer<ColumnFamilyOptions> customCfOptions;

        private Builder() {}

        /**
         * Sets the configuration profile. Subsequent explicit settings override profile defaults.
         *
         * @param profile preset profile
         * @return this builder
         */
        public Builder profile(Profile profile) {
            this.profile = profile;
            return this;
        }

        /**
         * Sets the write buffer size per column family (in bytes).
         * Larger values improve write performance but use more memory.
         *
         * @param writeBufferSize buffer size in bytes (e.g., 256 * 1024 * 1024 for 256MB)
         * @return this builder
         */
        public Builder writeBufferSize(long writeBufferSize) {
            this.writeBufferSize = writeBufferSize;
            return this;
        }

        /**
         * Sets the maximum number of write buffers. More buffers allow writes to continue
         * during flush/compaction but use more memory.
         *
         * @param maxWriteBufferNumber number of buffers (typical range: 2-6)
         * @return this builder
         */
        public Builder maxWriteBufferNumber(int maxWriteBufferNumber) {
            this.maxWriteBufferNumber = maxWriteBufferNumber;
            return this;
        }

        /**
         * Sets the minimum number of write buffers to merge before flush.
         *
         * @param minWriteBufferNumberToMerge min buffers (typical: 1-2)
         * @return this builder
         */
        public Builder minWriteBufferNumberToMerge(int minWriteBufferNumberToMerge) {
            this.minWriteBufferNumberToMerge = minWriteBufferNumberToMerge;
            return this;
        }

        /**
         * Sets the maximum number of background compaction and flush threads.
         *
         * @param maxBackgroundJobs thread count (typical: 2-16)
         * @return this builder
         */
        public Builder maxBackgroundJobs(int maxBackgroundJobs) {
            this.maxBackgroundJobs = maxBackgroundJobs;
            return this;
        }

        /**
         * Sets the maximum number of threads for a single compaction job.
         *
         * @param maxSubcompactions thread count (typical: 1-4)
         * @return this builder
         */
        public Builder maxSubcompactions(int maxSubcompactions) {
            this.maxSubcompactions = maxSubcompactions;
            return this;
        }

        /**
         * Sets the shared block cache size for reads (in bytes).
         * Larger cache improves read performance.
         *
         * @param blockCacheSize cache size in bytes (e.g., 512 * 1024 * 1024 for 512MB)
         * @return this builder
         */
        public Builder blockCacheSize(long blockCacheSize) {
            this.blockCacheSize = blockCacheSize;
            return this;
        }

        /**
         * Sets the number of bits per key for bloom filter.
         * Higher values reduce false positives but use more memory.
         *
         * @param bloomFilterBits bits per key (typical: 10-14)
         * @return this builder
         */
        public Builder bloomFilterBits(int bloomFilterBits) {
            this.bloomFilterBits = bloomFilterBits;
            return this;
        }

        /**
         * Sets the rate at which data is synced to disk (in bytes).
         * Helps smooth I/O spikes.
         *
         * @param bytesPerSync bytes (typical: 1MB)
         * @return this builder
         */
        public Builder bytesPerSync(long bytesPerSync) {
            this.bytesPerSync = bytesPerSync;
            return this;
        }

        /**
         * Enables dynamic level bytes for level compaction.
         * Generally recommended for write-heavy workloads.
         *
         * @param enabled true to enable
         * @return this builder
         */
        public Builder levelCompactionDynamicLevelBytes(boolean enabled) {
            this.levelCompactionDynamicLevelBytes = enabled;
            return this;
        }

        /**
         * Sets the compression algorithm for SST files.
         *
         * @param compressionType compression type (e.g., LZ4_COMPRESSION, ZSTD_COMPRESSION)
         * @return this builder
         */
        public Builder compressionType(CompressionType compressionType) {
            this.compressionType = compressionType;
            return this;
        }

        /**
         * Provides a custom configurator for DBOptions. Applied last, overriding all other settings.
         *
         * @param customDbOptions configurator function
         * @return this builder
         */
        public Builder customDbOptions(Consumer<DBOptions> customDbOptions) {
            this.customDbOptions = customDbOptions;
            return this;
        }

        /**
         * Provides a custom configurator for ColumnFamilyOptions. Applied last, overriding all other settings.
         *
         * @param customCfOptions configurator function
         * @return this builder
         */
        public Builder customCfOptions(Consumer<ColumnFamilyOptions> customCfOptions) {
            this.customCfOptions = customCfOptions;
            return this;
        }

        /**
         * Builds the RocksDbConfig instance.
         *
         * @return configured instance
         */
        public RocksDbConfig build() {
            return new RocksDbConfig(this);
        }
    }
}