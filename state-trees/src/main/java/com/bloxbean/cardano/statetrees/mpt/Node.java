package com.bloxbean.cardano.statetrees.mpt;

/**
 * Abstract base class for all Merkle Patricia Trie node types.
 *
 * <p>The MPT uses three types of nodes:</p>
 * <ul>
 *   <li>{@link LeafNode} - stores a key-value pair at the end of a path</li>
 *   <li>{@link BranchNode} - provides 16-way branching for hexary trie structure</li>
 *   <li>{@link ExtensionNode} - compresses paths by storing common prefixes</li>
 * </ul>
 *
 * <p>All nodes are immutable once created and are identified by their content hash.
 * The hash serves as both the node's identity and ensures data integrity in the
 * distributed storage system.</p>
 *
 */
abstract class Node {

  /**
   * Computes the cryptographic hash of this node.
   *
   * <p>The hash is computed from the CBOR-encoded node data using Blake2b-256.
   * This hash serves as the node's unique identifier and is used as the key
   * when storing the node in the persistent storage.</p>
   *
   * @return the 32-byte Blake2b-256 hash of this node
   */
  abstract byte[] hash();

  /**
   * Encodes this node to CBOR format for storage.
   *
   * <p>Each node type has a specific CBOR encoding format:</p>
   * <ul>
   *   <li>Leaf/Extension: 2-element array [HP-encoded path, value/child]</li>
   *   <li>Branch: 17-element array [16 child hashes, optional value]</li>
   * </ul>
   *
   * @return the CBOR-encoded byte representation of this node
   */
  abstract byte[] encode();
}

