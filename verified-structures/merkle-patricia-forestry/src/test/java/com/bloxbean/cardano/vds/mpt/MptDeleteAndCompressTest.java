package com.bloxbean.cardano.vds.mpt;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.mpt.commitment.MpfCommitmentScheme;
import com.bloxbean.cardano.vds.mpt.test.TestNodeStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests delete and compression with raw (unhashed) keys.
 * Uses MpfTrieImpl directly since MpfTrie hashes keys.
 */
public class MptDeleteAndCompressTest {
    private static final HashFunction HF = Blake2b256::digest;

    @Test
    void deleteCompressesToLeafOrExtension() {
        TestNodeStore store = new TestNodeStore();
        MpfTrieImpl trie = new MpfTrieImpl(store, HF, null, new MpfCommitmentScheme(HF));

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
