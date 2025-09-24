package com.bloxbean.cardano.statetrees.jmt;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.commitment.MpfCommitmentScheme;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JellyfishMerkleTreeTest {

    private static final HashFunction HASH = Blake2b256::digest;
    private static final CommitmentScheme COMMITMENTS = new MpfCommitmentScheme(HASH);

    @Test
    void commitMaintainsHistoricalVersions() {
        JellyfishMerkleTree tree = new JellyfishMerkleTree(COMMITMENTS, HASH);

        Map<byte[], byte[]> v1 = new LinkedHashMap<>();
        v1.put(bytes("alice"), bytes("100"));
        v1.put(bytes("bob"), bytes("200"));
        tree.commit(1, v1);

        assertArrayEquals(bytes("100"), tree.get(bytes("alice")));
        assertArrayEquals(bytes("200"), tree.get(bytes("bob")));

        Map<byte[], byte[]> v2 = new LinkedHashMap<>();
        v2.put(bytes("alice"), bytes("150"));
        tree.commit(2, v2);

        assertArrayEquals(bytes("150"), tree.get(bytes("alice")));
        assertArrayEquals(bytes("100"), tree.get(bytes("alice"), 1));

        Map<byte[], byte[]> v3 = new LinkedHashMap<>();
        v3.put(bytes("bob"), null); // delete bob
        tree.commit(3, v3);

        assertNull(tree.get(bytes("bob")));
        assertArrayEquals(bytes("200"), tree.get(bytes("bob"), 1));
    }

    @Test
    void proofVerificationRoundTrip() {
        JellyfishMerkleTree tree = new JellyfishMerkleTree(COMMITMENTS, HASH);

        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        updates.put(bytes("alice"), bytes("100"));
        updates.put(bytes("bob"), bytes("200"));
        tree.commit(1, updates);

        byte[] root = tree.rootHash(1);

        byte[] aliceKey = bytes("alice");
        Optional<JmtProof> inclusionProof = tree.getProof(aliceKey, 1);
        assertTrue(inclusionProof.isPresent());
        assertTrue(JmtProofVerifier.verify(root, aliceKey, bytes("100"), inclusionProof.get(), HASH, COMMITMENTS));

        byte[] carolKey = bytes("carol");
        Optional<JmtProof> nonInclusion = tree.getProof(carolKey, 1);
        assertTrue(nonInclusion.isPresent());
        assertTrue(JmtProofVerifier.verify(root, carolKey, null, nonInclusion.get(), HASH, COMMITMENTS));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
