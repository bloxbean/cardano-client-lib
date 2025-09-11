package com.bloxbean.cardano.statetrees.mpt;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.api.MerklePatriciaTrie;
import com.bloxbean.cardano.statetrees.api.NodeStore;

import java.util.List;

/**
 * Secure Merkle Patricia Trie that hashes keys before storage.
 *
 * <p>SecureTrie is a wrapper around the standard Merkle Patricia Trie that automatically
 * hashes all keys using the configured hash function before storing them. This provides
 * several security and performance benefits:</p>
 *
 * <p><b>Security Benefits:</b></p>
 * <ul>
 *   <li><b>DoS Protection:</b> Prevents adversarial key selection that could create
 *       unbalanced tries with poor performance characteristics</li>
 *   <li><b>Key Privacy:</b> Original keys are not directly stored, providing some
 *       level of key confidentiality</li>
 *   <li><b>Uniform Distribution:</b> Hash function ensures keys are uniformly
 *       distributed across the trie space</li>
 * </ul>
 *
 * <p><b>Performance Benefits:</b></p>
 * <ul>
 *   <li><b>Consistent Depth:</b> Hash uniformity leads to more balanced tries</li>
 *   <li><b>Fixed Key Size:</b> All keys become fixed-length hash values</li>
 *   <li><b>Better Caching:</b> Predictable access patterns improve cache performance</li>
 * </ul>
 *
 * <p><b>Trade-offs:</b></p>
 * <ul>
 *   <li><b>No Prefix Queries:</b> Cannot perform prefix scans on original keys</li>
 *   <li><b>Hash Overhead:</b> Additional computation for each key operation</li>
 *   <li><b>Key Recovery:</b> Original keys cannot be recovered from the trie</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * NodeStore store = new InMemoryNodeStore();
 * HashFunction blake2b = Blake2b256::digest;
 * SecureTrie secureTrie = new SecureTrie(store, blake2b);
 *
 * // Keys are automatically hashed before storage
 * secureTrie.put("user123".getBytes(), userData);
 * byte[] data = secureTrie.get("user123".getBytes());
 * }</pre>
 *
 * @see MerklePatriciaTrie
 * @see HashFunction
 */
public final class SecureTrie {
  /**
   * The underlying Merkle Patricia Trie that stores the hashed keys.
   */
  private final MerklePatriciaTrie inner;

  /**
   * The hash function used to hash keys before storage.
   */
  private final HashFunction hashFn;

  /**
   * Creates a new SecureTrie with empty root.
   *
   * @param store the storage backend for persisting trie nodes
   * @param hashFn the hash function for both key hashing and node hashing
   * @throws NullPointerException if store or hashFn is null
   */
  public SecureTrie(NodeStore store, HashFunction hashFn) {
    this.inner = new MerklePatriciaTrie(store, hashFn);
    this.hashFn = hashFn;
  }

  /**
   * Creates a SecureTrie with an existing root.
   *
   * <p>Use this constructor to load an existing secure trie from storage.</p>
   *
   * @param store the storage backend for persisting trie nodes
   * @param hashFn the hash function for both key hashing and node hashing
   * @param root the root hash of an existing secure trie, or null for empty trie
   * @throws NullPointerException if store or hashFn is null
   */
  public SecureTrie(NodeStore store, HashFunction hashFn, byte[] root) {
    this.inner = new MerklePatriciaTrie(store, hashFn, root);
    this.hashFn = hashFn;
  }

  /**
   * Sets the root hash of this secure trie.
   *
   * @param root the new root hash, or null to reset to empty trie
   */
  public void setRootHash(byte[] root) {
    inner.setRootHash(root);
  }

  /**
   * Returns the current root hash of the secure trie.
   *
   * @return the root hash as a byte array, or null if the trie is empty
   */
  public byte[] getRootHash() {
    return inner.getRootHash();
  }

  /**
   * Stores a key-value pair in the secure trie.
   *
   * <p>The key is hashed using the configured hash function before storage.
   * This ensures uniform distribution and prevents adversarial key attacks.</p>
   *
   * @param key the original key (will be hashed before storage)
   * @param value the value to store (must not be null)
   * @throws IllegalArgumentException if value is null
   * @throws NullPointerException if key is null
   */
  public void put(byte[] key, byte[] value) {
    inner.put(hashFn.digest(key), value);
  }

  /**
   * Retrieves a value by its original key.
   *
   * <p>The key is hashed using the same hash function used during storage.</p>
   *
   * @param key the original key to look up
   * @return the value as a byte array, or null if the key doesn't exist
   * @throws NullPointerException if key is null
   */
  public byte[] get(byte[] key) {
    return inner.get(hashFn.digest(key));
  }

  /**
   * Removes a key-value pair from the secure trie.
   *
   * <p>The key is hashed before deletion. If the original key doesn't exist,
   * this operation has no effect.</p>
   *
   * @param key the original key to delete
   * @throws NullPointerException if key is null
   */
  public void delete(byte[] key) {
    inner.delete(hashFn.digest(key));
  }

  /**
   * Scans for entries with hashed keys matching a hashed prefix.
   *
   * <p><b>Important:</b> This method has limited utility in SecureTrie because:
   * <ul>
   *   <li>The prefix is hashed before scanning, losing prefix semantics</li>
   *   <li>Results contain hashed keys, not original keys</li>
   *   <li>Cryptographic hashes don't preserve prefix relationships</li>
   * </ul>
   *
   * <p>This method is mainly provided for API compatibility. For prefix queries
   * on original keys, use the standard {@link MerklePatriciaTrie} instead.</p>
   *
   * @param prefix the prefix to hash and search for
   * @param limit maximum number of results (0 or negative for unlimited)
   * @return a list of entries with hashed keys matching the hashed prefix
   * @throws NullPointerException if prefix is null
   */
  public List<MerklePatriciaTrie.Entry> scanByPrefix(byte[] prefix, int limit) {
    return inner.scanByPrefix(hashFn.digest(prefix), limit);
  }
}

