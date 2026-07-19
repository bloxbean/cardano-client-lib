package com.bloxbean.cardano.vds.jmt;

import com.bloxbean.cardano.vds.core.NibblePath;
import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.vds.jmt.store.InMemoryJmtStore;
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
    void rejectsNewHistoricalVersionButAllowsExistingReplay() {
        InMemoryJmtStore store = new InMemoryJmtStore();
        JellyfishMerkleTree tree = new JellyfishMerkleTree(store, HASH);
        byte[] root1 = tree.put(1L, update("alice", "100")).rootHash();
        tree.put(3L, update("bob", "200"));

        assertThrows(IllegalArgumentException.class, () -> tree.put(2L, update("mallory", "1")));
        assertArrayEquals(root1, tree.put(1L, update("alice", "100")).rootHash());
        assertEquals(3L, store.latestRoot().orElseThrow().version());
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
