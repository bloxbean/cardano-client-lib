package com.bloxbean.cardano.statetrees.rocksdb.gc.modern;

import com.bloxbean.cardano.statetrees.rocksdb.keys.RocksDbKey;
import com.bloxbean.cardano.statetrees.rocksdb.batch.RocksDbBatchContext;
import com.bloxbean.cardano.statetrees.rocksdb.exceptions.RocksDbStorageException;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Modern storage abstraction for GC operations with type-safe keys.
 * 
 * <p>This interface provides a clean separation between storage operations
 * and GC algorithms, enabling better testability, flexibility, and maintainability.
 * It uses type-safe keys and modern Java patterns like Optional and Stream.</p>
 * 
 * <p><b>Key Design Principles:</b></p>
 * <ul>
 *   <li>Type-safe operations using RocksDbKey hierarchy</li>
 *   <li>Clear separation of concerns between storage and algorithms</li>
 *   <li>Functional programming patterns with Stream API</li>
 *   <li>Consistent error handling with structured exceptions</li>
 *   <li>Support for both batch and individual operations</li>
 * </ul>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // Repository provides clean abstraction over storage
 * StorageRepository repo = new RocksDbStorageRepository(nodeStore, rootsIndex);
 * 
 * // Type-safe node operations
 * Optional<byte[]> nodeData = repo.getNode(NodeHashKey.of(hash));
 * 
 * // Stream-based traversal
 * Stream<NodeHashKey> reachableNodes = repo.getAllRoots()
 *     .flatMap(repo::traverseFromRoot);
 * 
 * // Batch operations with resource management
 * try (RocksDbBatchContext batch = repo.createBatchContext()) {
 *     reachableNodes.forEach(key -> repo.deleteNode(batch, key));
 *     batch.commit();
 * }
 * }</pre>
 * 
 * @author Bloxbean Project
 * @since 0.6.0
 */
public interface StorageRepository {
    
    /**
     * Retrieves node data by its hash key.
     * 
     * @param key the node hash key
     * @return the node data if found, empty otherwise
     * @throws RocksDbStorageException if a storage error occurs
     */
    Optional<byte[]> getNode(NodeHashKey key) throws RocksDbStorageException;
    
    /**
     * Stores node data with the specified key.
     * 
     * @param key the node hash key
     * @param data the node data to store
     * @throws RocksDbStorageException if a storage error occurs
     */
    void putNode(NodeHashKey key, byte[] data) throws RocksDbStorageException;
    
    /**
     * Stores node data within a batch context.
     * 
     * @param batch the batch context
     * @param key the node hash key
     * @param data the node data to store
     * @throws RocksDbStorageException if a storage error occurs
     */
    void putNode(RocksDbBatchContext batch, NodeHashKey key, byte[] data) throws RocksDbStorageException;
    
    /**
     * Deletes a node by its hash key.
     * 
     * @param key the node hash key to delete
     * @throws RocksDbStorageException if a storage error occurs
     */
    void deleteNode(NodeHashKey key) throws RocksDbStorageException;
    
    /**
     * Deletes a node within a batch context.
     * 
     * @param batch the batch context
     * @param key the node hash key to delete
     * @throws RocksDbStorageException if a storage error occurs
     */
    void deleteNode(RocksDbBatchContext batch, NodeHashKey key) throws RocksDbStorageException;
    
    /**
     * Checks if a node exists.
     * 
     * @param key the node hash key
     * @return true if the node exists, false otherwise
     * @throws RocksDbStorageException if a storage error occurs
     */
    boolean nodeExists(NodeHashKey key) throws RocksDbStorageException;
    
    /**
     * Gets all root hash keys currently stored.
     * 
     * @return a stream of all root hash keys
     * @throws RocksDbStorageException if a storage error occurs
     */
    Stream<RootHashKey> getAllRoots() throws RocksDbStorageException;
    
    /**
     * Gets the latest root hash key.
     * 
     * @return the latest root if found, empty otherwise
     * @throws RocksDbStorageException if a storage error occurs
     */
    Optional<RootHashKey> getLatestRoot() throws RocksDbStorageException;
    
    /**
     * Gets a root hash key by version.
     * 
     * @param version the version number
     * @return the root for that version if found, empty otherwise
     * @throws RocksDbStorageException if a storage error occurs
     */
    Optional<RootHashKey> getRootByVersion(long version) throws RocksDbStorageException;
    
    /**
     * Stores a root hash with its version.
     * 
     * @param version the version number
     * @param key the root hash key
     * @throws RocksDbStorageException if a storage error occurs
     */
    void putRoot(long version, RootHashKey key) throws RocksDbStorageException;
    
    /**
     * Stores a root hash within a batch context.
     * 
     * @param batch the batch context
     * @param version the version number
     * @param key the root hash key
     * @throws RocksDbStorageException if a storage error occurs
     */
    void putRoot(RocksDbBatchContext batch, long version, RootHashKey key) throws RocksDbStorageException;
    
    /**
     * Deletes a root by version.
     * 
     * @param version the version to delete
     * @throws RocksDbStorageException if a storage error occurs
     */
    void deleteRoot(long version) throws RocksDbStorageException;
    
    /**
     * Deletes a root within a batch context.
     * 
     * @param batch the batch context
     * @param version the version to delete
     * @throws RocksDbStorageException if a storage error occurs
     */
    void deleteRoot(RocksDbBatchContext batch, long version) throws RocksDbStorageException;
    
    /**
     * Gets reference count for a node.
     * 
     * @param key the node hash key
     * @return the current reference count, 0 if not found
     * @throws RocksDbStorageException if a storage error occurs
     */
    long getNodeRefCount(NodeHashKey key) throws RocksDbStorageException;
    
    /**
     * Sets reference count for a node.
     * 
     * @param key the node hash key
     * @param refCount the reference count to set
     * @throws RocksDbStorageException if a storage error occurs
     */
    void setNodeRefCount(NodeHashKey key, long refCount) throws RocksDbStorageException;
    
    /**
     * Sets reference count within a batch context.
     * 
     * @param batch the batch context
     * @param key the node hash key
     * @param refCount the reference count to set
     * @throws RocksDbStorageException if a storage error occurs
     */
    void setNodeRefCount(RocksDbBatchContext batch, NodeHashKey key, long refCount) throws RocksDbStorageException;
    
    /**
     * Increments reference count for a node.
     * 
     * @param key the node hash key
     * @param delta the amount to increment by
     * @return the new reference count
     * @throws RocksDbStorageException if a storage error occurs
     */
    long incrementNodeRefCount(NodeHashKey key, long delta) throws RocksDbStorageException;
    
    /**
     * Increments reference count within a batch context.
     * 
     * @param batch the batch context
     * @param key the node hash key
     * @param delta the amount to increment by
     * @throws RocksDbStorageException if a storage error occurs
     */
    void incrementNodeRefCount(RocksDbBatchContext batch, NodeHashKey key, long delta) throws RocksDbStorageException;
    
    /**
     * Traverses all nodes reachable from a root, returning their keys.
     * 
     * <p>This method performs a depth-first traversal of the trie structure
     * starting from the specified root, yielding the keys of all reachable nodes.</p>
     * 
     * @param rootKey the root to start traversal from
     * @return a stream of all reachable node keys
     * @throws RocksDbStorageException if a storage error occurs
     */
    Stream<NodeHashKey> traverseFromRoot(RootHashKey rootKey) throws RocksDbStorageException;
    
    /**
     * Gets the total number of nodes in storage.
     * 
     * @return the total node count
     * @throws RocksDbStorageException if a storage error occurs
     */
    long getTotalNodeCount() throws RocksDbStorageException;
    
    /**
     * Gets the total size of all node data in bytes.
     * 
     * @return the total data size in bytes
     * @throws RocksDbStorageException if a storage error occurs
     */
    long getTotalDataSize() throws RocksDbStorageException;
    
    /**
     * Creates a new batch context for batched operations.
     * 
     * @return a new batch context
     * @throws RocksDbStorageException if context creation fails
     */
    RocksDbBatchContext createBatchContext() throws RocksDbStorageException;
    
    /**
     * Performs a consistency check on the storage.
     * 
     * @return a set of any inconsistencies found
     * @throws RocksDbStorageException if the check fails
     */
    Set<String> performConsistencyCheck() throws RocksDbStorageException;
}