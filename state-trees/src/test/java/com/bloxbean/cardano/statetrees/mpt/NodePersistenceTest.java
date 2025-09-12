package com.bloxbean.cardano.statetrees.mpt;

import com.bloxbean.cardano.statetrees.TestNodeStore;
import com.bloxbean.cardano.statetrees.api.NodeStore;
import com.bloxbean.cardano.statetrees.common.NodeHash;
import com.bloxbean.cardano.statetrees.common.nibbles.Nibbles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NodePersistenceTest {
    
    private NodeStore mockStore;
    private NodePersistence persistence;
    private TestNodeStore realStore;
    private NodePersistence realPersistence;
    
    @BeforeEach
    void setUp() {
        mockStore = Mockito.mock(NodeStore.class);
        persistence = new NodePersistence(mockStore);
        
        realStore = new TestNodeStore();
        realPersistence = new NodePersistence(realStore);
    }
    
    @Test
    void testConstructorValidation() {
        assertThrows(NullPointerException.class, () -> 
            new NodePersistence(null));
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
        assertArrayEquals(leafNode.hash(), hash.getBytes());
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
        assertArrayEquals(originalNode.hash(), loadedLeaf.hash());
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
        
        verify(mockStore, times(1)).get(dummyHash);
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
    void testExists() {
        byte[] hash = new byte[32];
        NodeHash nodeHash = NodeHash.of(hash);
        
        // Test existing node
        when(mockStore.get(hash)).thenReturn(new byte[]{1, 2, 3});
        assertTrue(persistence.exists(nodeHash));
        
        // Test non-existing node
        when(mockStore.get(hash)).thenReturn(null);
        assertFalse(persistence.exists(nodeHash));
        
        verify(mockStore, times(2)).get(hash);
    }
    
    @Test
    void testExistsNullHash() {
        assertThrows(NullPointerException.class, () -> 
            persistence.exists(null));
    }
    
    @Test
    void testExistsStorageException() {
        byte[] hash = new byte[32];
        NodeHash nodeHash = NodeHash.of(hash);
        
        when(mockStore.get(hash)).thenThrow(new RuntimeException("Storage error"));
        
        RuntimeException exception = assertThrows(RuntimeException.class, () -> 
            persistence.exists(nodeHash));
        
        assertTrue(exception.getMessage().contains("Failed to check existence"));
    }
    
    @Test
    void testDelete() {
        byte[] hash = new byte[32];
        NodeHash nodeHash = NodeHash.of(hash);
        
        doNothing().when(mockStore).delete(hash);
        
        assertDoesNotThrow(() -> persistence.delete(nodeHash));
        
        verify(mockStore, times(1)).delete(hash);
    }
    
    @Test
    void testDeleteNullHash() {
        assertThrows(NullPointerException.class, () -> 
            persistence.delete(null));
    }
    
    @Test
    void testDeleteStorageException() {
        byte[] hash = new byte[32];
        NodeHash nodeHash = NodeHash.of(hash);
        
        doThrow(new RuntimeException("Delete error")).when(mockStore).delete(hash);
        
        RuntimeException exception = assertThrows(RuntimeException.class, () -> 
            persistence.delete(nodeHash));
        
        assertTrue(exception.getMessage().contains("Failed to delete node"));
    }
    
    @Test
    void testPersistIfAbsentNewNode() {
        LeafNode leafNode = createTestLeafNode();
        byte[] hash = leafNode.hash();
        
        // Mock store to indicate node doesn't exist, then allow persistence
        when(mockStore.get(hash)).thenReturn(null);
        doNothing().when(mockStore).put(eq(hash), any(byte[].class));
        
        NodeHash resultHash = persistence.persistIfAbsent(leafNode);
        
        // Should check existence and then persist
        verify(mockStore, times(1)).get(hash);
        verify(mockStore, times(1)).put(eq(hash), any(byte[].class));
        
        assertArrayEquals(hash, resultHash.getBytes());
    }
    
    @Test
    void testPersistIfAbsentExistingNode() {
        LeafNode leafNode = createTestLeafNode();
        byte[] hash = leafNode.hash();
        
        // Mock store to indicate node already exists
        when(mockStore.get(hash)).thenReturn(new byte[]{1, 2, 3});
        
        NodeHash resultHash = persistence.persistIfAbsent(leafNode);
        
        // Should only check existence, not persist
        verify(mockStore, times(1)).get(hash);
        verify(mockStore, never()).put(any(byte[].class), any(byte[].class));
        
        assertArrayEquals(hash, resultHash.getBytes());
    }
    
    @Test
    void testPersistIfAbsentNullNode() {
        assertThrows(NullPointerException.class, () -> 
            persistence.persistIfAbsent(null));
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
        
        // Verify exists
        assertTrue(realPersistence.exists(hash));
        
        // Load
        Node loadedNode = realPersistence.load(hash);
        assertNotNull(loadedNode);
        
        // Verify content matches
        assertArrayEquals(originalNode.hash(), loadedNode.hash());
        assertArrayEquals(originalNode.encode(), loadedNode.encode());
        
        // Delete
        realPersistence.delete(hash);
        
        // Verify no longer exists
        assertFalse(realPersistence.exists(hash));
        assertNull(realPersistence.load(hash));
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
        assertArrayEquals(node1.encode(), loaded1.encode());
        assertArrayEquals(node2.encode(), loaded2.encode());
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
        assertArrayEquals(node.encode(), loaded.encode());
    }
    
    // Helper methods
    private LeafNode createTestLeafNode() {
        return createTestLeafNodeWithValue("test-value");
    }
    
    private LeafNode createTestLeafNodeWithValue(String value) {
        LeafNode node = new LeafNode();
        // Access fields using reflection since they're package-private
        try {
            java.lang.reflect.Field hpField = LeafNode.class.getDeclaredField("hp");
            java.lang.reflect.Field valueField = LeafNode.class.getDeclaredField("value");
            
            hpField.setAccessible(true);
            valueField.setAccessible(true);
            
            hpField.set(node, Nibbles.packHP(true, new int[]{1, 2, 3}));
            valueField.set(node, value.getBytes());
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test node", e);
        }
        
        return node;
    }
}