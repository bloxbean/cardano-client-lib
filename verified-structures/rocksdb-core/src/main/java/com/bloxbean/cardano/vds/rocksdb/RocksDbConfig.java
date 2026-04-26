package com.bloxbean.cardano.vds.rocksdb;

import org.rocksdb.*;

import java.util.function.Consumer;

/**
 * Configurable RocksDB settings for verified data structure storage backends.
 *
 * <p>This class provides preset profiles optimized for different workloads while allowing
 * full customization for advanced use cases. The configuration affects both DBOptions and
 * ColumnFamilyOptions used by RocksDB-backed stores.
 *
 * <h2>Usage Examples:</h2>
 * <pre>
 * // Use preset profile for high-throughput write workload
 * RocksDbConfig config = RocksDbConfig.highThroughput();
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
 * @since 0.8.0
 */
public final class RocksDbConfig {

    /**
     * Preset configuration profiles optimized for different workloads.
     */
    public enum Profile {
        /**
         * High-throughput write-heavy workload (production blockchain nodes).
         *
         * <p><b>Configuration highlights:</b>
         * <ul>
         *   <li>Write buffers: 256MB x 6 memtables = 1.5GB total buffering</li>
         *   <li>SST files: 256MB L1 files, 1GB L1 total (reduces compaction frequency)</li>
         *   <li>Compaction: 8 background jobs, 4 subcompactions (parallel processing)</li>
         *   <li>L0 triggers: Start at 4 files, slowdown at 20, stop at 36</li>
         *   <li>Write optimization: Concurrent memtable writes, pipelined WAL</li>
         *   <li>Block cache: 512MB shared cache</li>
         * </ul>
         *
         * <p><b>Use when:</b> High insert/update rate, plenty of RAM available (4GB+ recommended)
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
     *
     * @return high-throughput configuration
     */
    public static RocksDbConfig highThroughput() {
        return builder().profile(Profile.HIGH_THROUGHPUT).build();
    }

    /**
     * Creates a BALANCED profile configuration.
     *
     * @return balanced configuration
     */
    public static RocksDbConfig balanced() {
        return builder().profile(Profile.BALANCED).build();
    }

    /**
     * Creates a LOW_MEMORY profile configuration.
     *
     * @return low-memory configuration
     */
    public static RocksDbConfig lowMemory() {
        return builder().profile(Profile.LOW_MEMORY).build();
    }

    /**
     * Creates a configuration using RocksDB defaults.
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
        switch (profile) {
            case HIGH_THROUGHPUT:
                dbOptions.setMaxBackgroundJobs(8);
                dbOptions.setMaxSubcompactions(4);
                dbOptions.setBytesPerSync(1024 * 1024);
                dbOptions.setAllowConcurrentMemtableWrite(true);
                dbOptions.setEnablePipelinedWrite(true);
                break;
            case BALANCED:
                dbOptions.setMaxBackgroundJobs(4);
                dbOptions.setMaxSubcompactions(2);
                dbOptions.setBytesPerSync(1024 * 1024);
                break;
            case LOW_MEMORY:
                dbOptions.setMaxBackgroundJobs(2);
                dbOptions.setMaxSubcompactions(1);
                dbOptions.setBytesPerSync(512 * 1024);
                break;
            case DEFAULT:
                break;
        }

        if (maxBackgroundJobs != null) {
            dbOptions.setMaxBackgroundJobs(maxBackgroundJobs);
        }
        if (maxSubcompactions != null) {
            dbOptions.setMaxSubcompactions(maxSubcompactions);
        }
        if (bytesPerSync != null) {
            dbOptions.setBytesPerSync(bytesPerSync);
        }

        if (customDbOptions != null) {
            customDbOptions.accept(dbOptions);
        }
    }

    /**
     * Applies this configuration to a ColumnFamilyOptions instance.
     *
     * @param cfOptions  the ColumnFamilyOptions to configure
     * @param blockCache shared block cache (may be null)
     * @return configured BlockBasedTableConfig if block cache settings were applied
     */
    public BlockBasedTableConfig applyToCfOptions(ColumnFamilyOptions cfOptions, Cache blockCache) {
        switch (profile) {
            case HIGH_THROUGHPUT:
                cfOptions.setWriteBufferSize(256 * 1024 * 1024);
                cfOptions.setMaxWriteBufferNumber(6);
                cfOptions.setMinWriteBufferNumberToMerge(2);
                cfOptions.setTargetFileSizeBase(256 * 1024 * 1024);
                cfOptions.setMaxBytesForLevelBase(1024 * 1024 * 1024);
                cfOptions.setTargetFileSizeMultiplier(2);
                cfOptions.setLevelCompactionDynamicLevelBytes(true);
                cfOptions.setLevel0FileNumCompactionTrigger(4);
                cfOptions.setLevel0SlowdownWritesTrigger(20);
                cfOptions.setLevel0StopWritesTrigger(36);
                cfOptions.setCompressionType(CompressionType.LZ4_COMPRESSION);
                break;
            case BALANCED:
                cfOptions.setWriteBufferSize(128 * 1024 * 1024);
                cfOptions.setMaxWriteBufferNumber(3);
                cfOptions.setMinWriteBufferNumberToMerge(1);
                cfOptions.setLevelCompactionDynamicLevelBytes(true);
                break;
            case LOW_MEMORY:
                cfOptions.setWriteBufferSize(64 * 1024 * 1024);
                cfOptions.setMaxWriteBufferNumber(2);
                cfOptions.setMinWriteBufferNumberToMerge(1);
                cfOptions.setLevelCompactionDynamicLevelBytes(false);
                break;
            case DEFAULT:
                break;
        }

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

        BlockBasedTableConfig tableConfig = null;
        if (blockCache != null || bloomFilterBits != null) {
            tableConfig = new BlockBasedTableConfig();

            if (blockCache != null) {
                tableConfig.setBlockCache(blockCache);
            }

            if (bloomFilterBits != null) {
                tableConfig.setFilterPolicy(new BloomFilter(bloomFilterBits));
            } else if (profile != Profile.DEFAULT) {
                tableConfig.setFilterPolicy(new BloomFilter(10));
            }

            cfOptions.setTableFormatConfig(tableConfig);
        }

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

        switch (profile) {
            case HIGH_THROUGHPUT:
                return 512 * 1024 * 1024L;
            case BALANCED:
                return 256 * 1024 * 1024L;
            case LOW_MEMORY:
                return 128 * 1024 * 1024L;
            case DEFAULT:
            default:
                return 0;
        }
    }

    public Profile profile() {
        return profile;
    }

    public static final class Builder {
        private Profile profile = Profile.BALANCED;
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

        public Builder profile(Profile profile) {
            this.profile = profile;
            return this;
        }

        public Builder writeBufferSize(long writeBufferSize) {
            this.writeBufferSize = writeBufferSize;
            return this;
        }

        public Builder maxWriteBufferNumber(int maxWriteBufferNumber) {
            this.maxWriteBufferNumber = maxWriteBufferNumber;
            return this;
        }

        public Builder minWriteBufferNumberToMerge(int minWriteBufferNumberToMerge) {
            this.minWriteBufferNumberToMerge = minWriteBufferNumberToMerge;
            return this;
        }

        public Builder maxBackgroundJobs(int maxBackgroundJobs) {
            this.maxBackgroundJobs = maxBackgroundJobs;
            return this;
        }

        public Builder maxSubcompactions(int maxSubcompactions) {
            this.maxSubcompactions = maxSubcompactions;
            return this;
        }

        public Builder blockCacheSize(long blockCacheSize) {
            this.blockCacheSize = blockCacheSize;
            return this;
        }

        public Builder bloomFilterBits(int bloomFilterBits) {
            this.bloomFilterBits = bloomFilterBits;
            return this;
        }

        public Builder bytesPerSync(long bytesPerSync) {
            this.bytesPerSync = bytesPerSync;
            return this;
        }

        public Builder levelCompactionDynamicLevelBytes(boolean enabled) {
            this.levelCompactionDynamicLevelBytes = enabled;
            return this;
        }

        public Builder compressionType(CompressionType compressionType) {
            this.compressionType = compressionType;
            return this;
        }

        public Builder customDbOptions(Consumer<DBOptions> customDbOptions) {
            this.customDbOptions = customDbOptions;
            return this;
        }

        public Builder customCfOptions(Consumer<ColumnFamilyOptions> customCfOptions) {
            this.customCfOptions = customCfOptions;
            return this;
        }

        public RocksDbConfig build() {
            return new RocksDbConfig(this);
        }
    }
}
