package com.bloxbean.cardano.vds.mpt;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.mpt.mode.Modes;
import com.bloxbean.cardano.vds.mpt.test.TestNodeStore;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MptClassicProofWireTest {

    private static final HashFunction HF = Blake2b256::digest;

    @Test
    void inclusion_roundtrip_classic_wire() {
        TestNodeStore store = new TestNodeStore();
        MerklePatriciaTrie trie = new MerklePatriciaTrie(store, HF, Modes.classic(HF));

        byte[] kPrefix = hex("aa");
        byte[] kLonger = hex("aa01");
        byte[] vPrefix = b("P");
        byte[] vLonger = b("L");

        trie.put(kPrefix, vPrefix);
        trie.put(kLonger, vLonger);

        byte[] root = trie.getRootHash();

        Optional<byte[]> w1 = trie.getProofWire(kPrefix);
        assertTrue(w1.isPresent());
        assertTrue(trie.verifyProofWire(root, kPrefix, vPrefix, true, w1.get()));

        Optional<byte[]> w2 = trie.getProofWire(kLonger);
        assertTrue(w2.isPresent());
        assertTrue(trie.verifyProofWire(root, kLonger, vLonger, true, w2.get()));
    }

    @Test
    void non_inclusion_missing_branch_classic_wire() {
        TestNodeStore store = new TestNodeStore();
        MerklePatriciaTrie trie = new MerklePatriciaTrie(store, HF, Modes.classic(HF));

        byte[] k1 = hex("aa00");
        byte[] k2 = hex("aa02");
        trie.put(k1, b("one"));
        trie.put(k2, b("two"));

        byte[] root = trie.getRootHash();
        byte[] target = hex("aa01");

        byte[] wire = trie.getProofWire(target).orElseThrow();
        assertTrue(trie.verifyProofWire(root, target, null, false, wire));
        assertFalse(trie.verifyProofWire(root, target, b("x"), true, wire));
    }

    @Test
    void cross_mode_mismatch_throws() {
        TestNodeStore store = new TestNodeStore();
        // Classic trie
        MerklePatriciaTrie classicTrie = new MerklePatriciaTrie(store, HF, Modes.classic(HF));
        // MPF trie (default)
        MerklePatriciaTrie mpfTrie = new MerklePatriciaTrie(store, HF, Modes.mpf(HF));

        byte[] key = hex("ab01");
        byte[] val = b("v");
        classicTrie.put(key, val);
        mpfTrie.put(key, val);

        byte[] classicRoot = classicTrie.getRootHash();
        byte[] mpfRoot = mpfTrie.getRootHash();

        byte[] classicWire = classicTrie.getProofWire(key).orElseThrow();
        byte[] mpfWire = mpfTrie.getProofWire(key).orElseThrow();

        // MPF trie should reject Classic wire
        assertThrows(IllegalArgumentException.class, () -> mpfTrie.verifyProofWire(mpfRoot, key, val, true, classicWire));

        // Classic trie should reject MPF wire
        assertThrows(IllegalArgumentException.class, () -> classicTrie.verifyProofWire(classicRoot, key, val, true, mpfWire));
    }

    private static byte[] hex(String hex) {
        String s = hex.startsWith("0x") ? hex.substring(2) : hex;
        if ((s.length() & 1) == 1) s = "0" + s;
        int len = s.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(s.charAt(2 * i), 16);
            int lo = Character.digit(s.charAt(2 * i + 1), 16);
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static byte[] b(String s) {
        return s.getBytes();
    }
}
