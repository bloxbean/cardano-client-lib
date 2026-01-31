package com.bloxbean.cardano.vds.mpf;

import com.bloxbean.cardano.vds.mpf.test.TestNodeStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests put/get roundtrip using MpfTrie public API.
 * Keys are automatically hashed by MpfTrie.
 */
public class MpfRoundtripTest {

    @Test
    void putGetRoundtripDifferentKeys() {
        TestNodeStore store = new TestNodeStore();
        MpfTrie trie = new MpfTrie(store);

        // Use string keys - MpfTrie will hash them
        byte[] k1 = "key1".getBytes();
        byte[] k2 = "key2".getBytes();
        byte[] k3 = "key3".getBytes();

        trie.put(k1, b("A"));
        byte[] r1 = trie.getRootHash();
        assertNotNull(r1);
        assertArrayEquals(b("A"), trie.get(k1));

        trie.put(k2, b("B"));
        byte[] r2 = trie.getRootHash();
        assertNotNull(r2);
        assertNotEquals(bytesToStr(r1), bytesToStr(r2));
        assertArrayEquals(b("A"), trie.get(k1));
        assertArrayEquals(b("B"), trie.get(k2));

        trie.put(k3, b("C"));
        assertArrayEquals(b("C"), trie.get(k3));

        // Verify all entries can be retrieved
        var entries = trie.getAllEntries();
        assertEquals(3, entries.size());
    }

    @Test
    void putGetUpdateValue() {
        TestNodeStore store = new TestNodeStore();
        MpfTrie trie = new MpfTrie(store);

        byte[] key = "mykey".getBytes();

        trie.put(key, b("original"));
        assertArrayEquals(b("original"), trie.get(key));

        trie.put(key, b("updated"));
        assertArrayEquals(b("updated"), trie.get(key));

        // Only one entry should exist
        assertEquals(1, trie.getAllEntries().size());
    }

    @Test
    void getReturnsNullForMissingKey() {
        TestNodeStore store = new TestNodeStore();
        MpfTrie trie = new MpfTrie(store);

        trie.put("existing".getBytes(), b("value"));

        assertNull(trie.get("nonexistent".getBytes()));
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
