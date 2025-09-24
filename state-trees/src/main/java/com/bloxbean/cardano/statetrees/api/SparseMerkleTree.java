package com.bloxbean.cardano.statetrees.api;

import com.bloxbean.cardano.statetrees.smt.SparseMerkleTreeImpl;

/**
 * Public API for the Sparse Merkle Tree (SMT).
 *
 * <p>An SMT is a fixed-depth (typically 256) binary Merkle tree where each
 * level branches on a single bit of the hashed key. It provides a compact
 * cryptographic commitment to a key-value map with efficient proofs.
 *
 * <p>This facade mirrors the MPT facade for API familiarity. It is not
 * thread-safe; use external synchronization if accessed concurrently.
 *
 * @since 0.8.0
 */
public final class SparseMerkleTree {

    private final SparseMerkleTreeImpl impl;

    /**
     * Creates a new empty SMT using the provided storage and hash function.
     *
     * @param store  storage backend for SMT node persistence
     * @param hashFn hash function used for key/value hashing and node commitments
     * @since 0.8.0
     */
    public SparseMerkleTree(NodeStore store, HashFunction hashFn) {
        this.impl = new SparseMerkleTreeImpl(store, hashFn, null);
    }

    /**
     * Creates an SMT view rooted at an existing root hash.
     *
     * @param store  storage backend for SMT node persistence
     * @param hashFn hash function used for key/value hashing and node commitments
     * @param root   root hash of an existing SMT (or null for empty)
     * @since 0.8.0
     */
    public SparseMerkleTree(NodeStore store, HashFunction hashFn, byte[] root) {
        this.impl = new SparseMerkleTreeImpl(store, hashFn, root);
    }

    /**
     * Sets the root hash to point this instance to another SMT state.
     *
     * @param root new root hash (or null to represent empty tree)
     * @since 0.8.0
     */
    public void setRootHash(byte[] root) {
        impl.setRootHash(root);
    }

    /**
     * Returns the current root hash of this SMT.
     *
     * @return the root hash, or null for empty tree
     * @since 0.8.0
     */
    public byte[] getRootHash() {
        return impl.getRootHash();
    }

    /**
     * Inserts or updates a key-value pair. Keys and values are hashed internally.
     *
     * @param key   raw key bytes (will be hashed)
     * @param value raw value bytes (will be hashed)
     * @since 0.8.0
     */
    public void put(byte[] key, byte[] value) {
        impl.put(key, value);
    }

    /**
     * Retrieves the value for a key. If stored as hashed value, returns the raw value
     * that was provided to {@link #put(byte[], byte[])}.
     *
     * @param key raw key bytes (will be hashed)
     * @return value bytes, or null if not present
     * @since 0.8.0
     */
    public byte[] get(byte[] key) {
        return impl.get(key);
    }

    /**
     * Deletes the entry for the given key, if present.
     *
     * @param key raw key bytes (will be hashed)
     * @since 0.8.0
     */
    public void delete(byte[] key) {
        impl.delete(key);
    }

    /**
     * Builds an inclusion or non-inclusion (empty path) proof for the given key.
     *
     * @param key the raw key bytes
     * @return a SparseMerkleProof indicating inclusion (with value) or non-inclusion
     * @since 0.8.0
     */
    public SparseMerkleProof getProof(byte[] key) {
        return impl.getProof(key);
    }
}
