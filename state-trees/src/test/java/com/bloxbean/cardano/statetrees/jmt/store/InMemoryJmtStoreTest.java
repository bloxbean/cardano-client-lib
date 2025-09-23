package com.bloxbean.cardano.statetrees.jmt.store;

import com.bloxbean.cardano.statetrees.common.NibblePath;
import com.bloxbean.cardano.statetrees.common.nibbles.Nibbles;
import com.bloxbean.cardano.statetrees.jmt.JmtLeafNode;
import com.bloxbean.cardano.statetrees.jmt.JmtNode;
import com.bloxbean.cardano.statetrees.jmt.NodeKey;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryJmtStoreTest {

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
}
