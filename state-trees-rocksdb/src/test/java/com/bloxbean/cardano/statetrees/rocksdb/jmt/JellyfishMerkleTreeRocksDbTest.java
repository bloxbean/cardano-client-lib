package com.bloxbean.cardano.statetrees.rocksdb.jmt;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.statetrees.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for JellyfishMerkleTree with RocksDB backend.
 */
class JellyfishMerkleTreeRocksDbTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new ClassicJmtCommitmentScheme(HASH);

    @TempDir
    Path tempDir;

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void testBasicInsert_WithRocksDb() {
        try (RocksDbJmtStore store = new RocksDbJmtStore(tempDir.resolve("jmt-v2-basic").toString())) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store, COMMITMENTS, HASH);

            Map<byte[], byte[]> updates = new LinkedHashMap<>();
            updates.put(bytes("key1"), bytes("value1"));

            JellyfishMerkleTree.CommitResult result = tree.put(0, updates);

            assertEquals(0, result.version());
            assertNotNull(result.rootHash());
            assertFalse(result.nodes().isEmpty());

            // Verify root hash is persisted
            assertArrayEquals(result.rootHash(), store.rootHash(0).orElseThrow());
            assertArrayEquals(result.rootHash(), store.latestRoot().orElseThrow().rootHash());

            // Verify value is persisted
            byte[] keyHash = HASH.digest(bytes("key1"));
            byte[] persistedValue = store.getValue(keyHash).orElseThrow();
            assertArrayEquals(bytes("value1"), persistedValue);
        }
    }

    @Test
    void testMultipleInserts_WithRocksDb() {
        try (RocksDbJmtStore store = new RocksDbJmtStore(tempDir.resolve("jmt-v2-multiple").toString())) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store, COMMITMENTS, HASH);

            Map<byte[], byte[]> updates = new LinkedHashMap<>();
            updates.put(bytes("alice"), bytes("100"));
            updates.put(bytes("bob"), bytes("200"));
            updates.put(bytes("charlie"), bytes("300"));

            JellyfishMerkleTree.CommitResult result = tree.put(0, updates);

            assertEquals(0, result.version());
            assertNotNull(result.rootHash());
            assertEquals(3, result.valueOperations().size());

            // Verify all values are persisted
            assertArrayEquals(bytes("100"), store.getValue(HASH.digest(bytes("alice"))).orElseThrow());
            assertArrayEquals(bytes("200"), store.getValue(HASH.digest(bytes("bob"))).orElseThrow());
            assertArrayEquals(bytes("300"), store.getValue(HASH.digest(bytes("charlie"))).orElseThrow());
        }
    }

    @Test
    void testSequentialVersions_WithRocksDb() {
        try (RocksDbJmtStore store = new RocksDbJmtStore(tempDir.resolve("jmt-v2-sequential").toString())) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store, COMMITMENTS, HASH);

            // Version 0
            Map<byte[], byte[]> updates0 = new LinkedHashMap<>();
            updates0.put(bytes("key1"), bytes("value1"));
            JellyfishMerkleTree.CommitResult result0 = tree.put(0, updates0);

            // Version 1
            Map<byte[], byte[]> updates1 = new LinkedHashMap<>();
            updates1.put(bytes("key2"), bytes("value2"));
            JellyfishMerkleTree.CommitResult result1 = tree.put(1, updates1);

            // Version 2
            Map<byte[], byte[]> updates2 = new LinkedHashMap<>();
            updates2.put(bytes("key3"), bytes("value3"));
            JellyfishMerkleTree.CommitResult result2 = tree.put(2, updates2);

            // Verify all versions are persisted
            assertArrayEquals(result0.rootHash(), store.rootHash(0).orElseThrow());
            assertArrayEquals(result1.rootHash(), store.rootHash(1).orElseThrow());
            assertArrayEquals(result2.rootHash(), store.rootHash(2).orElseThrow());

            // Verify latest root
            assertArrayEquals(result2.rootHash(), store.latestRoot().orElseThrow().rootHash());
            assertEquals(2, store.latestRoot().orElseThrow().version());
        }
    }

    @Test
    void testUpdateSameKey_WithRocksDb() {
        try (RocksDbJmtStore store = new RocksDbJmtStore(tempDir.resolve("jmt-v2-update").toString())) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store, COMMITMENTS, HASH);

            // Initial insert
            Map<byte[], byte[]> updates0 = new LinkedHashMap<>();
            updates0.put(bytes("key1"), bytes("value1"));
            JellyfishMerkleTree.CommitResult result0 = tree.put(0, updates0);

            // Update same key
            Map<byte[], byte[]> updates1 = new LinkedHashMap<>();
            updates1.put(bytes("key1"), bytes("value2"));
            JellyfishMerkleTree.CommitResult result1 = tree.put(1, updates1);

            // Root hashes should be different
            assertFalse(Arrays.equals(result0.rootHash(), result1.rootHash()));

            // Verify latest value is persisted
            byte[] keyHash = HASH.digest(bytes("key1"));
            assertArrayEquals(bytes("value2"), store.getValue(keyHash).orElseThrow());

            // Stale nodes should be tracked
            assertFalse(result1.staleNodes().isEmpty());
        }
    }

    // Delete operation removed - V2 follows Diem-compatible architecture (no delete support)
    // See ADR-0012 for reorg/rollback handling using version-based state

    @Test
    void testLargerBatch_WithRocksDb() {
        try (RocksDbJmtStore store = new RocksDbJmtStore(tempDir.resolve("jmt-v2-large").toString())) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store, COMMITMENTS, HASH);

            Map<byte[], byte[]> updates = new LinkedHashMap<>();
            for (int i = 0; i < 100; i++) {
                updates.put(bytes("key" + i), bytes("value" + i));
            }

            JellyfishMerkleTree.CommitResult result = tree.put(0, updates);

            assertEquals(0, result.version());
            assertNotNull(result.rootHash());
            assertEquals(100, result.valueOperations().size());

            // Verify all values persisted
            for (int i = 0; i < 100; i++) {
                byte[] keyHash = HASH.digest(bytes("key" + i));
                assertArrayEquals(bytes("value" + i), store.getValue(keyHash).orElseThrow());
            }

            // Verify root hash persisted
            assertArrayEquals(result.rootHash(), store.rootHash(0).orElseThrow());
        }
    }

    @Test
    void testPruneStaleNodes_WithRocksDb() {
        try (RocksDbJmtStore store = new RocksDbJmtStore(tempDir.resolve("jmt-v2-prune").toString())) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store, COMMITMENTS, HASH);

            // Version 0: Insert
            Map<byte[], byte[]> updates0 = new LinkedHashMap<>();
            updates0.put(bytes("key1"), bytes("value1"));
            tree.put(0, updates0);

            // Version 1: Update (creates stale nodes)
            Map<byte[], byte[]> updates1 = new LinkedHashMap<>();
            updates1.put(bytes("key1"), bytes("value2"));
            JellyfishMerkleTree.CommitResult result1 = tree.put(1, updates1);

            // Verify stale nodes exist
            assertFalse(store.staleNodesUpTo(1).isEmpty());

            // Prune stale nodes
            int pruned = store.pruneUpTo(1);
            assertTrue(pruned >= result1.staleNodes().size());

            // Verify stale nodes are removed
            assertTrue(store.staleNodesUpTo(1).isEmpty());
        }
    }
}
