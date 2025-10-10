package com.bloxbean.cardano.statetrees.rocksdb.mpt;

import com.bloxbean.cardano.statetrees.api.MerklePatriciaTrie;
import com.bloxbean.cardano.statetrees.rocksdb.mpt.gc.GcOptions;
import com.bloxbean.cardano.statetrees.rocksdb.mpt.gc.GcReport;
import com.bloxbean.cardano.statetrees.rocksdb.namespace.NamespaceOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for single-version snapshot mode in RocksDbStateTrees.
 */
class RocksDbStateTreesSnapshotModeTest {

    @TempDir
    Path tempDir;

    private RocksDbStateTrees stateTrees;
    private MerklePatriciaTrie trie;

    @BeforeEach
    void setUp() {
        String dbPath = tempDir.resolve("test-snapshot-db").toString();
        stateTrees = new RocksDbStateTrees(dbPath, NamespaceOptions.defaults(), StorageMode.SINGLE_VERSION);
        trie = new MerklePatriciaTrie(stateTrees.nodeStore(), this::hash);
    }

    @AfterEach
    void tearDown() {
        if (stateTrees != null) {
            stateTrees.close();
        }
    }

    private byte[] hash(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testStorageModeIsCorrect() {
        assertEquals(StorageMode.SINGLE_VERSION, stateTrees.storageMode());
    }

    @Test
    void testPutRootSnapshot() {
        // Insert some data
        trie.put("key1".getBytes(), "value1".getBytes());
        byte[] root1 = trie.getRootHash();

        // Store snapshot
        stateTrees.putRootSnapshot(root1);

        // Verify current root
        assertArrayEquals(root1, stateTrees.getCurrentRoot());
    }

    @Test
    void testPutRootSnapshotOverwritesPrevious() {
        // First snapshot
        trie.put("key1".getBytes(), "value1".getBytes());
        byte[] root1 = trie.getRootHash();
        stateTrees.putRootSnapshot(root1);

        // Second snapshot (should overwrite)
        trie.put("key2".getBytes(), "value2".getBytes());
        byte[] root2 = trie.getRootHash();
        stateTrees.putRootSnapshot(root2);

        // Current root should be root2, not root1
        assertArrayEquals(root2, stateTrees.getCurrentRoot());
        assertFalse(Arrays.equals(root1, stateTrees.getCurrentRoot()));

        // Verify only version 0 exists
        assertArrayEquals(root2, stateTrees.rootsIndex().get(0L));
    }

    @Test
    void testGetCurrentRootWhenEmpty() {
        // No root stored yet
        assertNull(stateTrees.getCurrentRoot());
    }

    @Test
    void testCleanupOrphanedNodes() throws Exception {
        // Create initial state
        trie.put("key1".getBytes(), "value1".getBytes());
        trie.put("key2".getBytes(), "value2".getBytes());
        trie.put("key3".getBytes(), "value3".getBytes());
        byte[] root1 = trie.getRootHash();
        stateTrees.putRootSnapshot(root1);

        // Modify state (creates orphans from root1)
        trie.put("key4".getBytes(), "value4".getBytes());
        trie.put("key5".getBytes(), "value5".getBytes());
        byte[] root2 = trie.getRootHash();
        stateTrees.putRootSnapshot(root2);

        // Run cleanup
        GcOptions options = new GcOptions();
        GcReport report = stateTrees.cleanupOrphanedNodes(options);

        // Should have deleted some nodes
        assertTrue(report.deleted > 0, "Expected orphaned nodes to be deleted");

        // Current root should still be accessible
        assertArrayEquals(root2, stateTrees.getCurrentRoot());

        // Verify trie still works with current root
        MerklePatriciaTrie verifyTrie = new MerklePatriciaTrie(stateTrees.nodeStore(), this::hash, root2);
        assertArrayEquals("value4".getBytes(), verifyTrie.get("key4".getBytes()));
        assertArrayEquals("value5".getBytes(), verifyTrie.get("key5".getBytes()));
    }

    @Test
    void testPutRootWithRefcountThrowsInSnapshotMode() {
        trie.put("key1".getBytes(), "value1".getBytes());
        byte[] root = trie.getRootHash();

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> stateTrees.putRootWithRefcount(root)
        );
        assertTrue(exception.getMessage().contains("MULTI_VERSION mode"));
        assertTrue(exception.getMessage().contains("putRootSnapshot()"));
    }

    @Test
    void testPutRootSnapshotNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> stateTrees.putRootSnapshot(null));
    }

    @Test
    void testPutRootSnapshotEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> stateTrees.putRootSnapshot(new byte[0]));
    }

    @Test
    void testMultipleSnapshotUpdates() {
        // Simulate multiple snapshot updates
        for (int i = 0; i < 10; i++) {
            trie.put(("key" + i).getBytes(), ("value" + i).getBytes());
            byte[] root = trie.getRootHash();
            stateTrees.putRootSnapshot(root);

            // Verify current root matches what we just stored
            assertArrayEquals(root, stateTrees.getCurrentRoot());
        }

        // Verify final state
        byte[] currentRoot = stateTrees.getCurrentRoot();
        MerklePatriciaTrie verifyTrie = new MerklePatriciaTrie(stateTrees.nodeStore(), this::hash, currentRoot);

        for (int i = 0; i < 10; i++) {
            assertArrayEquals(("value" + i).getBytes(), verifyTrie.get(("key" + i).getBytes()));
        }
    }

    @Test
    void testCleanupAfterMultipleUpdates() throws Exception {
        // Create many snapshots to generate orphans
        for (int i = 0; i < 5; i++) {
            trie.put(("key" + i).getBytes(), ("value" + i).getBytes());
            stateTrees.putRootSnapshot(trie.getRootHash());
        }

        // Cleanup orphaned nodes
        GcOptions options = new GcOptions();
        GcReport report = stateTrees.cleanupOrphanedNodes(options);

        // Should have cleaned up some orphans
        assertTrue(report.deleted > 0);

        // Verify current state still accessible
        byte[] currentRoot = stateTrees.getCurrentRoot();
        MerklePatriciaTrie verifyTrie = new MerklePatriciaTrie(stateTrees.nodeStore(), this::hash, currentRoot);

        for (int i = 0; i < 5; i++) {
            assertArrayEquals(("value" + i).getBytes(), verifyTrie.get(("key" + i).getBytes()));
        }
    }

    @Test
    void testReopenDatabaseInSnapshotMode() {
        // Store some data
        trie.put("key1".getBytes(), "value1".getBytes());
        byte[] root = trie.getRootHash();
        stateTrees.putRootSnapshot(root);

        String dbPath = tempDir.resolve("test-snapshot-db").toString();
        stateTrees.close();

        // Reopen in snapshot mode
        stateTrees = new RocksDbStateTrees(dbPath, NamespaceOptions.defaults(), StorageMode.SINGLE_VERSION);

        // Verify root is still there
        assertArrayEquals(root, stateTrees.getCurrentRoot());

        // Verify data is accessible
        MerklePatriciaTrie verifyTrie = new MerklePatriciaTrie(stateTrees.nodeStore(), this::hash, root);
        assertArrayEquals("value1".getBytes(), verifyTrie.get("key1".getBytes()));
    }
}
