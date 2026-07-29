package com.bloxbean.cardano.vds.jmt.store;

import com.bloxbean.cardano.vds.core.NibblePath;
import com.bloxbean.cardano.vds.core.nibbles.Nibbles;
import com.bloxbean.cardano.vds.jmt.JmtLeafNode;
import com.bloxbean.cardano.vds.jmt.JmtNode;
import com.bloxbean.cardano.vds.jmt.NodeKey;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryJmtStoreTest {

    @Test
    void rawCommitReplayCannotMutateCommittedValuesOrRegressLatest() {
        InMemoryJmtStore store = new InMemoryJmtStore();
        byte[] keyHash = new byte[32];
        keyHash[0] = 0x0A;
        byte[] root1 = root(1);
        byte[] root2 = root(2);

        commitValue(store, 1L, root1, keyHash, bytes("100"));
        commitValue(store, 2L, root2, keyHash, bytes("200"));

        try (JmtStore.CommitBatch historical = store.beginCommit(
                1L, JmtStore.CommitConfig.defaults())) {
            historical.putValue(keyHash, bytes("stale"));
            historical.setRootHash(root1);
            assertThrows(JmtWriteConflictException.class, historical::commit);
        }

        try (JmtStore.CommitBatch replay = store.beginCommit(
                2L, JmtStore.CommitConfig.defaults())) {
            replay.putValue(keyHash, bytes("conflict"));
            replay.setRootHash(root2);
            assertDoesNotThrow(replay::commit);
        }

        assertEquals(2L, store.latestRoot().orElseThrow().version());
        assertArrayEquals(root2, store.latestRoot().orElseThrow().rootHash());
        assertArrayEquals(bytes("200"), store.getValue(keyHash).orElseThrow());
        assertArrayEquals(bytes("100"), store.getValueAt(keyHash, 1L).orElseThrow());
    }

    @Test
    void getValueAtRespectsDeletes() throws Exception {
        InMemoryJmtStore store = new InMemoryJmtStore();
        byte[] keyHash = new byte[32];
        keyHash[0] = 0x0A;
        byte[] value = "200".getBytes();
        byte[] valueHash = new byte[32];
        valueHash[0] = 0x5;
        NibblePath path = NibblePath.of(Nibbles.toNibbles(keyHash));
        NodeKey leafKey = NodeKey.of(path, 1L);
        JmtNode leaf = JmtLeafNode.of(keyHash, valueHash);

        try (JmtStore.CommitBatch batch = store.beginCommit(1L, JmtStore.CommitConfig.defaults())) {
            batch.putNode(leafKey, leaf);
            batch.putValue(keyHash, value);
            batch.setRootHash(new byte[32]);
            batch.commit();
        }

        assertArrayEquals(value, store.getValueAt(keyHash, 1L).orElse(null));

        try (JmtStore.CommitBatch batch = store.beginCommit(3L, JmtStore.CommitConfig.defaults())) {
            batch.markStale(leafKey);
            batch.deleteValue(keyHash);
            batch.setRootHash(new byte[32]);
            batch.commit();
        }

        Optional<byte[]> deleted = store.getValueAt(keyHash, 3L);
        assertTrue(deleted.isEmpty(), "Deleted value should not be returned at newer version");
    }

    private static void commitValue(InMemoryJmtStore store,
                                    long version,
                                    byte[] root,
                                    byte[] keyHash,
                                    byte[] value) {
        try (JmtStore.CommitBatch batch = store.beginCommit(
                version, JmtStore.CommitConfig.defaults())) {
            batch.putValue(keyHash, value);
            batch.setRootHash(root);
            batch.commit();
        }
    }

    private static byte[] root(int marker) {
        byte[] root = new byte[32];
        root[0] = (byte) marker;
        return root;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
