package com.bloxbean.cardano.statetrees.jmt;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JmtClassicProofWireTest {

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void inclusion_and_non_inclusion_classic_mode() {
        HashFunction hash = Blake2b256::digest;
        JellyfishMerkleTree tree = new JellyfishMerkleTree(hash);

        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        updates.put(b("alice"), b("100"));
        updates.put(b("bob"), b("200"));
        updates.put(b("carol"), b("300"));
        JellyfishMerkleTree.CommitResult result = tree.commit(1L, updates);
        byte[] root = result.rootHash();

        Optional<byte[]> w1 = tree.getProofWire(b("alice"), 1L);
        assertTrue(w1.isPresent());
        assertTrue(tree.verifyProofWire(root, b("alice"), b("100"), true, w1.get()));

        Optional<byte[]> w2 = tree.getProofWire(b("dave"), 1L);
        assertTrue(w2.isPresent());
        assertTrue(tree.verifyProofWire(root, b("dave"), null, false, w2.get()));
    }
}
