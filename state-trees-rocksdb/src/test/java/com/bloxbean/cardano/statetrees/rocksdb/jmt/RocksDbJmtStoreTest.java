package com.bloxbean.cardano.statetrees.rocksdb.jmt;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.JellyfishMerkleTree;
import com.bloxbean.cardano.statetrees.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.store.JmtStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RocksDbJmtStoreTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new ClassicJmtCommitmentScheme(HASH);

    @TempDir
    Path tempDir;

    @Test
    void applyCommitPersistsRootsNodesAndValues() {
        JellyfishMerkleTree tree = new JellyfishMerkleTree(COMMITMENTS, HASH);
        try (RocksDbJmtStore store = new RocksDbJmtStore(tempDir.resolve("jmt-db").toString())) {
            Map<byte[], byte[]> updates = new LinkedHashMap<>();
            updates.put(bytes("alice"), bytes("100"));
            updates.put(bytes("bob"), bytes("200"));

            JellyfishMerkleTree.CommitResult v1 = tree.commit(1, updates);
            store.applyCommit(v1);

            assertArrayEquals(v1.rootHash(), store.rootHash(1).orElseThrow());
            assertArrayEquals(v1.rootHash(), store.latestRoot().orElseThrow().rootHash());

            byte[] keyHash = HASH.digest(bytes("alice"));
            byte[] persistedValue = store.getValue(keyHash).orElseThrow();
            assertArrayEquals(bytes("100"), persistedValue);

            // Ensure nodes can be reloaded by NodeKey
            assertTrue(store.getNode(v1.nodes().keySet().iterator().next()).isPresent());

            // Second commit deletes bob to exercise stale index
            Map<byte[], byte[]> updates2 = new LinkedHashMap<>();
            updates2.put(bytes("bob"), null);
            JellyfishMerkleTree.CommitResult v2 = tree.commit(2, updates2);
            store.applyCommit(v2);

            List<com.bloxbean.cardano.statetrees.jmt.NodeKey> stale = store.staleNodesUpTo(2);
            assertFalse(stale.isEmpty());
            assertTrue(stale.containsAll(v2.staleNodes()));
            assertTrue(store.getValue(HASH.digest(bytes("bob"))).isEmpty());
            assertArrayEquals(v2.rootHash(), store.latestRoot().orElseThrow().rootHash());

            int pruned = store.pruneUpTo(2);
            assertTrue(pruned >= v2.staleNodes().size());
            for (com.bloxbean.cardano.statetrees.jmt.NodeKey nodeKey : v2.staleNodes()) {
                assertTrue(store.getNode(nodeKey).isEmpty());
            }
            assertTrue(store.staleNodesUpTo(2).isEmpty());
        }
    }

    @Test
    void crashMidCommitLeavesStoreUntouched() {
        JellyfishMerkleTree tree = new JellyfishMerkleTree(COMMITMENTS, HASH);
        try (RocksDbJmtStore store = new RocksDbJmtStore(tempDir.resolve("jmt-crash-db").toString())) {
            Map<byte[], byte[]> updates = new LinkedHashMap<>();
            updates.put(bytes("alice"), bytes("100"));
            JellyfishMerkleTree.CommitResult commitResult = tree.commit(1, updates);

            Map.Entry<com.bloxbean.cardano.statetrees.jmt.NodeKey, com.bloxbean.cardano.statetrees.jmt.JmtNode> firstNode =
                    commitResult.nodes().entrySet().iterator().next();
            byte[] aliceHash = HASH.digest(bytes("alice"));

            // Simulate a crash before commit() is invoked – the batch is never flushed.
            try (JmtStore.CommitBatch batch = store.beginCommit(commitResult.version(), JmtStore.CommitConfig.defaults())) {
                batch.putNode(firstNode.getKey(), firstNode.getValue());
                batch.putValue(aliceHash, bytes("100"));
                batch.setRootHash(commitResult.rootHash());
                // No call to batch.commit(); closing the try-with-resources simulates abrupt termination.
            }

            assertTrue(store.latestRoot().isEmpty(), "root should not be published when commit fails");
            assertTrue(store.rootHash(1).isEmpty(), "version root should not exist after aborted batch");
            assertTrue(store.getNode(firstNode.getKey()).isEmpty(), "node writes must not leak without commit");
            assertTrue(store.getValue(aliceHash).isEmpty(), "value writes must not leak without commit");

            // A subsequent successful commit should persist state normally.
            store.applyCommit(commitResult);
            assertArrayEquals(commitResult.rootHash(), store.rootHash(1).orElseThrow());
            assertArrayEquals(bytes("100"), store.getValue(aliceHash).orElseThrow());
        }
    }

    @Test
    void pruneRetentionSurvivesRocksDbRestart() {
        JellyfishMerkleTree tree = new JellyfishMerkleTree(COMMITMENTS, HASH);
        Path dbPath = tempDir.resolve("jmt-retention-db");

        JellyfishMerkleTree.CommitResult c1;
        JellyfishMerkleTree.CommitResult c2;
        JellyfishMerkleTree.CommitResult c3;
        List<com.bloxbean.cardano.statetrees.jmt.NodeKey> staleAfterV2;
        List<com.bloxbean.cardano.statetrees.jmt.NodeKey> nodesToKeep;

        try (RocksDbJmtStore store = new RocksDbJmtStore(dbPath.toString())) {
            Map<byte[], byte[]> v1 = new LinkedHashMap<>();
            v1.put(bytes("alice"), bytes("100"));
            v1.put(bytes("bob"), bytes("200"));
            c1 = tree.commit(1, v1);
            store.applyCommit(c1);

            Map<byte[], byte[]> v2 = new LinkedHashMap<>();
            v2.put(bytes("alice"), bytes("150")); // update alice, marking previous leaf stale
            c2 = tree.commit(2, v2);
            store.applyCommit(c2);

            Map<byte[], byte[]> v3 = new LinkedHashMap<>();
            v3.put(bytes("bob"), null); // delete bob
            v3.put(bytes("carol"), bytes("50")); // add carol to ensure live nodes remain
            c3 = tree.commit(3, v3);
            store.applyCommit(c3);

            staleAfterV2 = new ArrayList<>(c2.staleNodes());
            nodesToKeep = new ArrayList<>(c3.nodes().keySet());

            int pruned = store.pruneUpTo(2);
            assertTrue(pruned >= staleAfterV2.size(), "prune should remove at least the stale nodes marked by version 2");

            for (com.bloxbean.cardano.statetrees.jmt.NodeKey staleKey : staleAfterV2) {
                assertTrue(store.getNode(staleKey).isEmpty(), "stale node should be removed after prune");
            }

            for (com.bloxbean.cardano.statetrees.jmt.NodeKey liveKey : nodesToKeep) {
                assertTrue(store.getNode(liveKey).isPresent(), "live node should remain after prune");
            }

            assertArrayEquals(c3.rootHash(), store.latestRoot().orElseThrow().rootHash());
            assertArrayEquals(bytes("150"), store.getValue(HASH.digest(bytes("alice"))).orElseThrow());
            assertTrue(store.getValue(HASH.digest(bytes("bob"))).isEmpty(), "deleted value should not resurrect after prune");
        }

        try (RocksDbJmtStore reopened = new RocksDbJmtStore(dbPath.toString())) {
            for (com.bloxbean.cardano.statetrees.jmt.NodeKey staleKey : staleAfterV2) {
                assertTrue(reopened.getNode(staleKey).isEmpty(), "stale nodes must remain absent after restart");
            }
            for (com.bloxbean.cardano.statetrees.jmt.NodeKey liveKey : nodesToKeep) {
                assertTrue(reopened.getNode(liveKey).isPresent(), "live nodes must survive RocksDB reopen");
            }
            assertArrayEquals(c3.rootHash(), reopened.latestRoot().orElseThrow().rootHash());
            assertArrayEquals(bytes("150"), reopened.getValue(HASH.digest(bytes("alice"))).orElseThrow());
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
