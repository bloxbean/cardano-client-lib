package com.bloxbean.cardano.vds.mpf;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.mpf.test.TestNodeStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.SecureRandom;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for MPT implementation to establish baseline behavior
 * before refactoring. This ensures no regressions during the refactoring process.
 */
public class MpfComprehensiveTest {
    private static final HashFunction HASH_FN = Blake2b256::digest;
    private TestNodeStore store;
    private MpfTrie trie;
    private SecureRandom random;

    @BeforeEach
    void setUp() {
        store = new TestNodeStore();
        trie = new MpfTrie(store);
        random = new SecureRandom();
    }

    @Test
    void testEmptyTrieOperations() {
        // Empty trie should have null root
        assertNull(trie.getRootHash());

        // Get on empty trie should return null
        assertNull(trie.get(hex("aa")));

        // Delete on empty trie should not crash
        assertDoesNotThrow(() -> trie.delete(hex("aa")));

        // getAllEntries on empty trie should return empty list
        var results = trie.getAllEntries();
        assertTrue(results.isEmpty());
    }

    @Test
    void testSingleKeyOperations() {
        byte[] key = hex("abcd");
        byte[] value = b("test-value");

        // Put single key
        trie.put(key, value);
        assertNotNull(trie.getRootHash());
        assertArrayEquals(value, trie.get(key));

        // Update same key
        byte[] newValue = b("updated-value");
        trie.put(key, newValue);
        assertArrayEquals(newValue, trie.get(key));

        // Delete key
        trie.delete(key);
        assertNull(trie.getRootHash());
        assertNull(trie.get(key));
    }

    @Test
    void testMultipleKeyOperations() {
        Map<byte[], byte[]> testData = new HashMap<>();
        testData.put(hex("aa00"), b("value1"));
        testData.put(hex("aa01"), b("value2"));
        testData.put(hex("aa10"), b("value3"));
        testData.put(hex("bb00"), b("value4"));
        testData.put(hex("ccdd"), b("value5"));

        // Insert all keys
        for (var entry : testData.entrySet()) {
            trie.put(entry.getKey(), entry.getValue());
        }

        // Verify all keys are retrievable
        for (var entry : testData.entrySet()) {
            assertArrayEquals(entry.getValue(), trie.get(entry.getKey()),
                    "Failed to retrieve value for key: " + bytesToHex(entry.getKey()));
        }

        // Verify root hash is consistent
        byte[] rootHash1 = trie.getRootHash().clone();
        byte[] rootHash2 = trie.getRootHash().clone();
        assertArrayEquals(rootHash1, rootHash2);
    }

    @Test
    void testKeyOverwrites() {
        byte[] key = hex("aabbcc");

        trie.put(key, b("original"));
        assertArrayEquals(b("original"), trie.get(key));

        trie.put(key, b("updated"));
        assertArrayEquals(b("updated"), trie.get(key));

        trie.put(key, b("final"));
        assertArrayEquals(b("final"), trie.get(key));
    }

    // Note: Prefix scanning is not supported - MpfTrie hashes keys which destroys prefix relationships.
    // Aiken's merkle-patricia-forestry also does not support prefix scanning.

    @Test
    void testDeletionAndCompression() {
        // Create a tree structure that will require compression after deletion
        trie.put(hex("aa00"), b("value1"));
        trie.put(hex("aa01"), b("value2"));
        trie.put(hex("aa10"), b("value3"));

        byte[] rootBefore = trie.getRootHash().clone();

        // Delete one key, should trigger compression
        trie.delete(hex("aa10"));

        byte[] rootAfter = trie.getRootHash();
        assertFalse(Arrays.equals(rootBefore, rootAfter));

        // Verify remaining keys still accessible
        assertArrayEquals(b("value1"), trie.get(hex("aa00")));
        assertArrayEquals(b("value2"), trie.get(hex("aa01")));
        assertNull(trie.get(hex("aa10")));
    }

    @Test
    void testNullValuePrevention() {
        byte[] key = hex("test");

        // Null values should be rejected
        assertThrows(IllegalArgumentException.class, () -> trie.put(key, null));

        // Empty array should be allowed
        assertDoesNotThrow(() -> trie.put(key, new byte[0]));
        byte[] retrieved = trie.get(key);
        assertNotNull(retrieved, "Empty array should be retrievable, not null");
        assertArrayEquals(new byte[0], retrieved);
    }

    @Test
    void testRootHashConsistency() {
        Map<String, byte[]> operations = new HashMap<>();

        // Perform series of operations and track root hashes
        operations.put("step1", null);
        byte[] key1 = hex("aa");
        trie.put(key1, b("value1"));
        operations.put("step2", trie.getRootHash().clone());

        byte[] key2 = hex("bb");
        trie.put(key2, b("value2"));
        operations.put("step3", trie.getRootHash().clone());

        trie.delete(key1);
        operations.put("step4", trie.getRootHash().clone());

        trie.delete(key2);
        operations.put("step5", trie.getRootHash()); // Should be null

        // Verify all steps have different root hashes (except first and last)
        assertNull(operations.get("step1"));
        assertNotNull(operations.get("step2"));
        assertNotNull(operations.get("step3"));
        assertNotNull(operations.get("step4"));
        assertNull(operations.get("step5"));

        assertFalse(Arrays.equals(operations.get("step2"), operations.get("step3")));
        assertFalse(Arrays.equals(operations.get("step3"), operations.get("step4")));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 50, 100, 500})
    void testRandomOperations(int numOperations) {
        Map<String, byte[]> expectedState = new HashMap<>();

        for (int i = 0; i < numOperations; i++) {
            byte[] key = randomBytes(4);
            byte[] value = randomBytes(8);
            String keyStr = bytesToHex(key);

            trie.put(key, value);
            expectedState.put(keyStr, value);

            // Occasionally delete some keys
            if (i > 10 && random.nextDouble() < 0.1) {
                var keyToDelete = expectedState.keySet().stream()
                        .skip(random.nextInt(expectedState.size()))
                        .findFirst();

                if (keyToDelete.isPresent()) {
                    trie.delete(hexToBytes(keyToDelete.get()));
                    expectedState.remove(keyToDelete.get());
                }
            }
        }

        // Verify final state matches expectations
        for (var entry : expectedState.entrySet()) {
            byte[] actualValue = trie.get(hexToBytes(entry.getKey()));
            assertArrayEquals(entry.getValue(), actualValue,
                    "Mismatch for key: " + entry.getKey());
        }
    }

    @Test
    void testLargeValues() {
        byte[] key = hex("large-value-key");
        byte[] largeValue = new byte[1024 * 10]; // 10KB value
        random.nextBytes(largeValue);

        trie.put(key, largeValue);
        assertArrayEquals(largeValue, trie.get(key));
    }

    @Test
    void testKeyCollisionHandling() {
        // Test keys that share common prefixes but diverge at different points
        byte[] key1 = hex("aabbccdd");
        byte[] key2 = hex("aabbccde"); // Differs in last nibble
        byte[] key3 = hex("aabbcddd"); // Differs in 3rd-to-last nibble

        trie.put(key1, b("value1"));
        trie.put(key2, b("value2"));
        trie.put(key3, b("value3"));

        assertArrayEquals(b("value1"), trie.get(key1));
        assertArrayEquals(b("value2"), trie.get(key2));
        assertArrayEquals(b("value3"), trie.get(key3));
    }

    @Test
    void testTrieStatePersistence() {
        // Build a trie
        trie.put(hex("aa"), b("value1"));
        trie.put(hex("bb"), b("value2"));
        byte[] rootHash = trie.getRootHash();

        // Create new trie with same root
        MpfTrie trie2 = new MpfTrie(store, rootHash);

        // Should be able to access same data
        assertArrayEquals(b("value1"), trie2.get(hex("aa")));
        assertArrayEquals(b("value2"), trie2.get(hex("bb")));
        assertArrayEquals(rootHash, trie2.getRootHash());
    }

    // Helper methods
    private byte[] hex(String h) {
        String s = h.startsWith("0x") ? h.substring(2) : h;
        if (s.length() % 2 != 0) s = "0" + s;
        int len = s.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(s.charAt(2 * i), 16);
            int lo = Character.digit(s.charAt(2 * i + 1), 16);
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private byte[] hexToBytes(String hex) {
        return hex(hex);
    }

    private String bytesToHex(byte[] bytes) {
        if (bytes == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private byte[] b(String s) {
        return s.getBytes();
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }
}
