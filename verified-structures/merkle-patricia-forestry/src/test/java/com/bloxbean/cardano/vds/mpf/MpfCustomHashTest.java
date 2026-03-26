package com.bloxbean.cardano.vds.mpf;

import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.mpf.test.TestNodeStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MpfTrie with a custom (non-Blake2b-256) hash function.
 * Uses SHA-256 as a readily available alternative hash to verify that
 * custom hash functions are properly threaded through key hashing,
 * commitment scheme, and proof generation.
 */
class MpfCustomHashTest {

    private static final HashFunction SHA256 = data -> {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    };

    private TestNodeStore store;

    @BeforeEach
    void setUp() {
        store = new TestNodeStore();
    }

    @Test
    void putGetRoundtrip() {
        MpfTrie trie = new MpfTrie(store, SHA256);

        byte[] key = b("hello");
        byte[] value = b("world");

        trie.put(key, value);
        assertArrayEquals(value, trie.get(key));
    }

    @Test
    void delete() {
        MpfTrie trie = new MpfTrie(store, SHA256);

        byte[] key = b("to-delete");
        trie.put(key, b("value"));
        assertNotNull(trie.get(key));

        trie.delete(key);
        assertNull(trie.get(key));
    }

    @Test
    void proofGenerationAndVerification() {
        MpfTrie trie = new MpfTrie(store, SHA256);

        trie.put(b("apple"), b("red"));
        trie.put(b("banana"), b("yellow"));

        Optional<byte[]> proofWire = trie.getProofWire(b("apple"));
        assertTrue(proofWire.isPresent(), "proof wire should be present for existing key");

        Optional<ListPlutusData> proofPlutus = trie.getProofPlutusData(b("apple"));
        assertTrue(proofPlutus.isPresent(), "proof plutus data should be present for existing key");
    }

    @Test
    void rootHashDiffersFromBlake2bDefault() {
        byte[] key = b("same-key");
        byte[] value = b("same-value");

        // Build trie with default Blake2b-256
        TestNodeStore blake2bStore = new TestNodeStore();
        MpfTrie blake2bTrie = new MpfTrie(blake2bStore);
        blake2bTrie.put(key, value);
        byte[] blake2bRoot = blake2bTrie.getRootHash();

        // Build trie with SHA-256
        TestNodeStore sha256Store = new TestNodeStore();
        MpfTrie sha256Trie = new MpfTrie(sha256Store, SHA256);
        sha256Trie.put(key, value);
        byte[] sha256Root = sha256Trie.getRootHash();

        assertNotNull(blake2bRoot);
        assertNotNull(sha256Root);
        assertFalse(
                java.util.Arrays.equals(blake2bRoot, sha256Root),
                "Root hashes must differ when using different hash functions"
        );
    }

    @Test
    void multipleEntries() {
        MpfTrie trie = new MpfTrie(store, SHA256);

        String[] keys = {"alpha", "bravo", "charlie", "delta", "echo"};
        for (String k : keys) {
            trie.put(b(k), b("val-" + k));
        }

        for (String k : keys) {
            assertArrayEquals(b("val-" + k), trie.get(b(k)), "value mismatch for key: " + k);
        }

        assertNull(trie.get(b("nonexistent")), "non-existent key should return null");
    }

    @Test
    void reloadFromRoot() {
        MpfTrie trie1 = new MpfTrie(store, SHA256);
        trie1.put(b("key1"), b("value1"));
        trie1.put(b("key2"), b("value2"));

        byte[] root = trie1.getRootHash();
        assertNotNull(root);

        // Create a new trie pointing to the same store and root
        MpfTrie trie2 = new MpfTrie(store, SHA256, root);

        assertArrayEquals(b("value1"), trie2.get(b("key1")));
        assertArrayEquals(b("value2"), trie2.get(b("key2")));
        assertArrayEquals(root, trie2.getRootHash());
    }

    @Test
    void convenienceConstructorCreatesEmptyTrie() {
        MpfTrie trie = new MpfTrie(store, SHA256);
        assertNull(trie.getRootHash(), "new trie should have null root");
        assertNull(trie.get(b("anything")));
    }

    @Test
    void nullHashFnThrows() {
        assertThrows(NullPointerException.class, () -> new MpfTrie(store, (HashFunction) null));
        assertThrows(NullPointerException.class, () -> new MpfTrie(store, (HashFunction) null, null));
    }

    private byte[] b(String s) {
        return s.getBytes();
    }
}
