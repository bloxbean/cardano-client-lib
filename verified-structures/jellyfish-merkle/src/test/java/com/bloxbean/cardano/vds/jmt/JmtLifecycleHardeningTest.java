package com.bloxbean.cardano.vds.jmt;

import com.bloxbean.cardano.vds.core.NibblePath;
import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.vds.jmt.store.InMemoryJmtStore;
import com.bloxbean.cardano.vds.jmt.store.JmtStore;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JmtLifecycleHardeningTest {

    private static final HashFunction HASH = Blake2b256::digest;

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static Map<byte[], byte[]> update(String key, String value) {
        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        updates.put(bytes(key), bytes(value));
        return updates;
    }

    @Test
    void rejectsNon256BitHashConfiguration() {
        HashFunction shortHash = ignored -> new byte[28];
        assertThrows(IllegalArgumentException.class,
                () -> new JellyfishMerkleTree(new InMemoryJmtStore(), shortHash));
    }

    @Test
    void gappedVersionMarksActualPriorRootStale() {
        InMemoryJmtStore store = new InMemoryJmtStore();
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store, HASH);
        tree.put(1L, update("alice", "100"));

        JellyfishMerkleTree.CommitResult v3 = tree.put(3L, update("alice", "200"));

        assertTrue(v3.staleNodes().contains(NodeKey.of(NibblePath.EMPTY, 1L)),
                "the persisted v1 root, not a synthetic v2 key, must become stale");
        assertTrue(v3.staleNodes().stream().noneMatch(key -> key.version() == 2L));
        JmtProof proof = tree.getProof(bytes("alice"), 3L).orElseThrow();
        assertTrue(JmtProofVerifier.verify(v3.rootHash(), bytes("alice"), bytes("200"), proof,
                HASH, new ClassicJmtCommitmentScheme(HASH)));
    }

    @Test
    void rejectsEveryHistoricalWriteIncludingAnOlderCommittedReplay() {
        InMemoryJmtStore store = new InMemoryJmtStore();
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store, HASH);
        byte[] root1 = tree.put(1L, update("alice", "100")).rootHash();
        tree.put(3L, update("bob", "200"));

        assertThrows(IllegalArgumentException.class, () -> tree.put(2L, update("mallory", "1")));
        assertThrows(IllegalArgumentException.class, () -> tree.put(1L, update("alice", "100")));
        assertArrayEquals(root1, store.rootHash(1L).orElseThrow());
        assertEquals(3L, store.latestRoot().orElseThrow().version());
    }

    @Test
    void latestVersionReplayDoesNotMarkLiveNodesStaleOrBreakPruning() {
        InMemoryJmtStore store = new InMemoryJmtStore();
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store, HASH);
        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        updates.put(bytes("alice"), bytes("100"));
        updates.put(bytes("bob"), bytes("200"));
        byte[] originalRoot = tree.put(1L, updates).rootHash();

        JellyfishMerkleTree.CommitResult replay = tree.put(1L, updates);

        assertArrayEquals(originalRoot, replay.rootHash());
        assertTrue(replay.staleNodes().stream().noneMatch(replay.nodes()::containsKey));
        store.pruneUpTo(1L);
        JmtProof proof = tree.getProof(bytes("alice"), 1L).orElseThrow();
        assertTrue(JmtProofVerifier.verify(originalRoot, bytes("alice"), bytes("100"), proof,
                HASH, new ClassicJmtCommitmentScheme(HASH)));
    }

    @Test
    void genesisReplayDoesNotEraseTheTree() {
        InMemoryJmtStore store = new InMemoryJmtStore();
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store, HASH);
        byte[] root = tree.put(0L, update("alice", "100")).rootHash();

        assertArrayEquals(root, tree.put(0L, update("alice", "100")).rootHash());
        store.pruneUpTo(0L);

        JmtProof proof = tree.getProof(bytes("alice"), 0L).orElseThrow();
        assertTrue(JmtProofVerifier.verify(root, bytes("alice"), bytes("100"), proof,
                HASH, new ClassicJmtCommitmentScheme(HASH)));
    }

    @Test
    void rejectsDuplicateLogicalKeysBeforeCommitting() {
        InMemoryJmtStore store = new InMemoryJmtStore();
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store, HASH);
        Map<byte[], byte[]> duplicates = new LinkedHashMap<>();
        duplicates.put(bytes("alice"), bytes("100"));
        duplicates.put(bytes("alice"), bytes("999"));

        assertThrows(IllegalArgumentException.class, () -> tree.put(0L, duplicates));
        assertTrue(store.latestRoot().isEmpty());
    }

    @Test
    void missingCommittedChildFailsClosedInsteadOfDroppingSubtree() {
        InMemoryJmtStore store = new InMemoryJmtStore();
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store, HASH);
        byte[] key = bytes("alice");
        int childNibble = (HASH.digest(key)[0] >>> 4) & 0x0F;
        byte[] missingChildHash = Blake2b256.digest(bytes("missing-child"));
        JmtInternalNode root = JmtInternalNode.of(
                1 << childNibble, new byte[][]{missingChildHash}, null);
        ClassicJmtCommitmentScheme commitments = new ClassicJmtCommitmentScheme(HASH);
        byte[][] children = new byte[16][];
        children[childNibble] = missingChildHash;
        try (JmtStore.CommitBatch batch = store.beginCommit(
                0L, JmtStore.CommitConfig.defaults())) {
            batch.putNode(NodeKey.of(NibblePath.EMPTY, 0L), root);
            batch.setRootHash(commitments.commitBranch(NibblePath.EMPTY, children));
            batch.commit();
        }

        assertThrows(IllegalStateException.class,
                () -> tree.put(1L, update("alice", "100")));
        assertEquals(0L, store.latestRoot().orElseThrow().version());
    }

    @Test
    void directCommitRequiresAProfileSizedRoot() {
        InMemoryJmtStore store = new InMemoryJmtStore();
        new JellyfishMerkleTree(store);

        try (JmtStore.CommitBatch missingRoot = store.beginCommit(
                0L, JmtStore.CommitConfig.defaults())) {
            assertThrows(IllegalStateException.class, missingRoot::commit);
        }
        try (JmtStore.CommitBatch shortRoot = store.beginCommit(
                0L, JmtStore.CommitConfig.defaults())) {
            shortRoot.setRootHash(new byte[31]);
            assertThrows(IllegalStateException.class, shortRoot::commit);
        }
        assertTrue(store.latestRoot().isEmpty());
    }

    @Test
    void fullDepthCommonPrefixProducesVerifiableProofs() {
        byte[] keyA = new byte[32];
        byte[] keyB = new byte[32];
        keyA[31] = 0x01;
        keyB[31] = 0x02;
        HashFunction identityForKeys = data -> data.length == 32
                ? data.clone() : Blake2b256.digest(data);
        ClassicJmtCommitmentScheme commitments = new ClassicJmtCommitmentScheme(identityForKeys);
        JellyfishMerkleTree tree = new JellyfishMerkleTree(
                new InMemoryJmtStore(), commitments, identityForKeys);
        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        updates.put(keyA, bytes("a"));
        updates.put(keyB, bytes("b"));

        byte[] root = tree.put(0L, updates).rootHash();

        assertTrue(JmtProofVerifier.verify(root, keyA, bytes("a"),
                tree.getProof(keyA, 0L).orElseThrow(), identityForKeys, commitments));
        assertTrue(JmtProofVerifier.verify(root, keyB, bytes("b"),
                tree.getProof(keyB, 0L).orElseThrow(), identityForKeys, commitments));
    }

    @Test
    void inMemoryRejectsDivergentReplayWithoutMutatingValues() {
        InMemoryJmtStore store = new InMemoryJmtStore();
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store, HASH);
        byte[] originalRoot = tree.put(1L, update("alice", "100")).rootHash();

        assertThrows(IllegalStateException.class, () -> tree.put(1L, update("alice", "999")));

        assertArrayEquals(originalRoot, store.rootHash(1L).orElseThrow());
        assertArrayEquals(bytes("100"), tree.get(bytes("alice")).orElseThrow());
    }

    @Test
    void truncateAtLongMaxValuePreservesAllState() {
        InMemoryJmtStore store = new InMemoryJmtStore();
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store, HASH);
        byte[] root = tree.put(1L, update("alice", "100")).rootHash();

        store.truncateAfter(Long.MAX_VALUE);

        assertArrayEquals(root, store.rootHash(1L).orElseThrow());
        assertArrayEquals(bytes("100"), tree.get(bytes("alice")).orElseThrow());
    }
}
