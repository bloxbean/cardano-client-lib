package com.bloxbean.cardano.statetrees;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.api.MerklePatriciaTrie;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MptRoundtripTest {
    private static final HashFunction HF = Blake2b256::digest;

    @Test
    void putGetRoundtripDifferentKeys() {
        TestNodeStore store = new TestNodeStore();
        MerklePatriciaTrie trie = new MerklePatriciaTrie(store, HF);

        byte[] k1 = hex("0a0b0c");
        byte[] k2 = hex("0a0b0d");
        byte[] k3 = hex("ffff");

        trie.put(k1, b("A"));
        byte[] r1 = trie.getRootHash();
        assertArrayEquals(b("A"), trie.get(k1));

        trie.put(k2, b("B"));
        byte[] r2 = trie.getRootHash();
        assertNotNull(r2);
        assertNotEquals(bytesToStr(r1), bytesToStr(r2));
        assertArrayEquals(b("A"), trie.get(k1));
        assertArrayEquals(b("B"), trie.get(k2));

        trie.put(k3, b("C"));
        assertArrayEquals(b("C"), trie.get(k3));
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

