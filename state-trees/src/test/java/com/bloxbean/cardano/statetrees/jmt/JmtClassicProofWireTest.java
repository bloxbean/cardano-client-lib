package com.bloxbean.cardano.statetrees.jmt;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import com.bloxbean.cardano.statetrees.jmt.mode.JmtMode;
import com.bloxbean.cardano.statetrees.jmt.mode.JmtModes;
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
        JmtMode classic = JmtModes.classic(hash);
        JellyfishMerkleTree tree = new JellyfishMerkleTree(classic, hash);

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

    @Test
    void cross_mode_mismatch_rejected() {
        HashFunction hash = Blake2b256::digest;
        JellyfishMerkleTree mpfTree = new JellyfishMerkleTree(JmtModes.mpf(hash), hash);
        JellyfishMerkleTree classicTree = new JellyfishMerkleTree(JmtModes.classic(hash), hash);

        Map<byte[], byte[]> updates = new LinkedHashMap<>();
        updates.put(b("x"), b("1"));
        updates.put(b("y"), b("2"));
        mpfTree.commit(1L, updates);
        classicTree.commit(1L, updates);

        byte[] mpfWire = mpfTree.getProofWire(b("x"), 1L).orElseThrow();
        byte[] classicWire = classicTree.getProofWire(b("x"), 1L).orElseThrow();

        // Classic verifier should reject MPF wire
        assertThrows(IllegalArgumentException.class, () -> classicTree.verifyProofWire(classicTree.rootHash(1L), b("x"), b("1"), true, mpfWire));
        // MPF verifier should reject Classic wire
        assertThrows(IllegalArgumentException.class, () -> mpfTree.verifyProofWire(mpfTree.rootHash(1L), b("x"), b("1"), true, classicWire));
    }
}

