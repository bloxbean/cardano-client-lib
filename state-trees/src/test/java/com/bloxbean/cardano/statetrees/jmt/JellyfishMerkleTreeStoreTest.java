package com.bloxbean.cardano.statetrees.jmt;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.commitment.ClassicJmtCommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.store.InMemoryJmtStore;
import com.bloxbean.cardano.statetrees.jmt.store.JmtStore;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JellyfishMerkleTreeStoreTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new ClassicJmtCommitmentScheme(HASH);

    @Test
    void commitPersistAndProofs() {
        JmtStore backend = new InMemoryJmtStore();
        JellyfishMerkleTreeStore tree = new JellyfishMerkleTreeStore(backend, COMMITMENTS, HASH);

        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        updates.put(bytes("alice"), bytes("100"));
        updates.put(bytes("bob"), bytes("200"));
        JellyfishMerkleTree.CommitResult r1 = tree.commit(1, updates);

        assertArrayEquals(r1.rootHash(), tree.rootHash(1));
        assertArrayEquals(bytes("100"), tree.get(bytes("alice")));

        byte[] root = tree.rootHash(1);
        Optional<JmtProof> inclusion = tree.getProof(bytes("alice"), 1);
        assertTrue(inclusion.isPresent());
        assertTrue(JmtProofVerifier.verify(root, bytes("alice"), bytes("100"), inclusion.get(), HASH, COMMITMENTS));

        Map<byte[], byte[]> del = new LinkedHashMap<>();
        del.put(bytes("bob"), null);
        JellyfishMerkleTree.CommitResult r2 = tree.commit(2, del);
        assertNull(tree.get(bytes("bob")));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
