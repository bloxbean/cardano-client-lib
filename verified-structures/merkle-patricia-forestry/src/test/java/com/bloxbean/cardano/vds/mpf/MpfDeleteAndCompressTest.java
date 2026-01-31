package com.bloxbean.cardano.vds.mpf;

import com.bloxbean.cardano.vds.mpf.test.TestNodeStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests delete and compression using MpfTrie public API.
 * Keys are automatically hashed by MpfTrie.
 */
public class MpfDeleteAndCompressTest {

    @Test
    void deleteCompressesToLeafOrExtension() {
        TestNodeStore store = new TestNodeStore();
        MpfTrie trie = new MpfTrie(store);

        // Use string keys - MpfTrie will hash them
        byte[] a = "key_a".getBytes();
        byte[] b = "key_b".getBytes();
        byte[] c = "key_c".getBytes();

        trie.put(a, b("A"));
        trie.put(b, b("B"));
        trie.put(c, b("C"));

        byte[] rBefore = trie.getRootHash();
        assertNotNull(rBefore);
        assertEquals(3, trie.computeSize());

        trie.delete(c); // collapses a branch with one child
        byte[] rMid = trie.getRootHash();
        assertNotNull(rMid);
        assertNotEquals(bytesToStr(rBefore), bytesToStr(rMid));
        assertArrayEquals(b("A"), trie.get(a));
        assertArrayEquals(b("B"), trie.get(b));
        assertNull(trie.get(c));
        assertEquals(2, trie.computeSize());

        trie.delete(b);
        assertArrayEquals(b("A"), trie.get(a));
        assertNull(trie.get(b));
        assertEquals(1, trie.computeSize());
    }

    @Test
    void deleteAllKeysResultsInEmptyTrie() {
        TestNodeStore store = new TestNodeStore();
        MpfTrie trie = new MpfTrie(store);

        trie.put("key1".getBytes(), b("value1"));
        trie.put("key2".getBytes(), b("value2"));

        assertNotNull(trie.getRootHash());

        trie.delete("key1".getBytes());
        trie.delete("key2".getBytes());

        assertNull(trie.getRootHash());
        assertEquals(0, trie.computeSize());
    }

    @Test
    void deleteNonExistentKeyDoesNothing() {
        TestNodeStore store = new TestNodeStore();
        MpfTrie trie = new MpfTrie(store);

        trie.put("existing".getBytes(), b("value"));
        byte[] rootBefore = trie.getRootHash().clone();

        trie.delete("nonexistent".getBytes());

        assertArrayEquals(rootBefore, trie.getRootHash());
        assertArrayEquals(b("value"), trie.get("existing".getBytes()));
    }

    private static String bytesToStr(byte[] bytes) {
        if (bytes == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (byte x : bytes) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static byte[] b(String s) {
        return s.getBytes();
    }
}
