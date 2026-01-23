package com.bloxbean.cardano.vds.mpt;

import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.mpt.test.TestNodeStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the EntriesCollectorVisitor and TreePrinterVisitor.
 * Tests the getAllEntries() and printTree() methods exposed through MpfTrie.
 */
class TraversalVisitorsTest {

    private TestNodeStore store;
    private MpfTrie trie;

    @BeforeEach
    void setUp() {
        store = new TestNodeStore();
        trie = new MpfTrie(store);
    }

    // ===== getAllEntries() tests =====

    @Test
    void getAllEntries_emptyTrie_returnsEmptyList() {
        List<MpfTrie.Entry> entries = trie.getAllEntries();

        assertNotNull(entries);
        assertTrue(entries.isEmpty());
    }

    @Test
    void getAllEntries_singleEntry_returnsOneEntry() {
        byte[] key = b("key1");
        byte[] value = b("value1");
        trie.put(key, value);

        List<MpfTrie.Entry> entries = trie.getAllEntries();

        assertEquals(1, entries.size());
        assertArrayEquals(value, entries.get(0).getValue());
    }

    @Test
    void getAllEntries_multipleEntries_returnsAllEntries() {
        trie.put(b("key1"), b("value1"));
        trie.put(b("key2"), b("value2"));
        trie.put(b("key3"), b("value3"));

        List<MpfTrie.Entry> entries = trie.getAllEntries();

        assertEquals(3, entries.size());

        // Collect all values to verify
        Set<String> values = new HashSet<>();
        for (MpfTrie.Entry entry : entries) {
            values.add(new String(entry.getValue()));
        }

        assertTrue(values.contains("value1"));
        assertTrue(values.contains("value2"));
        assertTrue(values.contains("value3"));
    }

    @Test
    void getAllEntries_entriesWithSharedPrefix_returnsAll() {
        // Keys that will have shared prefix when hashed
        trie.put(b("abc"), b("value-abc"));
        trie.put(b("abd"), b("value-abd"));
        trie.put(b("xyz"), b("value-xyz"));

        List<MpfTrie.Entry> entries = trie.getAllEntries();

        assertEquals(3, entries.size());

        Set<String> values = new HashSet<>();
        for (MpfTrie.Entry entry : entries) {
            values.add(new String(entry.getValue()));
        }

        assertTrue(values.contains("value-abc"));
        assertTrue(values.contains("value-abd"));
        assertTrue(values.contains("value-xyz"));
    }

    @Test
    void getAllEntries_afterDeletion_returnsRemainingEntries() {
        trie.put(b("key1"), b("value1"));
        trie.put(b("key2"), b("value2"));
        trie.put(b("key3"), b("value3"));

        trie.delete(b("key2"));

        List<MpfTrie.Entry> entries = trie.getAllEntries();

        assertEquals(2, entries.size());

        Set<String> values = new HashSet<>();
        for (MpfTrie.Entry entry : entries) {
            values.add(new String(entry.getValue()));
        }

        assertTrue(values.contains("value1"));
        assertFalse(values.contains("value2"));
        assertTrue(values.contains("value3"));
    }

    @Test
    void getAllEntries_largeNumberOfEntries_returnsAll() {
        int numEntries = 100;
        for (int i = 0; i < numEntries; i++) {
            trie.put(b("key" + i), b("value" + i));
        }

        List<MpfTrie.Entry> entries = trie.getAllEntries();

        assertEquals(numEntries, entries.size());
    }

    @Test
    void getAllEntries_valuesAreCorrect() {
        byte[] key1 = b("hello");
        byte[] value1 = hex("deadbeef");
        byte[] key2 = b("world");
        byte[] value2 = hex("cafebabe");

        trie.put(key1, value1);
        trie.put(key2, value2);

        List<MpfTrie.Entry> entries = trie.getAllEntries();

        assertEquals(2, entries.size());

        // Check that both values are present
        boolean foundValue1 = false;
        boolean foundValue2 = false;
        for (MpfTrie.Entry entry : entries) {
            if (Arrays.equals(value1, entry.getValue())) foundValue1 = true;
            if (Arrays.equals(value2, entry.getValue())) foundValue2 = true;
        }

        assertTrue(foundValue1, "Expected to find value1");
        assertTrue(foundValue2, "Expected to find value2");
    }

    // ===== getEntries(limit) tests =====

    @Test
    void getEntries_withLimit_returnsExactCount() {
        // Insert 10 entries
        for (int i = 0; i < 10; i++) {
            trie.put(b("key" + i), b("value" + i));
        }

        // Request only 5 entries
        List<MpfTrie.Entry> entries = trie.getEntries(5);

        assertEquals(5, entries.size());
    }

    @Test
    void getEntries_limitExceedsTotal_returnsAllEntries() {
        // Insert 5 entries
        for (int i = 0; i < 5; i++) {
            trie.put(b("key" + i), b("value" + i));
        }

        // Request 100 entries (more than available)
        List<MpfTrie.Entry> entries = trie.getEntries(100);

        assertEquals(5, entries.size());
    }

    @Test
    void getEntries_emptyTrie_returnsEmptyList() {
        List<MpfTrie.Entry> entries = trie.getEntries(10);

        assertNotNull(entries);
        assertTrue(entries.isEmpty());
    }

    @Test
    void getEntries_limitOne_returnsSingleEntry() {
        trie.put(b("key1"), b("value1"));
        trie.put(b("key2"), b("value2"));
        trie.put(b("key3"), b("value3"));

        List<MpfTrie.Entry> entries = trie.getEntries(1);

        assertEquals(1, entries.size());
    }

    @Test
    void getEntries_invalidLimit_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> trie.getEntries(0));
        assertThrows(IllegalArgumentException.class, () -> trie.getEntries(-1));
        assertThrows(IllegalArgumentException.class, () -> trie.getEntries(-100));
    }

    @Test
    void getEntries_largeNumberWithSmallLimit_returnsLimitedEntries() {
        // Insert 100 entries
        int numEntries = 100;
        for (int i = 0; i < numEntries; i++) {
            trie.put(b("entry-" + i), b("data-" + i));
        }

        // Request various limits
        assertEquals(10, trie.getEntries(10).size());
        assertEquals(25, trie.getEntries(25).size());
        assertEquals(50, trie.getEntries(50).size());
        assertEquals(100, trie.getEntries(100).size());
        assertEquals(100, trie.getEntries(200).size()); // exceeds total
    }

    @Test
    void getEntries_withOriginalKeyStorage_preservesOriginalKeys() {
        TestNodeStore storeWithKeys = new TestNodeStore();
        MpfTrie trieWithKeys = MpfTrie.withOriginalKeyStorage(storeWithKeys);

        for (int i = 0; i < 10; i++) {
            trieWithKeys.put(b("key" + i), b("value" + i));
        }

        List<MpfTrie.Entry> entries = trieWithKeys.getEntries(5);

        assertEquals(5, entries.size());
        for (MpfTrie.Entry entry : entries) {
            assertNotNull(entry.getOriginalKey(), "Original key should be preserved");
        }
    }

    @Test
    void getEntries_afterDeletion_returnsCorrectLimitedEntries() {
        // Insert 10 entries
        for (int i = 0; i < 10; i++) {
            trie.put(b("key" + i), b("value" + i));
        }

        // Delete 3 entries
        trie.delete(b("key0"));
        trie.delete(b("key5"));
        trie.delete(b("key9"));

        // Should have 7 entries total
        assertEquals(7, trie.getAllEntries().size());

        // Request 5 entries
        List<MpfTrie.Entry> entries = trie.getEntries(5);
        assertEquals(5, entries.size());

        // Request all remaining
        entries = trie.getEntries(100);
        assertEquals(7, entries.size());
    }

    // ===== printTree() tests =====

    @Test
    void printTree_emptyTrie_showsEmptyMessage() {
        String tree = trie.printTree();

        assertEquals("(empty trie)", tree);
    }

    @Test
    void printTree_singleEntry_showsLeafNode() {
        trie.put(b("test"), b("value"));

        String tree = trie.printTree();

        assertNotNull(tree);
        assertTrue(tree.startsWith("Root: 0x"), "Should show root hash");
        assertTrue(tree.contains("[Leaf]"), "Should contain Leaf node");
        assertTrue(tree.contains("path="), "Should show path");
        assertTrue(tree.contains("value="), "Should show value");
    }

    @Test
    void printTree_multipleEntries_showsAllNodeTypes() {
        // Insert enough entries to create a more complex tree
        trie.put(b("abc"), b("val1"));
        trie.put(b("abd"), b("val2"));
        trie.put(b("xyz"), b("val3"));

        System.out.println(HexUtil.encodeHexString(Blake2b256.digest(b("abc"))));
        System.out.println(HexUtil.encodeHexString(Blake2b256.digest(b("abd"))));
        System.out.println(HexUtil.encodeHexString(Blake2b256.digest(b("xyz"))));

        String tree = trie.printTree();

        assertNotNull(tree);
        assertTrue(tree.startsWith("Root: 0x"), "Should show root hash");
        // Should have multiple [Leaf] entries
        assertTrue(tree.contains("[Leaf]"), "Should contain Leaf nodes");
        assertTrue(tree.contains("value=0x"), "Should show values as hex");
    }

    @Test
    void printTree_showsRootHash() {
        trie.put(b("key"), b("value"));

        String tree = trie.printTree();
        byte[] rootHash = trie.getRootHash();
        String rootHex = HexUtil.encodeHexString(rootHash);

        assertTrue(tree.contains(rootHex), "Tree should contain full root hash");
    }

    @Test
    void printTree_valuesDisplayedAsHex() {
        byte[] value = hex("48656c6c6f"); // "Hello" in hex
        trie.put(b("key"), value);

        String tree = trie.printTree();

        // The tree should show the value as hex
        assertTrue(tree.contains("0x48656c6c6f") || tree.contains("0x48656c.."),
                "Value should be displayed as hex");
    }

    @Test
    void printTree_largeTree_doesNotFail() {
        // Create a larger tree
        for (int i = 0; i < 50; i++) {
            trie.put(b("key" + i), b("value" + i));
        }

        String tree = trie.printTree();

        assertNotNull(tree);
        assertFalse(tree.isEmpty());
        assertTrue(tree.startsWith("Root: 0x"));
    }

    @Test
    void printTree_consistentAfterUpdate() {
        trie.put(b("key1"), b("value1"));
        String tree1 = trie.printTree();

        // Update the same key
        trie.put(b("key1"), b("value2"));
        String tree2 = trie.printTree();

        // Trees should be different due to different values
        assertNotEquals(tree1, tree2, "Tree should change after value update");

        // Both should still be valid
        assertTrue(tree1.contains("[Leaf]"));
        assertTrue(tree2.contains("[Leaf]"));
    }

    @Test
    void printTree_afterDelete_showsRemainingStructure() {
        trie.put(b("key1"), b("value1"));
        trie.put(b("key2"), b("value2"));

        trie.delete(b("key1"));

        String tree = trie.printTree();

        assertNotNull(tree);
        assertTrue(tree.startsWith("Root: 0x"));
        // Should still show the remaining leaf
        assertTrue(tree.contains("[Leaf]"));
    }

    @Test
    void printTree_afterAllDeleted_showsEmpty() {
        trie.put(b("key1"), b("value1"));
        trie.delete(b("key1"));

        String tree = trie.printTree();

        assertEquals("(empty trie)", tree);
    }

    // ===== Original Key Storage Tests =====

    @Test
    void withOriginalKeyStorage_singleEntry_storesOriginalKey() {
        TestNodeStore storeWithKeys = new TestNodeStore();
        MpfTrie trieWithKeys = MpfTrie.withOriginalKeyStorage(storeWithKeys);

        byte[] key = b("hello");
        byte[] value = b("world");
        trieWithKeys.put(key, value);

        List<MpfTrie.Entry> entries = trieWithKeys.getAllEntries();

        assertEquals(1, entries.size());
        assertArrayEquals(value, entries.get(0).getValue());
        assertArrayEquals(key, entries.get(0).getOriginalKey());
    }

    @Test
    void withOriginalKeyStorage_multipleEntries_storesAllOriginalKeys() {
        TestNodeStore storeWithKeys = new TestNodeStore();
        MpfTrie trieWithKeys = MpfTrie.withOriginalKeyStorage(storeWithKeys);

        trieWithKeys.put(b("key1"), b("value1"));
        trieWithKeys.put(b("key2"), b("value2"));
        trieWithKeys.put(b("key3"), b("value3"));

        List<MpfTrie.Entry> entries = trieWithKeys.getAllEntries();

        assertEquals(3, entries.size());

        // Collect all original keys to verify
        Set<String> originalKeys = new HashSet<>();
        for (MpfTrie.Entry entry : entries) {
            assertNotNull(entry.getOriginalKey(), "Original key should not be null");
            originalKeys.add(new String(entry.getOriginalKey()));
        }

        assertTrue(originalKeys.contains("key1"));
        assertTrue(originalKeys.contains("key2"));
        assertTrue(originalKeys.contains("key3"));
    }

    @Test
    void withoutOriginalKeyStorage_originalKeyIsNull() {
        // Default constructor should not store original keys
        List<MpfTrie.Entry> entries;

        trie.put(b("hello"), b("world"));
        entries = trie.getAllEntries();

        assertEquals(1, entries.size());
        assertNull(entries.get(0).getOriginalKey(), "Original key should be null when not storing");
    }

    @Test
    void rootHashUnchanged_withOrWithoutOriginalKeyStorage() {
        // Create two tries with same data - one with original key storage, one without
        TestNodeStore store1 = new TestNodeStore();
        TestNodeStore store2 = new TestNodeStore();

        MpfTrie trieWithout = new MpfTrie(store1);
        MpfTrie trieWith = MpfTrie.withOriginalKeyStorage(store2);

        // Insert same data
        trieWithout.put(b("hello"), b("world"));
        trieWith.put(b("hello"), b("world"));

        // Root hashes should be identical!
        assertArrayEquals(trieWithout.getRootHash(), trieWith.getRootHash(),
                "Root hash should be identical with or without original key storage");
    }

    @Test
    void rootHashUnchanged_multipleEntries() {
        TestNodeStore store1 = new TestNodeStore();
        TestNodeStore store2 = new TestNodeStore();

        MpfTrie trieWithout = new MpfTrie(store1);
        MpfTrie trieWith = MpfTrie.withOriginalKeyStorage(store2);

        // Insert same data
        String[] keys = {"apple", "banana", "cherry", "date", "elderberry"};
        for (String key : keys) {
            trieWithout.put(b(key), b("value-" + key));
            trieWith.put(b(key), b("value-" + key));
        }

        // Root hashes should be identical!
        assertArrayEquals(trieWithout.getRootHash(), trieWith.getRootHash(),
                "Root hash should be identical with or without original key storage");

        // Verify original keys are accessible
        List<MpfTrie.Entry> entries = trieWith.getAllEntries();
        assertEquals(keys.length, entries.size());

        Set<String> foundKeys = new HashSet<>();
        for (MpfTrie.Entry entry : entries) {
            assertNotNull(entry.getOriginalKey());
            foundKeys.add(new String(entry.getOriginalKey()));
        }

        for (String key : keys) {
            assertTrue(foundKeys.contains(key));
        }
    }

    @Test
    void originalKeyStorage_updatePreservesOriginalKey() {
        TestNodeStore storeWithKeys = new TestNodeStore();
        MpfTrie trieWithKeys = MpfTrie.withOriginalKeyStorage(storeWithKeys);

        byte[] key = b("mykey");
        trieWithKeys.put(key, b("value1"));
        trieWithKeys.put(key, b("value2")); // Update

        List<MpfTrie.Entry> entries = trieWithKeys.getAllEntries();

        assertEquals(1, entries.size());
        assertArrayEquals(b("value2"), entries.get(0).getValue());
        assertArrayEquals(key, entries.get(0).getOriginalKey());
    }

    @Test
    void originalKeyStorage_afterDelete_remainingEntriesHaveOriginalKeys() {
        TestNodeStore storeWithKeys = new TestNodeStore();
        MpfTrie trieWithKeys = MpfTrie.withOriginalKeyStorage(storeWithKeys);

        trieWithKeys.put(b("key1"), b("value1"));
        trieWithKeys.put(b("key2"), b("value2"));
        trieWithKeys.put(b("key3"), b("value3"));

        trieWithKeys.delete(b("key2"));

        List<MpfTrie.Entry> entries = trieWithKeys.getAllEntries();

        assertEquals(2, entries.size());

        Set<String> originalKeys = new HashSet<>();
        for (MpfTrie.Entry entry : entries) {
            assertNotNull(entry.getOriginalKey());
            originalKeys.add(new String(entry.getOriginalKey()));
        }

        assertTrue(originalKeys.contains("key1"));
        assertFalse(originalKeys.contains("key2"));
        assertTrue(originalKeys.contains("key3"));
    }

    @Test
    void constructorWithFlag_storeOriginalKeysFalse() {
        TestNodeStore testStore = new TestNodeStore();
        MpfTrie trieNoKeys = new MpfTrie(testStore, null, false);

        trieNoKeys.put(b("test"), b("value"));

        List<MpfTrie.Entry> entries = trieNoKeys.getAllEntries();
        assertEquals(1, entries.size());
        assertNull(entries.get(0).getOriginalKey());
    }

    @Test
    void constructorWithFlag_storeOriginalKeysTrue() {
        TestNodeStore testStore = new TestNodeStore();
        MpfTrie trieWithKeys = new MpfTrie(testStore, null, true);

        trieWithKeys.put(b("test"), b("value"));

        List<MpfTrie.Entry> entries = trieWithKeys.getAllEntries();
        assertEquals(1, entries.size());
        assertArrayEquals(b("test"), entries.get(0).getOriginalKey());
    }

    @Test
    void withOriginalKeyStorageExistingRoot_loadsAndStoresNewKeys() {
        // Create a trie without original key storage and get root
        TestNodeStore sharedStore = new TestNodeStore();
        MpfTrie trie1 = new MpfTrie(sharedStore);
        trie1.put(b("existing"), b("existingValue"));
        byte[] root = trie1.getRootHash();

        // Load the same trie with original key storage enabled
        MpfTrie trie2 = MpfTrie.withOriginalKeyStorage(sharedStore, root);

        // Add new key - should have original key
        trie2.put(b("newKey"), b("newValue"));

        List<MpfTrie.Entry> entries = trie2.getAllEntries();
        assertEquals(2, entries.size());

        // Find the new entry
        for (MpfTrie.Entry entry : entries) {
            if (Arrays.equals(b("newValue"), entry.getValue())) {
                assertArrayEquals(b("newKey"), entry.getOriginalKey(),
                        "New entry should have original key");
            }
        }
    }

    @Test
    void backwardCompatibility_readsOldFormatWithoutOriginalKey() {
        // Create entries without original key storage
        TestNodeStore sharedStore = new TestNodeStore();
        MpfTrie oldTrie = new MpfTrie(sharedStore);
        oldTrie.put(b("legacy"), b("legacyValue"));
        byte[] root = oldTrie.getRootHash();

        // Load with new trie (even with original key storage flag)
        MpfTrie newTrie = MpfTrie.withOriginalKeyStorage(sharedStore, root);

        List<MpfTrie.Entry> entries = newTrie.getAllEntries();
        assertEquals(1, entries.size());
        assertArrayEquals(b("legacyValue"), entries.get(0).getValue());
        // Old entries won't have original key
        assertNull(entries.get(0).getOriginalKey());
    }

    // ===== getTreeStructure() tests =====

    @Test
    void getTreeStructure_emptyTrie_returnsNull() {
        TreeNode structure = trie.getTreeStructure();
        assertNull(structure);
    }

    @Test
    void getTreeStructure_singleEntry_returnsLeafNode() {
        trie.put(b("hello"), b("world"));

        TreeNode structure = trie.getTreeStructure();

        assertNotNull(structure);
        assertInstanceOf(TreeNode.LeafTreeNode.class, structure);

        TreeNode.LeafTreeNode leaf = (TreeNode.LeafTreeNode) structure;
        assertNotNull(leaf.getPath());
        assertEquals("776f726c64", leaf.getValue()); // "world" in hex
        assertEquals("leaf", leaf.getType());
    }

    @Test
    void getTreeStructure_multipleEntries_returnsBranchStructure() {
        trie.put(b("abc"), b("val1"));
        trie.put(b("abd"), b("val2"));
        trie.put(b("xyz"), b("val3"));

        TreeNode structure = trie.getTreeStructure();

        assertNotNull(structure);
        // With multiple entries, root should be a branch node
        assertInstanceOf(TreeNode.BranchTreeNode.class, structure);

        TreeNode.BranchTreeNode branch = (TreeNode.BranchTreeNode) structure;
        assertEquals("branch", branch.getType());
        assertNotNull(branch.getChildren());
        assertEquals(16, branch.getChildren().size());
    }

    @Test
    void getTreeStructure_withOriginalKeyStorage_includesOriginalKey() {
        TestNodeStore storeWithKeys = new TestNodeStore();
        MpfTrie trieWithKeys = MpfTrie.withOriginalKeyStorage(storeWithKeys);

        trieWithKeys.put(b("hello"), b("world"));

        TreeNode structure = trieWithKeys.getTreeStructure();

        assertNotNull(structure);
        assertInstanceOf(TreeNode.LeafTreeNode.class, structure);

        TreeNode.LeafTreeNode leaf = (TreeNode.LeafTreeNode) structure;
        assertNotNull(leaf.getOriginalKey());
        assertEquals("68656c6c6f", leaf.getOriginalKey()); // "hello" in hex
    }

    @Test
    void getTreeStructure_withoutOriginalKeyStorage_originalKeyIsNull() {
        trie.put(b("hello"), b("world"));

        TreeNode structure = trie.getTreeStructure();

        assertNotNull(structure);
        assertInstanceOf(TreeNode.LeafTreeNode.class, structure);

        TreeNode.LeafTreeNode leaf = (TreeNode.LeafTreeNode) structure;
        assertNull(leaf.getOriginalKey());
    }

    // ===== printTreeJson() tests =====

    @Test
    void printTreeJson_emptyTrie_returnsNullString() {
        String json = trie.printTreeJson();

        assertEquals("null", json);
    }

    @Test
    void printTreeJson_singleEntry_containsTypeAndValue() {
        trie.put(b("test"), b("value"));

        String json = trie.printTreeJson();

        assertNotNull(json);
        assertTrue(json.contains("\"type\""));
        assertTrue(json.contains("\"leaf\""));
        assertTrue(json.contains("\"path\""));
        assertTrue(json.contains("\"value\""));
    }

    @Test
    void printTreeJson_multipleEntries_containsBranchStructure() {
        trie.put(b("abc"), b("val1"));
        trie.put(b("abd"), b("val2"));
        trie.put(b("xyz"), b("val3"));

        String json = trie.printTreeJson();

        assertNotNull(json);
        assertTrue(json.contains("\"type\" : \"branch\""));
        assertTrue(json.contains("\"children\""));
        assertTrue(json.contains("\"hash\""));
    }

    @Test
    void printTreeJson_withOriginalKey_includesOriginalKeyField() {
        TestNodeStore storeWithKeys = new TestNodeStore();
        MpfTrie trieWithKeys = MpfTrie.withOriginalKeyStorage(storeWithKeys);

        trieWithKeys.put(b("hello"), b("world"));

        String json = trieWithKeys.printTreeJson();

        assertNotNull(json);
        assertTrue(json.contains("\"originalKey\""));
        assertTrue(json.contains("68656c6c6f")); // "hello" in hex
    }

    @Test
    void printTreeJson_isValidJson() {
        trie.put(b("key1"), b("value1"));
        trie.put(b("key2"), b("value2"));

        String json = trie.printTreeJson();

        assertNotNull(json);
        // Basic JSON structure validation
        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));
        assertTrue(json.contains(":"));
    }

    @Test
    void printTreeJson_largeTree_doesNotFail() {
        for (int i = 0; i < 50; i++) {
            trie.put(b("key" + i), b("value" + i));
        }

        String json = trie.printTreeJson();

        assertNotNull(json);
        assertFalse(json.isEmpty());
        assertTrue(json.contains("\"type\""));
    }

    @Test
    void printTreeJson_afterDelete_showsRemainingStructure() {
        trie.put(b("key1"), b("value1"));
        trie.put(b("key2"), b("value2"));

        trie.delete(b("key1"));

        String json = trie.printTreeJson();

        assertNotNull(json);
        assertTrue(json.contains("\"type\" : \"leaf\""));
    }

    @Test
    void printTreeJson_branchWithExtension_showsExtensionNode() {
        // Insert keys that will create extension nodes due to shared prefixes
        trie.put(b("aaaa"), b("val1"));
        trie.put(b("aaab"), b("val2"));

        String json = trie.printTreeJson();

        assertNotNull(json);
        assertTrue(json.contains("\"type\""));
        // The structure should contain various node types
        assertTrue(json.contains("\"path\"") || json.contains("\"children\""));
    }

    @Test
    void treeNode_toJson_withNullNode_returnsNullString() {
        String json = TreeNode.toJson(null);
        assertEquals("null", json);
    }

    @Test
    void treeNode_leafTreeNode_hasCorrectType() {
        TreeNode.LeafTreeNode leaf = new TreeNode.LeafTreeNode(
                new int[]{1, 2, 3},
                "deadbeef",
                "hello"
        );

        assertEquals("leaf", leaf.getType());
        assertArrayEquals(new int[]{1, 2, 3}, leaf.getPath());
        assertEquals("deadbeef", leaf.getValue());
        assertEquals("hello", leaf.getOriginalKey());
    }

    @Test
    void treeNode_branchTreeNode_hasCorrectType() {
        TreeNode.BranchTreeNode branch = new TreeNode.BranchTreeNode(
                "abc123",
                null,
                new java.util.LinkedHashMap<>()
        );

        assertEquals("branch", branch.getType());
        assertEquals("abc123", branch.getHash());
        assertNull(branch.getValue());
        assertNotNull(branch.getChildren());
    }

    @Test
    void treeNode_extensionTreeNode_hasCorrectType() {
        TreeNode.ExtensionTreeNode extension = new TreeNode.ExtensionTreeNode(
                "def456",
                new int[]{4, 5, 6},
                null
        );

        assertEquals("extension", extension.getType());
        assertEquals("def456", extension.getHash());
        assertArrayEquals(new int[]{4, 5, 6}, extension.getPath());
        assertNull(extension.getChild());
    }

    // ===== getStatistics() and size() tests =====

    @Test
    void getStatistics_emptyTrie_returnsZeroCounts() {
        TrieStatistics stats = trie.getStatistics();

        assertNotNull(stats);
        assertEquals(0, stats.getEntryCount());
        assertEquals(0, stats.getBranchCount());
        assertEquals(0, stats.getExtensionCount());
        assertEquals(0, stats.getMaxDepth());
        assertEquals(0, stats.totalNodes());
    }

    @Test
    void getStatistics_singleEntry_returnsEntryCountOne() {
        trie.put(b("hello"), b("world"));

        TrieStatistics stats = trie.getStatistics();

        assertEquals(1, stats.getEntryCount());
        assertEquals(0, stats.getBranchCount());
        // Single entry: no branch or extension nodes needed
        assertEquals(1, stats.totalNodes());
    }

    @Test
    void getStatistics_multipleEntries_returnsCorrectEntryCount() {
        trie.put(b("key1"), b("value1"));
        trie.put(b("key2"), b("value2"));
        trie.put(b("key3"), b("value3"));

        TrieStatistics stats = trie.getStatistics();

        assertEquals(3, stats.getEntryCount());
        // With multiple entries, we should have branch nodes
        assertTrue(stats.getBranchCount() > 0, "Should have branch nodes");
        assertTrue(stats.totalNodes() >= 3, "Total nodes should be at least entry count");
    }

    @Test
    void getStatistics_matchesGetAllEntriesSize() {
        // Insert random entries
        for (int i = 0; i < 20; i++) {
            trie.put(b("key-" + i), b("value-" + i));
        }

        TrieStatistics stats = trie.getStatistics();
        List<MpfTrie.Entry> entries = trie.getAllEntries();

        assertEquals(entries.size(), stats.getEntryCount(),
                "Entry count from statistics should match getAllEntries().size()");
    }

    @Test
    void getStatistics_afterDeletion_updatesCorrectly() {
        trie.put(b("key1"), b("value1"));
        trie.put(b("key2"), b("value2"));
        trie.put(b("key3"), b("value3"));

        assertEquals(3, trie.getStatistics().getEntryCount());

        trie.delete(b("key2"));

        assertEquals(2, trie.getStatistics().getEntryCount());
    }

    @Test
    void getStatistics_largeTree_returnsCorrectCount() {
        int numEntries = 100;
        for (int i = 0; i < numEntries; i++) {
            trie.put(b("entry-" + i), b("data-" + i));
        }

        TrieStatistics stats = trie.getStatistics();

        assertEquals(numEntries, stats.getEntryCount());
        assertTrue(stats.getBranchCount() > 0);
        assertTrue(stats.getMaxDepth() > 0);
    }

    @Test
    void getStatistics_includesBranchAndExtensionCounts() {
        // Insert entries that create a complex tree structure
        trie.put(b("abc"), b("val1"));
        trie.put(b("abd"), b("val2"));
        trie.put(b("xyz"), b("val3"));

        TrieStatistics stats = trie.getStatistics();

        assertEquals(3, stats.getEntryCount());
        assertTrue(stats.totalNodes() > stats.getEntryCount(),
                "Total nodes should include internal nodes (branches/extensions)");
    }

    @Test
    void getStatistics_maxDepthIsPositive_forNonEmptyTrie() {
        trie.put(b("test"), b("value"));

        TrieStatistics stats = trie.getStatistics();

        assertTrue(stats.getMaxDepth() > 0, "Max depth should be > 0 for non-empty trie");
        // MpfTrie hashes keys to 32 bytes = 64 nibbles, so max depth should be 64
        assertEquals(64, stats.getMaxDepth(),
                "With Blake2b-256 hashing, all keys end at depth 64");
    }

    @Test
    void trieStatistics_toString_containsAllFields() {
        trie.put(b("key1"), b("value1"));
        trie.put(b("key2"), b("value2"));

        TrieStatistics stats = trie.getStatistics();
        String str = stats.toString();

        assertTrue(str.contains("entryCount="));
        assertTrue(str.contains("branchCount="));
        assertTrue(str.contains("extensionCount="));
        assertTrue(str.contains("maxDepth="));
        assertTrue(str.contains("totalNodes="));
    }

    @Test
    void trieStatistics_equals_sameStats() {
        TrieStatistics stats1 = new TrieStatistics(5, 3, 2, 10);
        TrieStatistics stats2 = new TrieStatistics(5, 3, 2, 10);

        assertEquals(stats1, stats2);
        assertEquals(stats1.hashCode(), stats2.hashCode());
    }

    @Test
    void trieStatistics_equals_differentStats() {
        TrieStatistics stats1 = new TrieStatistics(5, 3, 2, 10);
        TrieStatistics stats2 = new TrieStatistics(5, 4, 2, 10);

        assertNotEquals(stats1, stats2);
    }

    @Test
    void trieStatistics_empty_isSingleton() {
        TrieStatistics empty1 = TrieStatistics.empty();
        TrieStatistics empty2 = TrieStatistics.empty();

        assertSame(empty1, empty2, "Empty statistics should be cached");
    }

    @Test
    void trieStatistics_totalNodes_sumOfAllNodeTypes() {
        TrieStatistics stats = new TrieStatistics(10, 5, 3, 20);

        assertEquals(18, stats.totalNodes());
    }

    // ===== Helper methods =====

    private static byte[] b(String s) {
        return s.getBytes();
    }

    private static byte[] hex(String hexString) {
        return HexUtil.decodeHexString(hexString);
    }
}
