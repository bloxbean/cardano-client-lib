package com.bloxbean.cardano.statetrees.api;

/**
 * Storage abstraction for Merkle Patricia Trie nodes.
 *
 * <p>This interface defines the contract for persisting and retrieving MPT nodes.
 * Nodes are stored as key-value pairs where the key is the node's hash (typically
 * 32 bytes for Blake2b-256) and the value is the CBOR-encoded node data.</p>
 *
 * <p>Implementations can provide different storage backends such as:</p>
 * <ul>
 *   <li>In-memory storage for testing</li>
 *   <li>RocksDB for production use</li>
 *   <li>PostgreSQL for distributed deployments</li>
 *   <li>Redis for caching layers</li>
 * </ul>
 *
 * <p>Thread Safety: Implementations should document their thread safety guarantees.
 * The MPT itself is not thread-safe and requires external synchronization.</p>
 *
 * @see com.bloxbean.cardano.statetrees.api.MerklePatriciaTrie
 * @since 0.8.0
 */
public interface NodeStore {
    /**
     * Retrieves a node by its hash.
     *
     * @param hash the hash of the node to retrieve (typically 32 bytes)
     * @return the CBOR-encoded node bytes, or null if not found
     * @throws RuntimeException if storage operation fails
     */
    byte[] get(byte[] hash);

    /**
     * Stores a node with its hash as the key.
     *
     * <p>Implementations may choose to ignore duplicate puts (same hash and data)
     * for efficiency, as nodes are immutable once created.</p>
     *
     * @param hash      the hash of the node (typically 32 bytes)
     * @param nodeBytes the CBOR-encoded node data
     * @throws RuntimeException     if storage operation fails
     * @throws NullPointerException if either parameter is null
     */
    void put(byte[] hash, byte[] nodeBytes);

    /**
     * Deletes a node by its hash.
     *
     * <p>Note: This operation should be used with caution as it may break
     * the integrity of the trie if the node is still referenced. It's primarily
     * intended for garbage collection of unreferenced nodes.</p>
     *
     * @param hash the hash of the node to delete
     * @throws RuntimeException if storage operation fails
     */
    void delete(byte[] hash);
}

