package com.bloxbean.cardano.vds.mpt.rocksdb;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.mpt.MerklePatriciaTrie;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.mpt.SecureTrie;
import com.bloxbean.cardano.vds.mpt.rocksdb.gc.*;
import com.bloxbean.cardano.vds.mpt.rocksdb.gc.strategy.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for OnDiskMarkSweepStrategy.
 *
 * <p>Tests the mark-and-sweep GC strategy to ensure it correctly:
 * <ul>
 *   <li>Marks all reachable nodes from retained roots</li>
 *   <li>Sweeps (deletes) unreachable nodes</li>
 *   <li>Handles large datasets with low memory usage</li>
 *   <li>Works correctly with retention policies</li>
 * </ul>
 */
public class OnDiskMarkSweepGcTest {

    @TempDir
    Path tempDir;

    private RocksDbStateTrees stateTrees;
    private SecureTrie trie;
    private Random random;
    private HashFunction hashFn;

    @BeforeEach
    public void setUp() throws Exception {
        stateTrees = new RocksDbStateTrees(tempDir.resolve("test-db").toString());
        hashFn = Blake2b256::digest;
        trie = new SecureTrie(stateTrees.nodeStore(), hashFn);
        random = new Random(42); // Deterministic for testing
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (stateTrees != null) {
            stateTrees.close();
        }
    }

    @Test
    public void testBasicMarkAndSweep() throws Exception {
        // Create version 1 with 100 keys
        for (int i = 0; i < 100; i++) {
            trie.put(randomKey(), randomValue());
        }
        byte[] root1 = trie.getRootHash();
        stateTrees.rootsIndex().put(1L, root1);

        // Create version 2 with 50 new keys (50 modified from v1)
        for (int i = 0; i < 50; i++) {
            trie.put(randomKey(), randomValue());
        }
        byte[] root2 = trie.getRootHash();
        stateTrees.rootsIndex().put(2L, root2);

        // Count nodes before GC
        long nodesBefore = countNodes();
        assertTrue(nodesBefore > 0, "Should have nodes before GC");

        // Run GC keeping only version 2 (should delete version 1's unique nodes)
        GcManager gcManager = new GcManager(stateTrees.nodeStore(), stateTrees.rootsIndex());
        RetentionPolicy policy = RetentionPolicy.keepLatestN(1);
        GcOptions options = new GcOptions();

        GcReport report = gcManager.runSync(new OnDiskMarkSweepStrategy(), policy, options);

        // Verify GC results
        assertNotNull(report);
        assertTrue(report.marked > 0, "Should have marked some nodes");
        assertTrue(report.deleted > 0, "Should have deleted some nodes");
        assertTrue(report.deleted < nodesBefore, "Should not delete all nodes");
        assertEquals(nodesBefore, report.total, "Total should match nodes scanned");

        // Verify version 2 still accessible
        SecureTrie trie2 = new SecureTrie(stateTrees.nodeStore(), hashFn, root2);
        assertNotNull(trie2.getRootHash(), "Version 2 should still be accessible");
    }

    @Test
    public void testMarkSweepWithMultipleVersions() throws Exception {
        // Create 5 versions, each with some overlap
        byte[][] roots = new byte[5][];
        for (int v = 0; v < 5; v++) {
            // Each version modifies 30% of keys
            for (int i = 0; i < 100; i++) {
                if (random.nextDouble() < 0.3 || v == 0) {
                    trie.put(("key-" + i).getBytes(), randomValue());
                }
            }
            roots[v] = trie.getRootHash();
            stateTrees.rootsIndex().put((long) v, roots[v]);
        }

        long nodesBefore = countNodes();

        // Keep last 2 versions (should delete versions 0, 1, 2's unique nodes)
        GcManager gcManager = new GcManager(stateTrees.nodeStore(), stateTrees.rootsIndex());
        GcReport report = gcManager.runSync(
            new OnDiskMarkSweepStrategy(),
            RetentionPolicy.keepLatestN(2),
            new GcOptions()
        );

        // Verify deletions occurred
        assertTrue(report.deleted > 0, "Should delete old version nodes");
        assertTrue(report.marked > 0, "Should mark retained nodes");

        // Verify versions 3 and 4 are still accessible
        SecureTrie trie3 = new SecureTrie(stateTrees.nodeStore(), hashFn, roots[3]);
        SecureTrie trie4 = new SecureTrie(stateTrees.nodeStore(), hashFn, roots[4]);
        assertNotNull(trie3.getRootHash());
        assertNotNull(trie4.getRootHash());
    }

    @Test
    public void testDryRun() throws Exception {
        // Create a version
        for (int i = 0; i < 50; i++) {
            trie.put(randomKey(), randomValue());
        }
        byte[] root1 = trie.getRootHash();
        stateTrees.rootsIndex().put(1L, root1);

        // Create another version
        for (int i = 0; i < 50; i++) {
            trie.put(randomKey(), randomValue());
        }
        stateTrees.rootsIndex().put(2L, trie.getRootHash());

        long nodesBefore = countNodes();

        // Run GC in dry-run mode
        GcOptions options = new GcOptions();
        options.dryRun = true;

        GcManager gcManager = new GcManager(stateTrees.nodeStore(), stateTrees.rootsIndex());
        GcReport report = gcManager.runSync(
            new OnDiskMarkSweepStrategy(),
            RetentionPolicy.keepLatestN(1),
            options
        );

        // Verify report shows what would be deleted
        assertTrue(report.deleted > 0, "Dry run should report nodes that would be deleted");

        // Verify no actual deletions occurred
        long nodesAfter = countNodes();
        assertEquals(nodesBefore, nodesAfter, "Dry run should not delete any nodes");
    }

    @Test
    public void testEmptyTrie() throws Exception {
        // Don't add any data, just run GC
        GcManager gcManager = new GcManager(stateTrees.nodeStore(), stateTrees.rootsIndex());
        GcReport report = gcManager.runSync(
            new OnDiskMarkSweepStrategy(),
            RetentionPolicy.keepLatestN(1),
            new GcOptions()
        );

        // Should handle empty trie gracefully
        assertNotNull(report);
        assertEquals(0, report.marked, "Empty trie should have 0 marked nodes");
        assertEquals(0, report.deleted, "Empty trie should have 0 deleted nodes");
    }

    @Test
    public void testLargeDataset() throws Exception {
        // Simulate high-throughput off-chain scenario
        // Create version with 10,000 keys
        for (int i = 0; i < 10_000; i++) {
            trie.put(randomKey(), randomValue());
            if (i % 1000 == 0) {
                System.out.println("Inserted " + i + " keys");
            }
        }
        byte[] root1 = trie.getRootHash();
        stateTrees.rootsIndex().put(1L, root1);

        // Create version 2 with 5,000 updates
        for (int i = 0; i < 5_000; i++) {
            trie.put(randomKey(), randomValue());
        }
        stateTrees.rootsIndex().put(2L, trie.getRootHash());

        long nodesBefore = countNodes();
        assertTrue(nodesBefore > 10_000, "Should have many nodes for large dataset");

        // Run GC with progress reporting
        GcOptions options = new GcOptions();
        options.deleteBatchSize = 1000;
        options.progress = deleted -> {
            if (deleted % 1000 == 0) {
                System.out.println("Deleted " + deleted + " nodes so far");
            }
        };

        GcManager gcManager = new GcManager(stateTrees.nodeStore(), stateTrees.rootsIndex());
        long startTime = System.currentTimeMillis();

        GcReport report = gcManager.runSync(
            new OnDiskMarkSweepStrategy(),
            RetentionPolicy.keepLatestN(1),
            options
        );

        long duration = System.currentTimeMillis() - startTime;

        // Verify GC completed successfully
        assertNotNull(report);
        assertTrue(report.deleted > 0, "Should delete nodes from old version");
        assertTrue(report.marked > 0, "Should mark nodes from retained version");
        System.out.println("GC completed in " + duration + "ms");
        System.out.println("Marked: " + report.marked + ", Deleted: " + report.deleted + ", Total: " + report.total);

        // Verify GC metrics are reasonable
        System.out.println("GC efficiency: " + (report.deleted * 100.0 / report.total) + "% deleted");

        // Verify no excessive memory usage (mark set on disk, not in RAM)
        // Note: This is a weak assertion since JVM memory can vary due to GC timing
        Runtime runtime = Runtime.getRuntime();
        System.gc(); // Suggest GC to get more accurate measurement
        Thread.sleep(100); // Give GC a moment
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long usedMB = usedMemory / (1024 * 1024);
        System.out.println("Memory used after GC: " + usedMB + " MB");
        // OnDiskMarkSweep should use minimal heap (mark set on disk)
        // This assertion can be flaky, so we use a generous limit
        assertTrue(usedMB < 500, "Should use < 500MB heap (mark set on disk)");
    }

    @Test
    public void testRetentionPolicyKeepAll() throws Exception {
        // Create 3 versions
        for (int v = 0; v < 3; v++) {
            for (int i = 0; i < 50; i++) {
                trie.put(("key-v" + v + "-" + i).getBytes(), randomValue());
            }
            stateTrees.rootsIndex().put((long) v, trie.getRootHash());
        }

        long nodesBefore = countNodes();

        // Run GC with policy that keeps all versions (use keepLatestN with large number)
        GcManager gcManager = new GcManager(stateTrees.nodeStore(), stateTrees.rootsIndex());
        GcReport report = gcManager.runSync(
            new OnDiskMarkSweepStrategy(),
            RetentionPolicy.keepLatestN(Integer.MAX_VALUE),
            new GcOptions()
        );

        // Should mark all reachable nodes
        // Note: Even when keeping all versions, some nodes may be deleted if they became
        // unreachable due to updates (old intermediate nodes replaced by new ones)
        assertTrue(report.marked > 0, "Should mark all reachable nodes");

        // Verify all versions are still accessible
        for (int v = 0; v < 3; v++) {
            byte[] root = stateTrees.rootsIndex().get((long) v);
            assertNotNull(root, "Root for version " + v + " should exist");
            SecureTrie trieV = new SecureTrie(stateTrees.nodeStore(), hashFn, root);
            assertNotNull(trieV.getRootHash(), "Version " + v + " should be accessible");
        }
    }

    @Test
    public void testSnapshotConsistency() throws Exception {
        // Create initial version
        for (int i = 0; i < 100; i++) {
            trie.put(randomKey(), randomValue());
        }
        stateTrees.rootsIndex().put(1L, trie.getRootHash());

        // Create version to delete
        for (int i = 0; i < 50; i++) {
            trie.put(randomKey(), randomValue());
        }
        stateTrees.rootsIndex().put(2L, trie.getRootHash());

        // Run GC with snapshot for consistency
        GcOptions options = new GcOptions();
        options.useSnapshot = true;

        GcManager gcManager = new GcManager(stateTrees.nodeStore(), stateTrees.rootsIndex());
        GcReport report = gcManager.runSync(
            new OnDiskMarkSweepStrategy(),
            RetentionPolicy.keepLatestN(1),
            options
        );

        // Verify GC completed successfully with snapshot
        assertNotNull(report);
        assertTrue(report.deleted > 0, "Should delete nodes even with snapshot");
    }

    // Helper methods

    private byte[] randomKey() {
        byte[] key = new byte[16];
        random.nextBytes(key);
        return key;
    }

    private byte[] randomValue() {
        byte[] value = new byte[64];
        random.nextBytes(value);
        return value;
    }

    private long countNodes() throws Exception {
        long count = 0;
        org.rocksdb.RocksIterator it = stateTrees.nodeStore().db().newIterator(stateTrees.nodeStore().nodesHandle());
        try {
            for (it.seekToFirst(); it.isValid(); it.next()) {
                count++;
            }
        } finally {
            it.close();
        }
        return count;
    }
}