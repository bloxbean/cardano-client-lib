package com.bloxbean.cardano.statetrees.jmt;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTree.CommitResult;
import com.bloxbean.cardano.statetrees.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.store.InMemoryJmtStore;
import com.bloxbean.cardano.statetrees.jmt.store.JmtStore;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comparison tests between JellyfishMerkleTreeV2 (new TreeCache-based implementation)
 * and JellyfishMerkleTree (reference implementation).
 *
 * <p>These tests verify that the new implementation produces the same root hashes
 * and tree structure as the reference implementation.</p>
 */
class JellyfishMerkleTreeV2ComparisonTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new ClassicJmtCommitmentScheme(HASH);

    @Test
    void testSingleInsert_MatchesReference() throws Exception {
        // Reference implementation
        JellyfishMerkleTree reference = new JellyfishMerkleTree(COMMITMENTS, HASH);

        // New implementation
        try (JmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTreeV2 v2 = new JellyfishMerkleTreeV2(store, COMMITMENTS, HASH);

            // Same update
            Map<byte[], byte[]> updates = new HashMap<>();
            updates.put("key1".getBytes(), "value1".getBytes());

            // Commit to both
            CommitResult refResult = reference.commit(0, updates);
            JellyfishMerkleTreeV2.CommitResult v2Result = v2.put(0, updates);

            // Compare root hashes
            assertArrayEquals(refResult.rootHash(), v2Result.rootHash(),
                    "Root hashes should match between reference and V2 implementations");
        }
    }

    @Test
    void testMultipleInserts_MatchesReference() throws Exception {
        JellyfishMerkleTree reference = new JellyfishMerkleTree(COMMITMENTS, HASH);

        try (JmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTreeV2 v2 = new JellyfishMerkleTreeV2(store, COMMITMENTS, HASH);

            Map<byte[], byte[]> updates = new HashMap<>();
            updates.put("key1".getBytes(), "value1".getBytes());
            updates.put("key2".getBytes(), "value2".getBytes());
            updates.put("key3".getBytes(), "value3".getBytes());

            CommitResult refResult = reference.commit(0, updates);
            JellyfishMerkleTreeV2.CommitResult v2Result = v2.put(0, updates);

            assertArrayEquals(refResult.rootHash(), v2Result.rootHash(),
                    "Root hashes should match for multiple inserts");
        }
    }

    @Test
    void testSequentialVersions_MatchesReference() throws Exception {
        JellyfishMerkleTree reference = new JellyfishMerkleTree(COMMITMENTS, HASH);

        try (JmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTreeV2 v2 = new JellyfishMerkleTreeV2(store, COMMITMENTS, HASH);

            // Version 0
            Map<byte[], byte[]> updates0 = new HashMap<>();
            updates0.put("key1".getBytes(), "value1".getBytes());

            CommitResult refResult0 = reference.commit(0, updates0);
            JellyfishMerkleTreeV2.CommitResult v2Result0 = v2.put(0, updates0);

            assertArrayEquals(refResult0.rootHash(), v2Result0.rootHash(),
                    "Version 0 root hashes should match");

            // Version 1
            Map<byte[], byte[]> updates1 = new HashMap<>();
            updates1.put("key2".getBytes(), "value2".getBytes());

            CommitResult refResult1 = reference.commit(1, updates1);
            JellyfishMerkleTreeV2.CommitResult v2Result1 = v2.put(1, updates1);

            assertArrayEquals(refResult1.rootHash(), v2Result1.rootHash(),
                    "Version 1 root hashes should match");

            // Version 2
            Map<byte[], byte[]> updates2 = new HashMap<>();
            updates2.put("key3".getBytes(), "value3".getBytes());

            CommitResult refResult2 = reference.commit(2, updates2);
            JellyfishMerkleTreeV2.CommitResult v2Result2 = v2.put(2, updates2);

            assertArrayEquals(refResult2.rootHash(), v2Result2.rootHash(),
                    "Version 2 root hashes should match");
        }
    }

    @Test
    void testUpdateSameKey_MatchesReference() throws Exception {
        JellyfishMerkleTree reference = new JellyfishMerkleTree(COMMITMENTS, HASH);

        try (JmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTreeV2 v2 = new JellyfishMerkleTreeV2(store, COMMITMENTS, HASH);

            // Version 0: Insert
            Map<byte[], byte[]> updates0 = new HashMap<>();
            updates0.put("key1".getBytes(), "value1".getBytes());

            CommitResult refResult0 = reference.commit(0, updates0);
            JellyfishMerkleTreeV2.CommitResult v2Result0 = v2.put(0, updates0);

            assertArrayEquals(refResult0.rootHash(), v2Result0.rootHash());

            // Version 1: Update same key
            Map<byte[], byte[]> updates1 = new HashMap<>();
            updates1.put("key1".getBytes(), "value1_updated".getBytes());

            CommitResult refResult1 = reference.commit(1, updates1);
            JellyfishMerkleTreeV2.CommitResult v2Result1 = v2.put(1, updates1);

            assertArrayEquals(refResult1.rootHash(), v2Result1.rootHash(),
                    "Root hash should match after updating same key");
        }
    }

    @Test
    void testRandomizedInserts_10Keys_MatchesReference() throws Exception {
        Random random = new Random(42); // Fixed seed for reproducibility

        JellyfishMerkleTree reference = new JellyfishMerkleTree(COMMITMENTS, HASH);

        try (JmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTreeV2 v2 = new JellyfishMerkleTreeV2(store, COMMITMENTS, HASH);

            Map<byte[], byte[]> updates = new HashMap<>();
            for (int i = 0; i < 10; i++) {
                byte[] key = new byte[32];
                byte[] value = new byte[32];
                random.nextBytes(key);
                random.nextBytes(value);
                updates.put(key, value);
            }

            CommitResult refResult = reference.commit(0, updates);
            JellyfishMerkleTreeV2.CommitResult v2Result = v2.put(0, updates);

            assertArrayEquals(refResult.rootHash(), v2Result.rootHash(),
                    "Root hashes should match for randomized 10-key insertion");
        }
    }

    @Test
    void testRandomizedInserts_50Keys_MatchesReference() throws Exception {
        Random random = new Random(12345);

        JellyfishMerkleTree reference = new JellyfishMerkleTree(COMMITMENTS, HASH);

        try (JmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTreeV2 v2 = new JellyfishMerkleTreeV2(store, COMMITMENTS, HASH);

            Map<byte[], byte[]> updates = new HashMap<>();
            for (int i = 0; i < 50; i++) {
                byte[] key = new byte[32];
                byte[] value = new byte[32];
                random.nextBytes(key);
                random.nextBytes(value);
                updates.put(key, value);
            }

            CommitResult refResult = reference.commit(0, updates);
            JellyfishMerkleTreeV2.CommitResult v2Result = v2.put(0, updates);

            assertArrayEquals(refResult.rootHash(), v2Result.rootHash(),
                    "Root hashes should match for randomized 50-key insertion");
        }
    }

    @Test
    void testEmptyTree_MatchesReference() throws Exception {
        JellyfishMerkleTree reference = new JellyfishMerkleTree(COMMITMENTS, HASH);

        try (JmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTreeV2 v2 = new JellyfishMerkleTreeV2(store, COMMITMENTS, HASH);

            Map<byte[], byte[]> updates = new HashMap<>();

            CommitResult refResult = reference.commit(0, updates);
            JellyfishMerkleTreeV2.CommitResult v2Result = v2.put(0, updates);

            assertArrayEquals(refResult.rootHash(), v2Result.rootHash(),
                    "Root hashes should match for empty tree");
        }
    }

    @Test
    void testCollisionHandling_MatchesReference() throws Exception {
        JellyfishMerkleTree reference = new JellyfishMerkleTree(COMMITMENTS, HASH);

        try (JmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTreeV2 v2 = new JellyfishMerkleTreeV2(store, COMMITMENTS, HASH);

            // Use keys that are likely to create collisions at various depths
            Map<byte[], byte[]> updates = new HashMap<>();
            updates.put("a".getBytes(), "value_a".getBytes());
            updates.put("aa".getBytes(), "value_aa".getBytes());
            updates.put("aaa".getBytes(), "value_aaa".getBytes());
            updates.put("b".getBytes(), "value_b".getBytes());
            updates.put("bb".getBytes(), "value_bb".getBytes());

            CommitResult refResult = reference.commit(0, updates);
            JellyfishMerkleTreeV2.CommitResult v2Result = v2.put(0, updates);

            assertArrayEquals(refResult.rootHash(), v2Result.rootHash(),
                    "Root hashes should match when handling collisions");
        }
    }

    @Test
    void testMultipleVersionsWithMixedOperations_MatchesReference() throws Exception {
        JellyfishMerkleTree reference = new JellyfishMerkleTree(COMMITMENTS, HASH);

        try (JmtStore store = new InMemoryJmtStore()) {
            JellyfishMerkleTreeV2 v2 = new JellyfishMerkleTreeV2(store, COMMITMENTS, HASH);

            // Version 0: Insert 3 keys
            Map<byte[], byte[]> updates0 = new HashMap<>();
            updates0.put("key1".getBytes(), "value1".getBytes());
            updates0.put("key2".getBytes(), "value2".getBytes());
            updates0.put("key3".getBytes(), "value3".getBytes());

            CommitResult refResult0 = reference.commit(0, updates0);
            JellyfishMerkleTreeV2.CommitResult v2Result0 = v2.put(0, updates0);
            assertArrayEquals(refResult0.rootHash(), v2Result0.rootHash());

            // Version 1: Update one, add one
            Map<byte[], byte[]> updates1 = new HashMap<>();
            updates1.put("key2".getBytes(), "value2_updated".getBytes());
            updates1.put("key4".getBytes(), "value4".getBytes());

            CommitResult refResult1 = reference.commit(1, updates1);
            JellyfishMerkleTreeV2.CommitResult v2Result1 = v2.put(1, updates1);
            assertArrayEquals(refResult1.rootHash(), v2Result1.rootHash());

            // Version 2: Add more keys
            Map<byte[], byte[]> updates2 = new HashMap<>();
            updates2.put("key5".getBytes(), "value5".getBytes());
            updates2.put("key6".getBytes(), "value6".getBytes());

            CommitResult refResult2 = reference.commit(2, updates2);
            JellyfishMerkleTreeV2.CommitResult v2Result2 = v2.put(2, updates2);
            assertArrayEquals(refResult2.rootHash(), v2Result2.rootHash());
        }
    }
}
