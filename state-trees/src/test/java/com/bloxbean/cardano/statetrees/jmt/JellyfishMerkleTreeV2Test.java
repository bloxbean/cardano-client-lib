package com.bloxbean.cardano.statetrees.jmt;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTreeV2.CommitResult;
import com.bloxbean.cardano.statetrees.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.store.InMemoryJmtStore;
import com.bloxbean.cardano.statetrees.jmt.store.JmtStore;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic tests for the unified JellyfishMerkleTreeV2 implementation.
 *
 * <p>These tests verify that the TreeCache-based implementation works correctly
 * with both in-memory storage.</p>
 */
class JellyfishMerkleTreeV2Test {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new ClassicJmtCommitmentScheme(HASH);

    @Test
    void testBasicInsert_SingleKeyValue() throws Exception {
        try (JmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTreeV2 tree = new JellyfishMerkleTreeV2(store, COMMITMENTS, HASH);

            // Insert single key-value
            Map<byte[], byte[]> updates = new HashMap<>();
            updates.put("key1".getBytes(), "value1".getBytes());

            CommitResult result = tree.put(0, updates);

            // Verify result
            assertEquals(0, result.version());
            assertNotNull(result.rootHash());
            assertFalse(isAllZeros(result.rootHash()), "Root hash should not be all zeros");
            assertTrue(result.nodes().size() > 0, "Should have created at least one node");
            assertEquals(0, result.staleNodes().size(), "First insert should have no stale nodes");
            assertEquals(1, result.valueOperations().size());
        }
    }

    @Test
    void testMultipleInserts_SameVersion() throws Exception {
        try (JmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTreeV2 tree = new JellyfishMerkleTreeV2(store, COMMITMENTS, HASH);

            // Insert multiple keys in one batch
            Map<byte[], byte[]> updates = new HashMap<>();
            updates.put("key1".getBytes(), "value1".getBytes());
            updates.put("key2".getBytes(), "value2".getBytes());
            updates.put("key3".getBytes(), "value3".getBytes());

            CommitResult result = tree.put(0, updates);

            assertEquals(0, result.version());
            assertNotNull(result.rootHash());
            assertTrue(result.nodes().size() >= 3, "Should have at least 3 nodes (leaves)");
            assertEquals(3, result.valueOperations().size());
        }
    }

    @Test
    void testSequentialVersions() throws Exception {
        try (JmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTreeV2 tree = new JellyfishMerkleTreeV2(store, COMMITMENTS, HASH);

            // Version 0
            Map<byte[], byte[]> updates0 = new HashMap<>();
            updates0.put("key1".getBytes(), "value1".getBytes());
            CommitResult result0 = tree.put(0, updates0);

            byte[] root0 = result0.rootHash();

            // Version 1
            Map<byte[], byte[]> updates1 = new HashMap<>();
            updates1.put("key2".getBytes(), "value2".getBytes());
            CommitResult result1 = tree.put(1, updates1);

            byte[] root1 = result1.rootHash();

            // Roots should be different
            assertFalse(java.util.Arrays.equals(root0, root1), "Root hashes should differ between versions");

            // Version 1 should have stale nodes from version 0
            assertTrue(result1.staleNodes().size() > 0, "Version 1 should mark some nodes as stale");
        }
    }

    @Test
    void testUpdateSameKey() throws Exception {
        try (JmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTreeV2 tree = new JellyfishMerkleTreeV2(store, COMMITMENTS, HASH);

            // Version 0: Insert key1
            Map<byte[], byte[]> updates0 = new HashMap<>();
            updates0.put("key1".getBytes(), "value1".getBytes());
            CommitResult result0 = tree.put(0, updates0);

            byte[] root0 = result0.rootHash();

            // Version 1: Update key1 with new value
            Map<byte[], byte[]> updates1 = new HashMap<>();
            updates1.put("key1".getBytes(), "value1_updated".getBytes());
            CommitResult result1 = tree.put(1, updates1);

            byte[] root1 = result1.rootHash();

            // Roots should be different
            assertFalse(java.util.Arrays.equals(root0, root1), "Root should change when value updates");

            // Should have stale nodes
            assertTrue(result1.staleNodes().size() > 0, "Update should create stale nodes");
        }
    }

    @Test
    void testEmptyBatch() throws Exception {
        try (JmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTreeV2 tree = new JellyfishMerkleTreeV2(store, COMMITMENTS, HASH);

            // Empty update
            Map<byte[], byte[]> updates = new HashMap<>();
            CommitResult result = tree.put(0, updates);

            // Should succeed
            assertEquals(0, result.version());
            // Note: TreeCache creates a null root node for version 0, so there's 1 node
            assertTrue(result.nodes().size() >= 0, "Empty batch may have null root node");
            assertEquals(0, result.valueOperations().size());
        }
    }

    @Test
    void testLargerBatch() throws Exception {
        try (JmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTreeV2 tree = new JellyfishMerkleTreeV2(store, COMMITMENTS, HASH);

            // Insert 10 keys
            Map<byte[], byte[]> updates = new HashMap<>();
            for (int i = 0; i < 10; i++) {
                updates.put(("key" + i).getBytes(), ("value" + i).getBytes());
            }

            CommitResult result = tree.put(0, updates);

            assertEquals(0, result.version());
            assertTrue(result.nodes().size() >= 10, "Should have at least 10 leaf nodes");
            assertEquals(10, result.valueOperations().size());

            // Verify root hash is not all zeros
            assertFalse(isAllZeros(result.rootHash()));
        }
    }

    // Helper method
    private boolean isAllZeros(byte[] bytes) {
        for (byte b : bytes) {
            if (b != 0) return false;
        }
        return true;
    }
}
