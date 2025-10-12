package com.bloxbean.cardano.vds.jmt.rocksdb;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.vds.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.vds.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.vds.jmt.store.JmtStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RocksDbJmtStore using JellyfishMerkleTreeV2 (Diem-style implementation).
 */
class RocksDbJmtStoreTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new ClassicJmtCommitmentScheme(HASH);

    @TempDir
    Path tempDir;

    @Test
    void commitPersistsRootsNodesAndValues() {
        try (RocksDbJmtStore store = new RocksDbJmtStore(tempDir.resolve("jmt-db").toString())) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store, COMMITMENTS, HASH);

            Map<byte[], byte[]> updates = new LinkedHashMap<>();
            updates.put(bytes("alice"), bytes("100"));
            updates.put(bytes("bob"), bytes("200"));

            JellyfishMerkleTree.CommitResult v1 = tree.put(1, updates);

            // Verify root and values persisted
            assertArrayEquals(v1.rootHash(), store.rootHash(1).orElseThrow());
            assertArrayEquals(v1.rootHash(), store.latestRoot().orElseThrow().rootHash());

            byte[] keyHash = HASH.digest(bytes("alice"));
            byte[] persistedValue = store.getValue(keyHash).orElseThrow();
            assertArrayEquals(bytes("100"), persistedValue);

            // Verify nodes are accessible
            assertTrue(store.getNode(v1.nodes().keySet().iterator().next()).isPresent());

            // Second commit updates alice
            Map<byte[], byte[]> updates2 = new LinkedHashMap<>();
            updates2.put(bytes("alice"), bytes("150"));
            JellyfishMerkleTree.CommitResult v2 = tree.put(2, updates2);

            // Verify stale nodes tracked
            assertFalse(store.staleNodesUpTo(2).isEmpty());
            assertArrayEquals(v2.rootHash(), store.latestRoot().orElseThrow().rootHash());

            // Prune old version (prune up to version 2 to remove stale nodes from v2)
            int pruned = store.pruneUpTo(2);
            assertTrue(pruned > 0);
            assertTrue(store.staleNodesUpTo(2).isEmpty());
        }
    }

    @Test
    void crashMidCommitLeavesStoreUntouched() {
        try (RocksDbJmtStore store = new RocksDbJmtStore(tempDir.resolve("jmt-crash-db").toString())) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store, COMMITMENTS, HASH);

            Map<byte[], byte[]> updates = new LinkedHashMap<>();
            updates.put(bytes("alice"), bytes("100"));

            // Simulate crash by starting a commit but not completing it
            long version = 1;
            byte[] aliceHash = HASH.digest(bytes("alice"));

            // Start a batch and abandon it (crash simulation)
            try (JmtStore.CommitBatch batch = store.beginCommit(version, JmtStore.CommitConfig.defaults())) {
                batch.putValue(aliceHash, bytes("100"));
                // No batch.commit() call - simulates crash
            }

            // Store should be untouched
            assertTrue(store.latestRoot().isEmpty(), "root should not be published when commit fails");
            assertTrue(store.rootHash(1).isEmpty(), "version root should not exist after aborted batch");
            assertTrue(store.getValue(aliceHash).isEmpty(), "value writes must not leak without commit");

            // Successful commit should work normally
            JellyfishMerkleTree.CommitResult result = tree.put(version, updates);
            assertArrayEquals(result.rootHash(), store.rootHash(1).orElseThrow());
            assertArrayEquals(bytes("100"), store.getValue(aliceHash).orElseThrow());
        }
    }

    @Test
    void pruneRetentionSurvivesRocksDbRestart() {
        Path dbPath = tempDir.resolve("jmt-retention-db");

        byte[] root3;

        // Initial writes and prune
        try (RocksDbJmtStore store = new RocksDbJmtStore(dbPath.toString())) {
            JellyfishMerkleTree tree = new JellyfishMerkleTree(store, COMMITMENTS, HASH);

            Map<byte[], byte[]> v1 = new LinkedHashMap<>();
            v1.put(bytes("alice"), bytes("100"));
            v1.put(bytes("bob"), bytes("200"));
            tree.put(1, v1);

            Map<byte[], byte[]> v2 = new LinkedHashMap<>();
            v2.put(bytes("alice"), bytes("150")); // update alice
            tree.put(2, v2);

            Map<byte[], byte[]> v3 = new LinkedHashMap<>();
            v3.put(bytes("carol"), bytes("50")); // add carol
            JellyfishMerkleTree.CommitResult c3 = tree.put(3, v3);
            root3 = c3.rootHash();

            // Prune up to version 2
            int pruned = store.pruneUpTo(2);
            assertTrue(pruned > 0, "prune should remove stale nodes");

            assertArrayEquals(root3, store.latestRoot().orElseThrow().rootHash());
            assertArrayEquals(bytes("150"), store.getValue(HASH.digest(bytes("alice"))).orElseThrow());
        }

        // Reopen and verify prune persisted
        try (RocksDbJmtStore reopened = new RocksDbJmtStore(dbPath.toString())) {
            assertArrayEquals(root3, reopened.latestRoot().orElseThrow().rootHash());
            assertArrayEquals(bytes("150"), reopened.getValue(HASH.digest(bytes("alice"))).orElseThrow());
            assertArrayEquals(bytes("200"), reopened.getValue(HASH.digest(bytes("bob"))).orElseThrow());
            assertArrayEquals(bytes("50"), reopened.getValue(HASH.digest(bytes("carol"))).orElseThrow());
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
