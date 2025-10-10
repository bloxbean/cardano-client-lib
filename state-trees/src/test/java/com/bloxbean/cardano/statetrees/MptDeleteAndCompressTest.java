package com.bloxbean.cardano.statetrees;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.api.MerklePatriciaTrie;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MptDeleteAndCompressTest {
    private static final HashFunction HF = Blake2b256::digest;

    @Test
    void deleteCompressesToLeafOrExtension() {
        TestNodeStore store = new TestNodeStore();
        MerklePatriciaTrie trie = new MerklePatriciaTrie(store, HF);

        byte[] a = hex("ab00");
        byte[] b = hex("ab01");
        byte[] c = hex("ab10");

        trie.put(a, b("A"));
        trie.put(b, b("B"));
        trie.put(c, b("C"));

        byte[] rBefore = trie.getRootHash();
        assertNotNull(rBefore);

        trie.delete(c); // collapses a branch with one child
        byte[] rMid = trie.getRootHash();
        assertNotNull(rMid);
        assertNotEquals(bytesToStr(rBefore), bytesToStr(rMid));
        assertArrayEquals(b("A"), trie.get(a));
        assertArrayEquals(b("B"), trie.get(b));
        assertNull(trie.get(c));

        trie.delete(b);
        assertArrayEquals(b("A"), trie.get(a));
        assertNull(trie.get(b));
    }

    private static byte[] hex(String h) {
        String s = h.startsWith("0x") ? h.substring(2) : h;
        int len = s.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(s.charAt(2 * i), 16);
            int lo = Character.digit(s.charAt(2 * i + 1), 16);
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static String bytesToStr(byte[] b) {
        if (b == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static byte[] b(String s) {
        return s.getBytes();
    }
}

