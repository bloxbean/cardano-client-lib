package com.bloxbean.cardano.statetrees.api;

/**
 * Index for managing Merkle Patricia Trie root hashes across versions or blockchain slots.
 * 
 * <p>This interface provides versioning capabilities for MPT state, allowing applications
 * to track root hashes at different points in time (versions, block heights, or slots).
 * This is essential for blockchain applications that need to maintain historical state
 * or support state rollbacks.</p>
 * 
 * <p>Typical usage in blockchain context:</p>
 * <pre>{@code
 * // After processing a block
 * trie.put(key, value);
 * byte[] newRoot = trie.getRootHash();
 * rootsIndex.put(blockHeight, newRoot);
 * 
 * // To access state at a specific block
 * byte[] historicalRoot = rootsIndex.get(blockHeight);
 * trie.setRootHash(historicalRoot);
 * }</pre>
 * 
 * <p>Thread Safety: Implementations should document their thread safety guarantees.</p>
 * 
 * @author Bloxbean Project
 * @since 0.6.0
 * @see com.bloxbean.cardano.statetrees.api.MerklePatriciaTrie
 */
public interface RootsIndex {
  /**
   * Stores a root hash for a specific version or slot.
   * 
   * <p>This method should also update the "latest" root hash to maintain
   * consistency. Implementations may choose to enforce monotonic version
   * numbers or allow arbitrary version updates.</p>
   * 
   * @param versionOrSlot the version number, block height, or slot number
   * @param rootHash the MPT root hash at this version (typically 32 bytes)
   * @throws RuntimeException if storage operation fails
   * @throws NullPointerException if rootHash is null
   */
  void   put(long versionOrSlot, byte[] rootHash);
  
  /**
   * Retrieves the root hash for a specific version or slot.
   * 
   * @param versionOrSlot the version number, block height, or slot number
   * @return the root hash at the specified version, or null if not found
   * @throws RuntimeException if storage operation fails
   */
  byte[] get(long versionOrSlot);
  
  /**
   * Retrieves the most recently stored root hash.
   * 
   * <p>This provides quick access to the current state root without
   * needing to know the latest version number.</p>
   * 
   * @return the latest root hash, or null if no roots have been stored
   * @throws RuntimeException if storage operation fails
   */
  byte[] latest();
}

