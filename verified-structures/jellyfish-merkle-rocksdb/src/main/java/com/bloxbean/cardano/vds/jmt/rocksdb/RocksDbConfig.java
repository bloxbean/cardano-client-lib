package com.bloxbean.cardano.vds.jmt.rocksdb;

import org.rocksdb.*;

import java.util.function.Consumer;

/**
 * @deprecated Use {@link com.bloxbean.cardano.vds.rocksdb.RocksDbConfig} from the rocksdb-core module instead.
 *             This class delegates all behavior to the shared implementation.
 */
@Deprecated
public final class RocksDbConfig {

    private final com.bloxbean.cardano.vds.rocksdb.RocksDbConfig delegate;

    private RocksDbConfig(com.bloxbean.cardano.vds.rocksdb.RocksDbConfig delegate) {
        this.delegate = delegate;
    }

    /**
     * Preset configuration profiles optimized for different workloads.
     *
     * @deprecated Use {@link com.bloxbean.cardano.vds.rocksdb.RocksDbConfig.Profile} instead.
     */
    @Deprecated
    public enum Profile {
        HIGH_THROUGHPUT,
        BALANCED,
        LOW_MEMORY,
        DEFAULT;

        com.bloxbean.cardano.vds.rocksdb.RocksDbConfig.Profile toShared() {
            switch (this) {
                case HIGH_THROUGHPUT: return com.bloxbean.cardano.vds.rocksdb.RocksDbConfig.Profile.HIGH_THROUGHPUT;
                case BALANCED: return com.bloxbean.cardano.vds.rocksdb.RocksDbConfig.Profile.BALANCED;
                case LOW_MEMORY: return com.bloxbean.cardano.vds.rocksdb.RocksDbConfig.Profile.LOW_MEMORY;
                case DEFAULT: return com.bloxbean.cardano.vds.rocksdb.RocksDbConfig.Profile.DEFAULT;
                default: return com.bloxbean.cardano.vds.rocksdb.RocksDbConfig.Profile.DEFAULT;
            }
        }
    }

    /** @deprecated Use {@link com.bloxbean.cardano.vds.rocksdb.RocksDbConfig#highThroughput()} */
    @Deprecated
    public static RocksDbConfig highThroughput() {
        return new RocksDbConfig(com.bloxbean.cardano.vds.rocksdb.RocksDbConfig.highThroughput());
    }

    /** @deprecated Use {@link com.bloxbean.cardano.vds.rocksdb.RocksDbConfig#balanced()} */
    @Deprecated
    public static RocksDbConfig balanced() {
        return new RocksDbConfig(com.bloxbean.cardano.vds.rocksdb.RocksDbConfig.balanced());
    }

    /** @deprecated Use {@link com.bloxbean.cardano.vds.rocksdb.RocksDbConfig#lowMemory()} */
    @Deprecated
    public static RocksDbConfig lowMemory() {
        return new RocksDbConfig(com.bloxbean.cardano.vds.rocksdb.RocksDbConfig.lowMemory());
    }

    /** @deprecated Use {@link com.bloxbean.cardano.vds.rocksdb.RocksDbConfig#defaults()} */
    @Deprecated
    public static RocksDbConfig defaults() {
        return new RocksDbConfig(com.bloxbean.cardano.vds.rocksdb.RocksDbConfig.defaults());
    }

    /** @deprecated Use {@link com.bloxbean.cardano.vds.rocksdb.RocksDbConfig#builder()} */
    @Deprecated
    public static Builder builder() {
        return new Builder();
    }

    public void applyToDbOptions(DBOptions dbOptions) {
        delegate.applyToDbOptions(dbOptions);
    }

    public BlockBasedTableConfig applyToCfOptions(ColumnFamilyOptions cfOptions, Cache blockCache) {
        return delegate.applyToCfOptions(cfOptions, blockCache);
    }

    public Cache createBlockCache() {
        return delegate.createBlockCache();
    }

    public long getBlockCacheSize() {
        return delegate.getBlockCacheSize();
    }

    public Profile profile() {
        com.bloxbean.cardano.vds.rocksdb.RocksDbConfig.Profile p = delegate.profile();
        switch (p) {
            case HIGH_THROUGHPUT: return Profile.HIGH_THROUGHPUT;
            case BALANCED: return Profile.BALANCED;
            case LOW_MEMORY: return Profile.LOW_MEMORY;
            case DEFAULT: return Profile.DEFAULT;
            default: return Profile.DEFAULT;
        }
    }

    /**
     * Returns the underlying shared config instance.
     *
     * @return the shared RocksDbConfig delegate
     */
    public com.bloxbean.cardano.vds.rocksdb.RocksDbConfig toSharedConfig() {
        return delegate;
    }

    /**
     * @deprecated Use {@link com.bloxbean.cardano.vds.rocksdb.RocksDbConfig.Builder} instead.
     */
    @Deprecated
    public static final class Builder {
        private final com.bloxbean.cardano.vds.rocksdb.RocksDbConfig.Builder delegate =
                com.bloxbean.cardano.vds.rocksdb.RocksDbConfig.builder();

        private Builder() {}

        public Builder profile(Profile profile) {
            delegate.profile(profile.toShared());
            return this;
        }

        public Builder writeBufferSize(long writeBufferSize) {
            delegate.writeBufferSize(writeBufferSize);
            return this;
        }

        public Builder maxWriteBufferNumber(int maxWriteBufferNumber) {
            delegate.maxWriteBufferNumber(maxWriteBufferNumber);
            return this;
        }

        public Builder minWriteBufferNumberToMerge(int minWriteBufferNumberToMerge) {
            delegate.minWriteBufferNumberToMerge(minWriteBufferNumberToMerge);
            return this;
        }

        public Builder maxBackgroundJobs(int maxBackgroundJobs) {
            delegate.maxBackgroundJobs(maxBackgroundJobs);
            return this;
        }

        public Builder maxSubcompactions(int maxSubcompactions) {
            delegate.maxSubcompactions(maxSubcompactions);
            return this;
        }

        public Builder blockCacheSize(long blockCacheSize) {
            delegate.blockCacheSize(blockCacheSize);
            return this;
        }

        public Builder bloomFilterBits(int bloomFilterBits) {
            delegate.bloomFilterBits(bloomFilterBits);
            return this;
        }

        public Builder bytesPerSync(long bytesPerSync) {
            delegate.bytesPerSync(bytesPerSync);
            return this;
        }

        public Builder levelCompactionDynamicLevelBytes(boolean enabled) {
            delegate.levelCompactionDynamicLevelBytes(enabled);
            return this;
        }

        public Builder compressionType(CompressionType compressionType) {
            delegate.compressionType(compressionType);
            return this;
        }

        public Builder customDbOptions(Consumer<DBOptions> customDbOptions) {
            delegate.customDbOptions(customDbOptions);
            return this;
        }

        public Builder customCfOptions(Consumer<ColumnFamilyOptions> customCfOptions) {
            delegate.customCfOptions(customCfOptions);
            return this;
        }

        public RocksDbConfig build() {
            return new RocksDbConfig(delegate.build());
        }
    }
}
