package com.bloxbean.cardano.vds.jmt;

import com.bloxbean.cardano.vds.core.NibblePath;
import com.bloxbean.cardano.vds.jmt.TreeCache.NodeEntry;
import com.bloxbean.cardano.vds.jmt.TreeCache.NodeStats;
import com.bloxbean.cardano.vds.jmt.TreeCache.StaleNodeIndex;
import com.bloxbean.cardano.vds.jmt.TreeCache.TreeUpdateBatch;
import com.bloxbean.cardano.vds.jmt.store.JmtStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TreeCache following Diem's TreeCache test patterns.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Three-tier lookup priority (staged → frozen → storage)</li>
 *   <li>Node insertion and deletion</li>
 *   <li>Freeze operation and batch accumulation</li>
 *   <li>Stale node tracking</li>
 *   <li>Multi-transaction batching</li>
 *   <li>Genesis and pre-genesis handling</li>
 * </ul>
 */
class TreeCacheTest {
    private static final byte[] DUMMY_HASH = new byte[32]; // Placeholder hash for tests


    private MockJmtStore mockStore;

    @BeforeEach
    void setUp() {
        mockStore = new MockJmtStore();
    }

    // ===== Basic Operations =====

    @Test
    void testCreateCacheForGenesis() {
        TreeCache cache = new TreeCache(mockStore, 0);

        assertEquals(0, cache.nextVersion());
        assertNotNull(cache.getRootNodeKey());
        assertEquals(NibblePath.EMPTY, cache.getRootNodeKey().path());
        assertEquals(0, cache.getRootNodeKey().version());

        // For genesis, should start with empty tree (no null root node)
        // The first insert will create a leaf at the root
        Optional<NodeEntry> root = cache.getNode(NodeKey.of(NibblePath.EMPTY, 0));
        assertFalse(root.isPresent(), "Genesis should start with empty tree, no null root");
    }

    @Test
    void testCreateCacheForNonGenesisVersion() {
        TreeCache cache = new TreeCache(mockStore, 5);

        assertEquals(5, cache.nextVersion());
        assertEquals(4, cache.getRootNodeKey().version());
        assertEquals(NibblePath.EMPTY, cache.getRootNodeKey().path());
    }

    @Test
    void testPutNodeInCache() {
        TreeCache cache = new TreeCache(mockStore, 1);

        NibblePath path = NibblePath.of(new int[]{1, 2, 3});
        NodeKey key = NodeKey.of(path, 1);
        JmtNode node = createLeafNode("key1", "value1");

        cache.putNode(key, node);

        Optional<NodeEntry> retrieved = cache.getNode(key);
        assertTrue(retrieved.isPresent());
        assertEquals(key, retrieved.get().nodeKey());
        assertEquals(node, retrieved.get().node());
    }

    @Test
    void testPutNodeTwiceFails() {
        TreeCache cache = new TreeCache(mockStore, 1);

        NibblePath path = NibblePath.of(new int[]{1, 2, 3});
        NodeKey key = NodeKey.of(path, 1);
        JmtNode node = createLeafNode("key1", "value1");

        cache.putNode(key, node);

        assertThrows(IllegalStateException.class, () -> {
            cache.putNode(key, node);
        });
    }

    // ===== Three-Tier Lookup =====

    @Test
    void testLookupPriority_StagedFirst() {
        // Setup: put node in storage and in cache
        NibblePath path = NibblePath.of(new int[]{1, 2, 3});
        NodeKey storageKey = NodeKey.of(path, 0);
        JmtNode storageNode = createLeafNode("storage", "old");
        mockStore.putNodeDirectly(storageKey, storageNode);

        TreeCache cache = new TreeCache(mockStore, 1);
        NodeKey cacheKey = NodeKey.of(path, 1);
        JmtNode cacheNode = createLeafNode("cache", "new");
        cache.putNode(cacheKey, cacheNode);

        // Should return cached node (higher priority)
        Optional<NodeEntry> result = cache.getNode(cacheKey);
        assertTrue(result.isPresent());
        assertEquals(cacheNode, result.get().node());
        assertEquals(cacheKey, result.get().nodeKey());
    }

    @Test
    void testLookupPriority_FrozenSecond() {
        // Setup: put node in storage, freeze one in cache, then check
        NibblePath path = NibblePath.of(new int[]{1, 2, 3});
        NodeKey storageKey = NodeKey.of(path, 0);
        JmtNode storageNode = createLeafNode("storage", "old");
        mockStore.putNodeDirectly(storageKey, storageNode);

        TreeCache cache = new TreeCache(mockStore, 1);
        NodeKey frozenKey = NodeKey.of(path, 1);
        JmtNode frozenNode = createLeafNode("frozen", "v1");
        cache.putNode(frozenKey, frozenNode);
        cache.freeze(DUMMY_HASH); // Moves to frozen cache

        // Now frozen node should be returned (not storage)
        Optional<NodeEntry> result = cache.getNode(frozenKey);
        assertTrue(result.isPresent());
        assertEquals(frozenNode, result.get().node());
        assertEquals(frozenKey, result.get().nodeKey());
    }

    @Test
    void testLookupPriority_StorageFallback() {
        NibblePath path = NibblePath.of(new int[]{1, 2, 3});
        NodeKey storageKey = NodeKey.of(path, 0);
        JmtNode storageNode = createLeafNode("storage", "value");
        mockStore.putNodeDirectly(storageKey, storageNode);

        TreeCache cache = new TreeCache(mockStore, 1);

        // Should fall back to storage
        Optional<NodeEntry> result = cache.getNode(storageKey);
        assertTrue(result.isPresent());
        assertEquals(storageNode, result.get().node());
        assertEquals(storageKey, result.get().nodeKey());
    }

    @Test
    void testLookupMissing() {
        TreeCache cache = new TreeCache(mockStore, 1);
        NibblePath path = NibblePath.of(new int[]{9, 9, 9});

        Optional<NodeEntry> result = cache.getNode(NodeKey.of(path, 1));
        assertFalse(result.isPresent());
    }

    // ===== Node Deletion =====

    @Test
    void testDeleteNodeFromCache_JustCreated() {
        TreeCache cache = new TreeCache(mockStore, 1);

        NibblePath path = NibblePath.of(new int[]{1, 2, 3});
        NodeKey key = NodeKey.of(path, 1);
        JmtNode node = createLeafNode("key", "value");

        // Put then immediately delete
        cache.putNode(key, node);
        cache.deleteNode(key, true);

        // Should be removed from cache (undo insertion)
        Optional<NodeEntry> result = cache.getNode(key);
        assertFalse(result.isPresent());
    }

    @Test
    void testDeleteNodeFromStorage_MarksStale() {
        // Setup: put node in storage
        NibblePath path = NibblePath.of(new int[]{1, 2, 3});
        NodeKey storageKey = NodeKey.of(path, 0);
        JmtNode storageNode = createLeafNode("key", "value");
        mockStore.putNodeDirectly(storageKey, storageNode);

        TreeCache cache = new TreeCache(mockStore, 1);

        // Delete it
        cache.deleteNode(storageKey, true);

        // Node should still be in storage but marked stale
        cache.freeze(DUMMY_HASH); byte[] rootHash = DUMMY_HASH;
        TreeUpdateBatch batch = cache.toBatch();

        assertEquals(1, batch.staleIndices().size());
        StaleNodeIndex stale = batch.staleIndices().iterator().next();
        assertEquals(1, stale.staleSinceVersion());
        assertEquals(storageKey, stale.nodeKey());
    }

    @Test
    void testDeleteNodeTwice_Fails() {
        NibblePath path = NibblePath.of(new int[]{1, 2, 3});
        NodeKey storageKey = NodeKey.of(path, 0);
        JmtNode storageNode = createLeafNode("key", "value");
        mockStore.putNodeDirectly(storageKey, storageNode);

        TreeCache cache = new TreeCache(mockStore, 1);

        cache.deleteNode(storageKey, false);

        assertThrows(IllegalStateException.class, () -> {
            cache.deleteNode(storageKey, false);
        });
    }

    // ===== Freeze Operation =====

    @Test
    void testFreeze_MovesNodesToFrozen() {
        TreeCache cache = new TreeCache(mockStore, 1);

        NibblePath path1 = NibblePath.of(new int[]{1});
        NodeKey key1 = NodeKey.of(path1, 1);
        cache.putNode(key1, createLeafNode("k1", "v1"));

        NibblePath path2 = NibblePath.of(new int[]{2});
        NodeKey key2 = NodeKey.of(path2, 1);
        cache.putNode(key2, createLeafNode("k2", "v2"));

        // Freeze
        cache.freeze(DUMMY_HASH); byte[] rootHash = DUMMY_HASH;
        assertNotNull(rootHash);

        // Should still be able to read frozen nodes
        assertTrue(cache.getNode(key1).isPresent());
        assertTrue(cache.getNode(key2).isPresent());

        // Version should be incremented
        assertEquals(2, cache.nextVersion());
    }

    @Test
    void testFreeze_CapturesStatistics() {
        TreeCache cache = new TreeCache(mockStore, 1);

        // Add 2 internal nodes and 3 leaf nodes
        cache.putNode(NodeKey.of(NibblePath.of(new int[]{1}), 1), createInternalNode());
        cache.putNode(NodeKey.of(NibblePath.of(new int[]{2}), 1), createInternalNode());
        cache.putNode(NodeKey.of(NibblePath.of(new int[]{3}), 1), createLeafNode("k1", "v1"));
        cache.putNode(NodeKey.of(NibblePath.of(new int[]{4}), 1), createLeafNode("k2", "v2"));
        cache.putNode(NodeKey.of(NibblePath.of(new int[]{5}), 1), createLeafNode("k3", "v3"));

        cache.freeze(DUMMY_HASH);

        TreeUpdateBatch batch = cache.toBatch();
        List<NodeStats> stats = batch.stats();

        assertEquals(1, stats.size());
        NodeStats s = stats.get(0);
        assertEquals(5, s.newNodes());
        assertEquals(3, s.newLeaves());
    }

    @Test
    void testFreeze_CapturesStaleNodes() {
        // Put node in storage
        NibblePath path = NibblePath.of(new int[]{1});
        NodeKey oldKey = NodeKey.of(path, 0);
        mockStore.putNodeDirectly(oldKey, createLeafNode("k", "old"));

        TreeCache cache = new TreeCache(mockStore, 1);

        // Delete it
        cache.deleteNode(oldKey, true);

        cache.freeze(DUMMY_HASH);

        TreeUpdateBatch batch = cache.toBatch();
        List<NodeStats> stats = batch.stats();

        assertEquals(1, stats.size());
        assertEquals(1, stats.get(0).staleNodes());
        assertEquals(1, stats.get(0).staleLeaves());
    }

    // ===== Multi-Transaction Batching =====

    @Test
    void testMultipleTransactions_AccumulateInBatch() {
        TreeCache cache = new TreeCache(mockStore, 1);

        // Transaction 1
        cache.putNode(NodeKey.of(NibblePath.of(new int[]{1}), 1), createLeafNode("k1", "v1"));
        cache.freeze(DUMMY_HASH);

        // Transaction 2
        cache.putNode(NodeKey.of(NibblePath.of(new int[]{2}), 2), createLeafNode("k2", "v2"));
        cache.freeze(DUMMY_HASH);

        // Transaction 3
        cache.putNode(NodeKey.of(NibblePath.of(new int[]{3}), 3), createLeafNode("k3", "v3"));
        cache.freeze(DUMMY_HASH);

        TreeUpdateBatch batch = cache.toBatch();

        // Should have 3 nodes total
        assertEquals(3, batch.nodes().size());

        // Should have 3 sets of statistics
        assertEquals(3, batch.stats().size());

        // Should have 3 root hashes
        assertEquals(3, cache.getRootHashes().size());
    }

    @Test
    void testMultipleTransactions_VersionIncrement() {
        TreeCache cache = new TreeCache(mockStore, 5);

        assertEquals(5, cache.nextVersion());

        cache.putNode(NodeKey.of(NibblePath.of(new int[]{1}), 5), createLeafNode("k1", "v1"));
        cache.freeze(DUMMY_HASH);
        assertEquals(6, cache.nextVersion());

        cache.putNode(NodeKey.of(NibblePath.of(new int[]{2}), 6), createLeafNode("k2", "v2"));
        cache.freeze(DUMMY_HASH);
        assertEquals(7, cache.nextVersion());
    }

    @Test
    void testMultipleTransactions_EarlierTxVisibleToLater() {
        TreeCache cache = new TreeCache(mockStore, 1);

        NibblePath path = NibblePath.of(new int[]{1, 2, 3});

        // Transaction 1: insert node
        NodeKey key1 = NodeKey.of(path, 1);
        JmtNode node1 = createLeafNode("k1", "v1");
        cache.putNode(key1, node1);
        cache.freeze(DUMMY_HASH);

        // Transaction 2: should see node from transaction 1
        Optional<NodeEntry> found = cache.getNode(key1);
        assertTrue(found.isPresent());
        assertEquals(node1, found.get().node());
    }

    // ===== Root Management =====

    @Test
    void testSetAndGetRootNodeKey() {
        TreeCache cache = new TreeCache(mockStore, 1);

        NodeKey newRoot = NodeKey.of(NibblePath.of(new int[]{1, 2}), 1);
        cache.setRootNodeKey(newRoot);

        assertEquals(newRoot, cache.getRootNodeKey());
    }

    @Test
    void testSetRootNodeKey_NullFails() {
        TreeCache cache = new TreeCache(mockStore, 1);

        assertThrows(NullPointerException.class, () -> {
            cache.setRootNodeKey(null);
        });
    }

    // ===== Batch Conversion =====

    @Test
    void testToBatch_EmptyCache() {
        TreeCache cache = new TreeCache(mockStore, 1);
        cache.freeze(DUMMY_HASH);

        TreeUpdateBatch batch = cache.toBatch();

        assertTrue(batch.nodes().isEmpty());
        assertTrue(batch.staleIndices().isEmpty());
        assertEquals(1, batch.stats().size());
    }

    @Test
    void testToBatch_WithNodes() {
        TreeCache cache = new TreeCache(mockStore, 1);

        cache.putNode(NodeKey.of(NibblePath.of(new int[]{1}), 1), createLeafNode("k1", "v1"));
        cache.putNode(NodeKey.of(NibblePath.of(new int[]{2}), 1), createLeafNode("k2", "v2"));
        cache.freeze(DUMMY_HASH);

        TreeUpdateBatch batch = cache.toBatch();

        assertEquals(2, batch.nodes().size());
        assertTrue(batch.staleIndices().isEmpty());
    }

    @Test
    void testToBatch_NodesAreSorted() {
        TreeCache cache = new TreeCache(mockStore, 1);

        // Insert in random order
        cache.putNode(NodeKey.of(NibblePath.of(new int[]{5}), 1), createLeafNode("k5", "v5"));
        cache.putNode(NodeKey.of(NibblePath.of(new int[]{1}), 1), createLeafNode("k1", "v1"));
        cache.putNode(NodeKey.of(NibblePath.of(new int[]{3}), 1), createLeafNode("k3", "v3"));
        cache.freeze(DUMMY_HASH);

        TreeUpdateBatch batch = cache.toBatch();

        // Nodes should be in sorted order (TreeMap)
        List<NodeKey> keys = new ArrayList<>(batch.nodes().keySet());
        assertEquals(3, keys.size());
        // TreeMap uses comparator based on toString(), so check they're sorted
        assertTrue(keys.get(0).toString().compareTo(keys.get(1).toString()) <= 0);
        assertTrue(keys.get(1).toString().compareTo(keys.get(2).toString()) <= 0);
    }

    // ===== Helper Methods =====

    private JmtLeafNode createLeafNode(String key, String value) {
        byte[] keyHash = hash(key.getBytes());
        byte[] valueHash = hash(value.getBytes());
        return JmtLeafNode.of(keyHash, valueHash);
    }

    private JmtInternalNode createInternalNode() {
        return JmtInternalNode.of(0, new byte[0][], null);
    }

    private byte[] hash(byte[] data) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ===== Mock JmtStore =====

    /**
     * Simple mock JmtStore for testing TreeCache in isolation.
     */
    private static class MockJmtStore implements JmtStore {
        private final Map<NodeKey, JmtNode> storage = new HashMap<>();
        private final Map<Long, byte[]> roots = new HashMap<>();

        void putNodeDirectly(NodeKey key, JmtNode node) {
            storage.put(key, node);
        }

        @Override
        public Optional<VersionedRoot> latestRoot() {
            if (roots.isEmpty()) return Optional.empty();
            long max = Collections.max(roots.keySet());
            return Optional.of(new VersionedRoot(max, roots.get(max)));
        }

        @Override
        public Optional<byte[]> rootHash(long version) {
            return Optional.ofNullable(roots.get(version));
        }

        @Override
        public Optional<NodeEntry> getNode(long version, NibblePath path) {
            // Simple lookup: find node with exact version and path
            NodeKey key = NodeKey.of(path, version);
            JmtNode node = storage.get(key);
            if (node != null) {
                return Optional.of(new NodeEntry(key, node));
            }

            // Try to find node with version <= requested version
            for (Map.Entry<NodeKey, JmtNode> entry : storage.entrySet()) {
                if (entry.getKey().path().equals(path) && entry.getKey().version() <= version) {
                    return Optional.of(new NodeEntry(entry.getKey(), entry.getValue()));
                }
            }

            return Optional.empty();
        }

        @Override
        public Optional<JmtNode> getNode(NodeKey nodeKey) {
            return Optional.ofNullable(storage.get(nodeKey));
        }

        @Override
        public Optional<NodeEntry> floorNode(long version, NibblePath path) {
            return Optional.empty();
        }

        @Override
        public Optional<byte[]> getValue(byte[] keyHash) {
            return Optional.empty();
        }

        @Override
        public CommitBatch beginCommit(long version, CommitConfig config) {
            return new CommitBatch() {
                @Override
                public void putNode(NodeKey nodeKey, JmtNode node) {
                    storage.put(nodeKey, node);
                }

                @Override
                public void markStale(NodeKey nodeKey) {
                    // No-op for mock
                }

                @Override
                public void putValue(byte[] keyHash, byte[] value) {
                    // No-op for mock
                }

                @Override
                public void deleteValue(byte[] keyHash) {
                    // No-op for mock
                }

                @Override
                public void setRootHash(byte[] rootHash) {
                    roots.put(version, rootHash);
                }

                @Override
                public void commit() {
                    // No-op for mock
                }

                @Override
                public void close() {
                    // No-op for mock
                }
            };
        }

        @Override
        public List<NodeKey> staleNodesUpTo(long versionInclusive) {
            return Collections.emptyList();
        }

        @Override
        public int pruneUpTo(long versionInclusive) {
            return 0;
        }

        @Override
        public void close() {
            // No-op for mock
        }
    }
}
