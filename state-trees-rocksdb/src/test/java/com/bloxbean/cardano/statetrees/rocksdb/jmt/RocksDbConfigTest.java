package com.bloxbean.cardano.statetrees.rocksdb.jmt;

import org.junit.jupiter.api.Test;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RocksDbConfig to verify configuration profiles apply correctly.
 */
class RocksDbConfigTest {

    @Test
    void highThroughput_shouldApplyOptimizedSettings() {
        RocksDbConfig config = RocksDbConfig.highThroughput();
        assertEquals(RocksDbConfig.Profile.HIGH_THROUGHPUT, config.profile());

        // Verify DBOptions settings
        try (DBOptions dbOpts = new DBOptions()) {
            config.applyToDbOptions(dbOpts);

            // ADR-0015 Phase 2 optimizations
            assertEquals(8, dbOpts.maxBackgroundJobs(), "Should have 8 background jobs for parallel compaction");
            assertEquals(4, dbOpts.maxSubcompactions(), "Should have 4 subcompactions for parallel processing");
            assertEquals(1024 * 1024, dbOpts.bytesPerSync(), "Should sync every 1MB");
            assertTrue(dbOpts.allowConcurrentMemtableWrite(), "Should allow concurrent memtable writes");
            assertTrue(dbOpts.enablePipelinedWrite(), "Should enable pipelined WAL writes");
        }

        // Verify ColumnFamilyOptions settings
        try (ColumnFamilyOptions cfOpts = new ColumnFamilyOptions()) {
            config.applyToCfOptions(cfOpts, null);

            // Write buffer tuning
            assertEquals(256 * 1024 * 1024, cfOpts.writeBufferSize(), "Should have 256MB write buffer");
            assertEquals(6, cfOpts.maxWriteBufferNumber(), "Should have 6 memtables for sustained writes");
            assertEquals(2, cfOpts.minWriteBufferNumberToMerge(), "Should merge 2 buffers before flush");

            // SST file sizing (reduces compaction frequency)
            assertEquals(256 * 1024 * 1024, cfOpts.targetFileSizeBase(), "Should have 256MB L1 files");
            assertEquals(1024 * 1024 * 1024, cfOpts.maxBytesForLevelBase(), "Should have 1GB L1 total");
            assertEquals(2, cfOpts.targetFileSizeMultiplier(), "Should double file size each level");

            // Level compaction optimizations
            assertTrue(cfOpts.levelCompactionDynamicLevelBytes(), "Should use dynamic level bytes");
            assertEquals(4, cfOpts.level0FileNumCompactionTrigger(), "Should trigger compaction at 4 L0 files");
            assertEquals(20, cfOpts.level0SlowdownWritesTrigger(), "Should slowdown at 20 L0 files");
            assertEquals(36, cfOpts.level0StopWritesTrigger(), "Should stop writes at 36 L0 files");
        }
    }

    @Test
    void balanced_shouldApplyModerateSettings() {
        RocksDbConfig config = RocksDbConfig.balanced();
        assertEquals(RocksDbConfig.Profile.BALANCED, config.profile());

        try (ColumnFamilyOptions cfOpts = new ColumnFamilyOptions()) {
            config.applyToCfOptions(cfOpts, null);

            assertEquals(128 * 1024 * 1024, cfOpts.writeBufferSize(), "Should have 128MB write buffer");
            assertEquals(3, cfOpts.maxWriteBufferNumber(), "Should have 3 memtables");
            assertEquals(1, cfOpts.minWriteBufferNumberToMerge());
        }
    }

    @Test
    void lowMemory_shouldApplyMinimalSettings() {
        RocksDbConfig config = RocksDbConfig.lowMemory();
        assertEquals(RocksDbConfig.Profile.LOW_MEMORY, config.profile());

        try (ColumnFamilyOptions cfOpts = new ColumnFamilyOptions()) {
            config.applyToCfOptions(cfOpts, null);

            assertEquals(64 * 1024 * 1024, cfOpts.writeBufferSize(), "Should have 64MB write buffer");
            assertEquals(2, cfOpts.maxWriteBufferNumber(), "Should have 2 memtables");
            assertFalse(cfOpts.levelCompactionDynamicLevelBytes(), "Should not use dynamic level bytes");
        }
    }

    @Test
    void builder_shouldAllowOverrides() {
        RocksDbConfig config = RocksDbConfig.builder()
                .profile(RocksDbConfig.Profile.HIGH_THROUGHPUT)
                .writeBufferSize(512 * 1024 * 1024) // Override to 512MB
                .maxBackgroundJobs(16) // Override to 16 jobs
                .build();

        try (ColumnFamilyOptions cfOpts = new ColumnFamilyOptions(); DBOptions dbOpts = new DBOptions()) {
            config.applyToCfOptions(cfOpts, null);
            config.applyToDbOptions(dbOpts);

            assertEquals(512 * 1024 * 1024, cfOpts.writeBufferSize(), "Should use overridden value");
            assertEquals(16, dbOpts.maxBackgroundJobs(), "Should use overridden value");
        }
    }

    @Test
    void blockCache_shouldBeConfiguredCorrectly() {
        RocksDbConfig config = RocksDbConfig.highThroughput();

        // HIGH_THROUGHPUT should have 512MB cache
        assertEquals(512 * 1024 * 1024L, config.getBlockCacheSize());

        // Can be overridden
        RocksDbConfig customConfig = RocksDbConfig.builder()
                .profile(RocksDbConfig.Profile.HIGH_THROUGHPUT)
                .blockCacheSize(1024 * 1024 * 1024L) // 1GB
                .build();

        assertEquals(1024 * 1024 * 1024L, customConfig.getBlockCacheSize());
    }
}
