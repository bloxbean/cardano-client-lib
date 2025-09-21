package com.bloxbean.cardano.statetrees.api;

import com.bloxbean.cardano.statetrees.mpt.MerklePatriciaTrieImpl;

import java.util.List;

/**
 * Public API for the Merkle Patricia Trie data structure.
 * 
 * <p>This class provides a high-level interface to the Merkle Patricia Trie (MPT),
 * a persistent data structure that combines the properties of a Patricia trie 
 * (radix tree) with Merkle tree cryptographic authentication. The MPT is widely 
 * used in blockchain systems for efficient and verifiable state storage.</p>
 * 
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>Cryptographically authenticated - every node is hashed</li>
 *   <li>Space-efficient - uses path compression via extension nodes</li>
 *   <li>Deterministic - same data always produces the same root hash</li>
 *   <li>Supports efficient prefix scanning for range queries</li>
 * </ul>
 * 
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * NodeStore store = new InMemoryNodeStore();
 * HashFunction hashFn = Blake2b256::digest;
 * MerklePatriciaTrie trie = new MerklePatriciaTrie(store, hashFn);
 * 
 * // Store key-value pairs
 * trie.put("hello".getBytes(), "world".getBytes());
 * trie.put("foo".getBytes(), "bar".getBytes());
 * 
 * // Retrieve values
 * byte[] value = trie.get("hello".getBytes());
 * 
 * // Get cryptographic root hash
 * byte[] rootHash = trie.getRootHash();
 * }</pre>
 * 
 * <p><b>Thread Safety:</b> This class is NOT thread-safe. External synchronization
 * is required for concurrent access.</p>
 * 
 * @author Bloxbean Project
 * @since 0.6.0
 * @see NodeStore
 * @see HashFunction
 * @see com.bloxbean.cardano.statetrees.mpt.SecureTrie
 */
public final class MerklePatriciaTrie {
  private final MerklePatriciaTrieImpl impl;

  /**
   * Creates a new empty Merkle Patricia Trie.
   * 
   * @param store the storage backend for persisting trie nodes
   * @param hashFn the hash function for computing node hashes (e.g., Blake2b-256)
   * @throws NullPointerException if store or hashFn is null
   */
  public MerklePatriciaTrie(NodeStore store, HashFunction hashFn) { 
    this.impl = new MerklePatriciaTrieImpl(store, hashFn, null); 
  }
  
  /**
   * Creates a Merkle Patricia Trie with an existing root.
   * 
   * <p>Use this constructor to load an existing trie from storage by providing
   * its root hash. This is useful for accessing historical states or resuming
   * from a checkpoint.</p>
   * 
   * @param store the storage backend for persisting trie nodes
   * @param hashFn the hash function for computing node hashes
   * @param root the root hash of an existing trie, or null for empty trie
   * @throws NullPointerException if store or hashFn is null
   */
  public MerklePatriciaTrie(NodeStore store, HashFunction hashFn, byte[] root) { 
    this.impl = new MerklePatriciaTrieImpl(store, hashFn, root); 
  }

  /**
   * Sets the root hash of this trie.
   * 
   * <p>This method allows switching to a different trie state by changing the root.
   * Useful for reverting to previous states or switching between different tries
   * that share the same storage backend.</p>
   * 
   * @param root the new root hash, or null to reset to empty trie
   */
  public void setRootHash(byte[] root) { 
    impl.setRootHash(root); 
  }
  
  /**
   * Returns the current root hash of the trie.
   * 
   * <p>The root hash uniquely identifies the entire state of the trie. Two tries
   * with the same root hash contain exactly the same data. This property is
   * fundamental for blockchain consensus and state verification.</p>
   * 
   * @return the root hash as a byte array, or null if the trie is empty
   */
  public byte[] getRootHash() { 
    return impl.getRootHash(); 
  }

  /**
   * Stores a key-value pair in the trie.
   * 
   * <p>If the key already exists, its value will be overwritten. The operation
   * updates the root hash to reflect the new state.</p>
   * 
   * @param key the key as a byte array
   * @param value the value to store (must not be null)
   * @throws IllegalArgumentException if value is null (use delete instead)
   * @throws NullPointerException if key is null
   */
  public void put(byte[] key, byte[] value) { 
    impl.put(key, value); 
  }
  
  /**
   * Retrieves the value associated with a key.
   * 
   * @param key the key to look up
   * @return the value as a byte array, or null if the key doesn't exist
   * @throws NullPointerException if key is null
   */
  public byte[] get(byte[] key) { 
    return impl.get(key); 
  }
  
  /**
   * Removes a key-value pair from the trie.
   * 
   * <p>If the key doesn't exist, this operation has no effect. The trie
   * automatically compresses nodes after deletion to maintain space efficiency.</p>
   * 
   * @param key the key to delete
   * @throws NullPointerException if key is null
   */
  public void delete(byte[] key) { 
    impl.delete(key); 
  }
  
  /**
   * Scans the trie for all keys matching a given prefix.
   * 
   * <p>This method efficiently traverses only the relevant portion of the trie,
   * making it suitable for range queries and prefix-based lookups.</p>
   * 
   * @param prefix the prefix to search for (empty array matches all keys)
   * @param limit maximum number of results to return (0 or negative for unlimited)
   * @return a list of Entry objects containing matching key-value pairs
   * @throws NullPointerException if prefix is null
   */
  public List<Entry> scanByPrefix(byte[] prefix, int limit) { 
    return impl.scanByPrefix(prefix, limit); 
  }

  /**
   * Builds an inclusion or non-inclusion proof for the given key.
   *
   * <p>The proof contains the CBOR-encoded nodes encountered while traversing
   * the trie for {@code key}. Depending on how the traversal terminates, the
   * proof type indicates inclusion, a missing branch, or a conflicting leaf.</p>
   *
   * @param key the key for which a proof is requested (must not be null)
   * @return a MerklePatriciaProof describing inclusion or non-inclusion
   * @throws NullPointerException if key is null
   * @since 0.9.0
   */
  public MerklePatriciaProof getProof(byte[] key) {
    return impl.getProof(key);
  }

  /**
   * Represents a key-value pair returned by scan operations.
   * 
   * <p>This immutable class encapsulates the results of prefix scans,
   * providing access to both the key and its associated value.</p>
   * 
   * @since 0.6.0
   */
  public static final class Entry {
    private final byte[] key;
    private final byte[] value;
    
    /**
     * Creates a new Entry with the given key and value.
     * 
     * @param key the key bytes
     * @param value the value bytes
     */
    public Entry(byte[] key, byte[] value) {
      this.key = key;
      this.value = value;
    }
    
    /**
     * Returns the key of this entry.
     * 
     * @return the key as a byte array
     */
    public byte[] getKey() { 
      return key; 
    }
    
    /**
     * Returns the value of this entry.
     * 
     * @return the value as a byte array
     */
    public byte[] getValue() { 
      return value; 
    }
  }
}
