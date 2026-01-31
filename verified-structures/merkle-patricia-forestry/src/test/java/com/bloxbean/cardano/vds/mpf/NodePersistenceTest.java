package com.bloxbean.cardano.vds.mpf;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.api.NodeStore;
import com.bloxbean.cardano.vds.core.NodeHash;
import com.bloxbean.cardano.vds.mpf.test.TestNodeStore;
import com.bloxbean.cardano.vds.core.nibbles.Nibbles;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.mpf.commitment.CommitmentScheme;
import com.bloxbean.cardano.vds.mpf.commitment.MpfCommitmentScheme;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NodePersistenceTest {

    private NodeStore mockStore;
    private NodePersistence persistence;
    private NodeStore realStore;
    private NodePersistence realPersistence;
    private HashFunction hashFn;
    private CommitmentScheme commitments;

    @BeforeEach
    void setUp() {
        mockStore = Mockito.mock(NodeStore.class);
        hashFn = Blake2b256::digest;
        commitments = new MpfCommitmentScheme(hashFn);
        persistence = new NodePersistence(mockStore, commitments, hashFn);

        realStore = new TestNodeStore();
        realPersistence = new NodePersistence(realStore, commitments, hashFn);
    }

    @Test
    void testConstructorValidation() {
        assertThrows(NullPointerException.class, () ->
                new NodePersistence(null, commitments, hashFn));
    }

    @Test
    void testPersistNode() {
        // Create a test leaf node
        LeafNode leafNode = createTestLeafNode();

        // Mock store behavior
        doNothing().when(mockStore).put(any(byte[].class), any(byte[].class));

        // Persist the node
        NodeHash hash = persistence.persist(leafNode);

        // Verify interactions
        verify(mockStore, times(1)).put(any(byte[].class), any(byte[].class));

        // Verify hash is correct
        assertNotNull(hash);
        assertArrayEquals(leafNode.commit(hashFn, commitments), hash.getBytes());
    }

    @Test
    void testPersistNullNode() {
        assertThrows(NullPointerException.class, () ->
                persistence.persist(null));
    }

    @Test
    void testPersistStorageException() {
        LeafNode leafNode = createTestLeafNode();

        // Mock store to throw exception
        doThrow(new RuntimeException("Storage error")).when(mockStore)
                .put(any(byte[].class), any(byte[].class));

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                persistence.persist(leafNode));

        assertTrue(exception.getMessage().contains("Failed to persist node"));
        assertTrue(exception.getCause().getMessage().contains("Storage error"));
    }

    @Test
    void testLoadExistingNode() {
        // Use real store for round-trip test
        LeafNode originalNode = createTestLeafNode();

        // Persist the node
        NodeHash hash = realPersistence.persist(originalNode);

        // Load it back
        Node loadedNode = realPersistence.load(hash);

        // Verify it's the same type and has same data
        assertNotNull(loadedNode);
        assertInstanceOf(LeafNode.class, loadedNode);

        LeafNode loadedLeaf = (LeafNode) loadedNode;
        assertArrayEquals(originalNode.encode(), loadedLeaf.encode());
        assertArrayEquals(originalNode.commit(hashFn, commitments), loadedLeaf.commit(hashFn, commitments));
    }

    @Test
    void testLoadNonExistentNode() {
        // Create a dummy hash for non-existent node
        byte[] dummyHash = new byte[32];
        NodeHash hash = NodeHash.of(dummyHash);

        // Mock store to return null
        when(mockStore.get(any(byte[].class))).thenReturn(null);

        // Load should return null
        Node result = persistence.load(hash);
        assertNull(result);

        verify(mockStore, times(1)).get(any(byte[].class));
    }

    @Test
    void testLoadNullHash() {
        assertThrows(NullPointerException.class, () ->
                persistence.load(null));
    }

    @Test
    void testLoadDecodingException() {
        byte[] hash = new byte[32];
        NodeHash nodeHash = NodeHash.of(hash);

        // Mock store to return invalid data
        when(mockStore.get(hash)).thenReturn(new byte[]{1, 2, 3}); // Invalid CBOR

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                persistence.load(nodeHash));

        assertTrue(exception.getMessage().contains("Failed to load node"));
    }

    @Test
    void testGetUnderlyingStore() {
        assertSame(mockStore, persistence.getUnderlyingStore());
    }

    @Test
    void testRoundTripPersistence() {
        // Test with real store for full integration
        LeafNode originalNode = createTestLeafNode();

        // Persist
        NodeHash hash = realPersistence.persist(originalNode);

        // Load
        Node loadedNode = realPersistence.load(hash);
        assertNotNull(loadedNode);
        assertInstanceOf(LeafNode.class, loadedNode);

        LeafNode loadedLeaf = (LeafNode) loadedNode;

        // Verify content matches
        assertArrayEquals(originalNode.commit(hashFn, commitments), loadedLeaf.commit(hashFn, commitments));
        assertArrayEquals(originalNode.encode(), loadedLeaf.encode());
    }

    @Test
    void testMultipleNodePersistence() {
        // Test persisting multiple different nodes
        LeafNode node1 = createTestLeafNode();
        LeafNode node2 = createTestLeafNodeWithValue("different-value");

        NodeHash hash1 = realPersistence.persist(node1);
        NodeHash hash2 = realPersistence.persist(node2);

        // Hashes should be different
        assertNotEquals(hash1, hash2);

        // Both should be loadable
        Node loaded1 = realPersistence.load(hash1);
        Node loaded2 = realPersistence.load(hash2);

        assertNotNull(loaded1);
        assertNotNull(loaded2);

        // Content should match
        assertInstanceOf(LeafNode.class, loaded1);
        assertInstanceOf(LeafNode.class, loaded2);

        LeafNode loadedLeaf1 = (LeafNode) loaded1;
        LeafNode loadedLeaf2 = (LeafNode) loaded2;

        assertArrayEquals(node1.commit(hashFn, commitments), loadedLeaf1.commit(hashFn, commitments));
        assertArrayEquals(node2.commit(hashFn, commitments), loadedLeaf2.commit(hashFn, commitments));
        assertArrayEquals(node1.encode(), loadedLeaf1.encode());
        assertArrayEquals(node2.encode(), loadedLeaf2.encode());
    }

    @Test
    void testPersistSameNodeTwice() {
        // Persisting the same node twice should produce the same hash
        LeafNode node = createTestLeafNode();

        NodeHash hash1 = realPersistence.persist(node);
        NodeHash hash2 = realPersistence.persist(node);

        assertEquals(hash1, hash2);

        // Should still be loadable
        Node loaded = realPersistence.load(hash1);
        assertNotNull(loaded);
        assertInstanceOf(LeafNode.class, loaded);
        assertArrayEquals(node.commit(hashFn, commitments), ((LeafNode) loaded).commit(hashFn, commitments));
        assertArrayEquals(node.encode(), loaded.encode());
    }

    // Helper methods
    private LeafNode createTestLeafNode() {
        return createTestLeafNodeWithValue("test-value");
    }

    private LeafNode createTestLeafNodeWithValue(String value) {
        byte[] hp = Nibbles.packHP(true, new int[]{1, 2, 3});
        return LeafNode.of(hp, value.getBytes());
    }
}
