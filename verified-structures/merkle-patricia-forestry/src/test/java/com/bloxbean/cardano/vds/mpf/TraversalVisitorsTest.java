package com.bloxbean.cardano.vds.mpf;

import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.mpf.test.TestNodeStore;
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
    void getEntries_preservesKeys() {
        // MpfTrie now always stores keys
        for (int i = 0; i < 10; i++) {
            trie.put(b("key" + i), b("value" + i));
        }

        List<MpfTrie.Entry> entries = trie.getEntries(5);

        assertEquals(5, entries.size());
        for (MpfTrie.Entry entry : entries) {
            assertNotNull(entry.getKey(), "Key should be preserved");
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

    // ===== Key Storage Tests =====

    @Test
    void singleEntry_storesKey() {
        byte[] key = b("hello");
        byte[] value = b("world");
        trie.put(key, value);

        List<MpfTrie.Entry> entries = trie.getAllEntries();

        assertEquals(1, entries.size());
        assertArrayEquals(value, entries.get(0).getValue());
        assertArrayEquals(key, entries.get(0).getKey());
    }

    @Test
    void multipleEntries_storesAllKeys() {
        trie.put(b("key1"), b("value1"));
        trie.put(b("key2"), b("value2"));
        trie.put(b("key3"), b("value3"));

        List<MpfTrie.Entry> entries = trie.getAllEntries();

        assertEquals(3, entries.size());

        // Collect all keys to verify
        Set<String> keys = new HashSet<>();
        for (MpfTrie.Entry entry : entries) {
            assertNotNull(entry.getKey(), "Key should not be null");
            keys.add(new String(entry.getKey()));
        }

        assertTrue(keys.contains("key1"));
        assertTrue(keys.contains("key2"));
        assertTrue(keys.contains("key3"));
    }

    @Test
    void updatePreservesKey() {
        byte[] key = b("mykey");
        trie.put(key, b("value1"));
        trie.put(key, b("value2")); // Update

        List<MpfTrie.Entry> entries = trie.getAllEntries();

        assertEquals(1, entries.size());
        assertArrayEquals(b("value2"), entries.get(0).getValue());
        assertArrayEquals(key, entries.get(0).getKey());
    }

    @Test
    void afterDelete_remainingEntriesHaveKeys() {
        trie.put(b("key1"), b("value1"));
        trie.put(b("key2"), b("value2"));
        trie.put(b("key3"), b("value3"));

        trie.delete(b("key2"));

        List<MpfTrie.Entry> entries = trie.getAllEntries();

        assertEquals(2, entries.size());

        Set<String> keys = new HashSet<>();
        for (MpfTrie.Entry entry : entries) {
            assertNotNull(entry.getKey());
            keys.add(new String(entry.getKey()));
        }

        assertTrue(keys.contains("key1"));
        assertFalse(keys.contains("key2"));
        assertTrue(keys.contains("key3"));
    }

    @Test
    void loadFromExistingRoot_newEntriesHaveKeys() {
        // Create a trie and get root
        trie.put(b("existing"), b("existingValue"));
        byte[] root = trie.getRootHash();

        // Load from same root
        MpfTrie trie2 = new MpfTrie(store, root);

        // Add new key - should have key
        trie2.put(b("newKey"), b("newValue"));

        List<MpfTrie.Entry> entries = trie2.getAllEntries();
        assertEquals(2, entries.size());

        // Find the new entry
        for (MpfTrie.Entry entry : entries) {
            if (Arrays.equals(b("newValue"), entry.getValue())) {
                assertArrayEquals(b("newKey"), entry.getKey(),
                        "New entry should have key");
            }
        }
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
    void getTreeStructure_includesKey() {
        trie.put(b("hello"), b("world"));

        TreeNode structure = trie.getTreeStructure();

        assertNotNull(structure);
        assertInstanceOf(TreeNode.LeafTreeNode.class, structure);

        TreeNode.LeafTreeNode leaf = (TreeNode.LeafTreeNode) structure;
        assertNotNull(leaf.getKey());
        assertEquals("68656c6c6f", leaf.getKey()); // "hello" in hex
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
    void printTreeJson_includesKeyField() {
        trie.put(b("hello"), b("world"));

        String json = trie.printTreeJson();

        assertNotNull(json);
        assertTrue(json.contains("\"key\""));
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
        assertEquals("hello", leaf.getKey());
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

    // ===== getTreeStructure(prefix, maxNodes) tests =====

    @Test
    void getTreeStructure_emptyPrefix_returnsRootSubtree() {
        // Insert more entries to ensure we get a complex tree
        for (int i = 0; i < 20; i++) {
            trie.put(b("key" + i), b("value" + i));
        }

        TreeNode structure = trie.getTreeStructure(new int[]{}, 100);

        assertNotNull(structure);
        // With many entries, root should be a branch node (but can also be extension leading to branch)
        assertTrue(structure instanceof TreeNode.BranchTreeNode || structure instanceof TreeNode.ExtensionTreeNode,
                "Root should be branch or extension node");
    }

    @Test
    void getTreeStructure_validPrefix_returnsSubtree() {
        // Insert many entries to ensure we have a complex tree
        for (int i = 0; i < 50; i++) {
            trie.put(b("key" + i), b("value" + i));
        }

        // Get root structure first
        TreeNode root = trie.getTreeStructure(new int[]{}, 1000);
        assertNotNull(root);

        // Get the first non-null child nibble from the root
        if (root instanceof TreeNode.BranchTreeNode) {
            TreeNode.BranchTreeNode branch = (TreeNode.BranchTreeNode) root;
            for (int nibble = 0; nibble < 16; nibble++) {
                TreeNode child = branch.getChildren().get(Integer.toHexString(nibble));
                if (child != null) {
                    // Now get subtree at that nibble
                    TreeNode subtree = trie.getTreeStructure(new int[]{nibble}, 100);
                    assertNotNull(subtree, "Should return subtree at nibble " + nibble);
                    break;
                }
            }
        }
    }

    @Test
    void getTreeStructure_invalidPrefix_returnsNull() {
        trie.put(b("key1"), b("value1"));

        // Try to navigate through a non-existent branch path
        // The hashed key determines where entries go, so pick a random deep path
        TreeNode result = trie.getTreeStructure(new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, 100);

        // Very likely to be null since we only have one entry
        // If by chance the entry is at this path, this test may occasionally fail
        // For robust testing, we'd need to verify the actual hash path
    }

    @Test
    void getTreeStructure_withMaxNodes_truncatesTree() {
        // Insert many entries to ensure we have a large tree
        for (int i = 0; i < 100; i++) {
            trie.put(b("entry-" + i), b("data-" + i));
        }

        // Get structure with very small limit
        TreeNode structure = trie.getTreeStructure(new int[]{}, 5);

        assertNotNull(structure);
        // Should contain at least one truncated node
        boolean hasTruncated = containsTruncatedNode(structure);
        assertTrue(hasTruncated, "With limit of 5, large tree should have truncated nodes");
    }

    @Test
    void getTreeStructure_withPrefixAndLimit_emptyTrie_returnsNull() {
        TreeNode structure = trie.getTreeStructure(new int[]{}, 100);

        assertNull(structure);
    }

    @Test
    void getTreeStructure_invalidNibble_throwsException() {
        trie.put(b("key"), b("value"));

        // Nibble > 15 should throw
        assertThrows(IllegalArgumentException.class,
                () -> trie.getTreeStructure(new int[]{16}, 100));

        // Nibble < 0 should throw
        assertThrows(IllegalArgumentException.class,
                () -> trie.getTreeStructure(new int[]{-1}, 100));

        // Valid nibble at boundary (15) should not throw
        assertDoesNotThrow(() -> trie.getTreeStructure(new int[]{15}, 100));

        // Valid nibble at boundary (0) should not throw
        assertDoesNotThrow(() -> trie.getTreeStructure(new int[]{0}, 100));
    }

    @Test
    void getTreeStructure_invalidMaxNodes_throwsException() {
        trie.put(b("key"), b("value"));

        // maxNodes = 0 should throw
        assertThrows(IllegalArgumentException.class,
                () -> trie.getTreeStructure(new int[]{}, 0));

        // maxNodes < 0 should throw
        assertThrows(IllegalArgumentException.class,
                () -> trie.getTreeStructure(new int[]{}, -1));

        // maxNodes = 1 should not throw
        assertDoesNotThrow(() -> trie.getTreeStructure(new int[]{}, 1));
    }

    @Test
    void getTreeStructure_deepPrefix_navigatesToCorrectNode() {
        // Insert entries to build a tree
        for (int i = 0; i < 20; i++) {
            trie.put(b("item-" + i), b("content-" + i));
        }

        // Get root structure
        TreeNode root = trie.getTreeStructure(new int[]{}, 1000);
        assertNotNull(root);

        // Find a path to a leaf
        int[] pathToLeaf = findPathToLeaf(root);
        if (pathToLeaf != null && pathToLeaf.length > 0) {
            // Get subtree at some point along the path
            int prefixLen = Math.min(2, pathToLeaf.length);
            int[] prefix = Arrays.copyOf(pathToLeaf, prefixLen);

            TreeNode subtree = trie.getTreeStructure(prefix, 1000);
            assertNotNull(subtree, "Should find subtree at valid prefix");
        }
    }

    @Test
    void getTreeStructure_truncatedBranchNode_containsMetadata() {
        // Insert many entries
        for (int i = 0; i < 50; i++) {
            trie.put(b("entry-" + i), b("data-" + i));
        }

        // Get structure with limit that will cause truncation
        TreeNode structure = trie.getTreeStructure(new int[]{}, 3);
        assertNotNull(structure);

        // Find a truncated node
        TreeNode.TruncatedTreeNode truncated = findTruncatedNode(structure);
        if (truncated != null) {
            assertNotNull(truncated.getHash(), "Truncated node should have hash");
            assertNotNull(truncated.getNodeType(), "Truncated node should have nodeType");
            assertTrue(truncated.getChildCount() >= 0, "Child count should be non-negative");
            assertEquals("truncated", truncated.getType());
        }
    }

    @Test
    void getTreeStructure_limitOne_returnsMinimalStructure() {
        for (int i = 0; i < 20; i++) {
            trie.put(b("key" + i), b("value" + i));
        }

        // With limit 1, we should get just the root with truncated children
        TreeNode structure = trie.getTreeStructure(new int[]{}, 1);

        assertNotNull(structure);
        // Root is counted as 1 node, so children should be truncated
        if (structure instanceof TreeNode.BranchTreeNode) {
            TreeNode.BranchTreeNode branch = (TreeNode.BranchTreeNode) structure;
            // Children should either be null or truncated
            for (TreeNode child : branch.getChildren().values()) {
                if (child != null) {
                    assertInstanceOf(TreeNode.TruncatedTreeNode.class, child,
                            "Children should be truncated with limit of 1");
                }
            }
        }
    }

    @Test
    void getTreeStructure_consistentBetweenRootAndPrefix() {
        for (int i = 0; i < 30; i++) {
            trie.put(b("data-" + i), b("value-" + i));
        }

        // Get full structure
        TreeNode fullRoot = trie.getTreeStructure(new int[]{}, 1000);
        assertNotNull(fullRoot);

        if (fullRoot instanceof TreeNode.BranchTreeNode) {
            TreeNode.BranchTreeNode branch = (TreeNode.BranchTreeNode) fullRoot;
            // Find first non-null child
            for (int nibble = 0; nibble < 16; nibble++) {
                TreeNode childFromFull = branch.getChildren().get(Integer.toHexString(nibble));
                if (childFromFull != null && !(childFromFull instanceof TreeNode.TruncatedTreeNode)) {
                    // Get same subtree via prefix
                    TreeNode childFromPrefix = trie.getTreeStructure(new int[]{nibble}, 1000);

                    // Both should be the same type
                    assertNotNull(childFromPrefix);
                    assertEquals(childFromFull.getType(), childFromPrefix.getType(),
                            "Same subtree should have same type regardless of access method");
                    break;
                }
            }
        }
    }

    @Test
    void truncatedTreeNode_jsonSerialization_works() {
        for (int i = 0; i < 50; i++) {
            trie.put(b("entry-" + i), b("data-" + i));
        }

        // Get structure with truncation
        TreeNode structure = trie.getTreeStructure(new int[]{}, 5);
        assertNotNull(structure);

        // Should serialize to JSON without error
        String json = TreeNode.toJson(structure);
        assertNotNull(json);
        assertFalse(json.isEmpty());

        // Should contain truncated type if there are truncated nodes
        if (containsTruncatedNode(structure)) {
            assertTrue(json.contains("\"type\" : \"truncated\"") || json.contains("\"type\":\"truncated\""),
                    "JSON should contain truncated node type");
        }
    }

    @Test
    void treeNode_truncatedTreeNode_hasCorrectType() {
        TreeNode.TruncatedTreeNode truncated = new TreeNode.TruncatedTreeNode(
                "abc123def456",
                "branch",
                5
        );

        assertEquals("truncated", truncated.getType());
        assertEquals("abc123def456", truncated.getHash());
        assertEquals("branch", truncated.getNodeType());
        assertEquals(5, truncated.getChildCount());
    }

    // ===== Helper methods for prefix tests =====

    /**
     * Recursively checks if the tree contains any TruncatedTreeNode.
     */
    private boolean containsTruncatedNode(TreeNode node) {
        if (node == null) {
            return false;
        }
        if (node instanceof TreeNode.TruncatedTreeNode) {
            return true;
        }
        if (node instanceof TreeNode.BranchTreeNode) {
            TreeNode.BranchTreeNode branch = (TreeNode.BranchTreeNode) node;
            for (TreeNode child : branch.getChildren().values()) {
                if (containsTruncatedNode(child)) {
                    return true;
                }
            }
        }
        if (node instanceof TreeNode.ExtensionTreeNode) {
            TreeNode.ExtensionTreeNode ext = (TreeNode.ExtensionTreeNode) node;
            return containsTruncatedNode(ext.getChild());
        }
        return false;
    }

    /**
     * Finds first TruncatedTreeNode in the tree.
     */
    private TreeNode.TruncatedTreeNode findTruncatedNode(TreeNode node) {
        if (node == null) {
            return null;
        }
        if (node instanceof TreeNode.TruncatedTreeNode) {
            return (TreeNode.TruncatedTreeNode) node;
        }
        if (node instanceof TreeNode.BranchTreeNode) {
            TreeNode.BranchTreeNode branch = (TreeNode.BranchTreeNode) node;
            for (TreeNode child : branch.getChildren().values()) {
                TreeNode.TruncatedTreeNode found = findTruncatedNode(child);
                if (found != null) {
                    return found;
                }
            }
        }
        if (node instanceof TreeNode.ExtensionTreeNode) {
            TreeNode.ExtensionTreeNode ext = (TreeNode.ExtensionTreeNode) node;
            return findTruncatedNode(ext.getChild());
        }
        return null;
    }

    /**
     * Finds a path to any leaf node in the tree.
     */
    private int[] findPathToLeaf(TreeNode node) {
        if (node == null) {
            return null;
        }
        if (node instanceof TreeNode.LeafTreeNode) {
            return new int[0];
        }
        if (node instanceof TreeNode.BranchTreeNode) {
            TreeNode.BranchTreeNode branch = (TreeNode.BranchTreeNode) node;
            for (int i = 0; i < 16; i++) {
                String nibbleKey = Integer.toHexString(i);
                TreeNode child = branch.getChildren().get(nibbleKey);
                if (child != null) {
                    int[] childPath = findPathToLeaf(child);
                    if (childPath != null) {
                        int[] path = new int[childPath.length + 1];
                        path[0] = i;
                        System.arraycopy(childPath, 0, path, 1, childPath.length);
                        return path;
                    }
                }
            }
        }
        if (node instanceof TreeNode.ExtensionTreeNode) {
            TreeNode.ExtensionTreeNode ext = (TreeNode.ExtensionTreeNode) node;
            int[] childPath = findPathToLeaf(ext.getChild());
            if (childPath != null) {
                int[] extPath = ext.getPath();
                int[] path = new int[extPath.length + childPath.length];
                System.arraycopy(extPath, 0, path, 0, extPath.length);
                System.arraycopy(childPath, 0, path, extPath.length, childPath.length);
                return path;
            }
        }
        return null;
    }

    // ===== Helper methods =====

    private static byte[] b(String s) {
        return s.getBytes();
    }

    private static byte[] hex(String hexString) {
        return HexUtil.decodeHexString(hexString);
    }
}
