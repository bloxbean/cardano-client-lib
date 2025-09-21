package com.bloxbean.cardano.statetrees.smt;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.api.NodeStore;
import com.bloxbean.cardano.statetrees.common.NodeHash;

import java.util.Objects;

/**
 * Core implementation of a 256-bit Sparse Merkle Tree.
 *
 * <p>This class follows the same patterns as the MPT implementation: immutable
 * node model, deterministic CBOR encoding, and a storage abstraction via
 * {@link NodeStore}. Keys are hashed to 256 bits and traversed bit-by-bit.
 *
 * <p>Note: This is an initial scaffold. Put/Get/Delete will be implemented in
 * subsequent steps while keeping the public API stable.
 *
 * @since 0.8.0
 */
public final class SparseMerkleTreeImpl {
  private final NodeStore store;
  private final HashFunction hashFn;
  private final SmtPersistence persistence;
  private byte[] root; // null implies empty tree

  /**
   * Creates a new SMT implementation.
   *
   * @param store node storage backend
   * @param hashFn hash function for commitments
   * @param root initial root hash (nullable)
   * @since 0.8.0
   */
  public SparseMerkleTreeImpl(NodeStore store, HashFunction hashFn, byte[] root) {
    this.store = Objects.requireNonNull(store, "NodeStore");
    this.hashFn = Objects.requireNonNull(hashFn, "HashFunction");
    this.persistence = new SmtPersistence(store);
    this.root = (root == null || root.length == 0) ? null : root.clone();
  }

  /**
   * Sets the root hash of the SMT.
   *
   * @param root new root hash (nullable for empty)
   * @since 0.8.0
   */
  public void setRootHash(byte[] root) {
    this.root = (root == null || root.length == 0) ? null : root.clone();
  }

  /**
   * Returns the current root hash of the SMT.
   *
   * @return root hash or null
   * @since 0.8.0
   */
  public byte[] getRootHash() {
    return root == null ? null : root.clone();
  }

  /**
   * Inserts or updates a key-value pair.
   *
   * @param key raw key bytes (hashed internally)
   * @param value raw value bytes (hashed internally)
   * @since 0.8.0
   */
  public void put(byte[] key, byte[] value) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(value, "value");
    byte[] keyHash = hashFn.digest(key);

    // Start insertion at depth 0
    NodeHash newRoot = insertAt(root, keyHash, value, 0);
    this.root = newRoot == null ? null : newRoot.toBytes();
  }

  /**
   * Retrieves a value by key.
   *
   * @param key raw key bytes (hashed internally)
   * @return value bytes or null
   * @since 0.8.0
   */
  public byte[] get(byte[] key) {
    Objects.requireNonNull(key, "key");
    byte[] keyHash = hashFn.digest(key);
    return getAt(root, keyHash, 0);
  }

  /**
   * Deletes a key.
   *
   * @param key raw key bytes (hashed internally)
   * @since 0.8.0
   */
  public void delete(byte[] key) {
    Objects.requireNonNull(key, "key");
    if (root == null) return;
    byte[] keyHash = hashFn.digest(key);

    // Traverse down, recording path
    java.util.ArrayList<PathEntry> stack = new java.util.ArrayList<>(257);
    byte[] nodeDigest = root;
    for (int depth = 0; depth < 256; depth++) {
      SmtNode node = persistence.load(NodeHash.of(nodeDigest));
      if (!(node instanceof SmtInternalNode)) {
        return; // unexpected structure or not found
      }
      SmtInternalNode internal = (SmtInternalNode) node;
      byte[] l = normalizeDigest(internal.getLeft(), depth + 1);
      byte[] r = normalizeDigest(internal.getRight(), depth + 1);
      int bit = bitAt(keyHash, depth);
      boolean wentLeft = (bit == 0);
      stack.add(new PathEntry(depth, l, r, wentLeft));
      nodeDigest = wentLeft ? l : r;
      if (isEmptyDigest(nodeDigest, depth + 1)) {
        return; // key not present
      }
    }

    // Expect a leaf at nodeDigest
    SmtNode node = persistence.load(NodeHash.of(nodeDigest));
    if (!(node instanceof SmtLeafNode)) {
      return; // not found or structure mismatch
    }
    SmtLeafNode leaf = (SmtLeafNode) node;
    if (!equals32(leaf.getKeyHash(), keyHash)) {
      return; // different key; not found
    }

    // Bottom-up recomputation replacing the leaf with empty commitment
    byte[] currentDigest = EmptyCommitments.EMPTY[256];
    for (int i = stack.size() - 1; i >= 0; i--) {
      PathEntry e = stack.get(i);
      byte[] left = e.wentLeft ? currentDigest : e.leftDigest;
      byte[] right = e.wentLeft ? e.rightDigest : currentDigest;

      if (isEmptyDigest(left, e.depth + 1) && isEmptyDigest(right, e.depth + 1)) {
        currentDigest = EmptyCommitments.EMPTY[e.depth];
      } else {
        NodeHash nh = persistence.persist(SmtInternalNode.of(left, right));
        currentDigest = nh.toBytes();
      }
    }

    // Update root
    this.root = isEmptyDigest(currentDigest, 0) ? null : currentDigest;
  }

  /**
   * Generates an inclusion or non-inclusion proof for a key.
   *
   * @param key raw key bytes
   * @return a proof object with siblings and optional value
   */
  public com.bloxbean.cardano.statetrees.api.SparseMerkleProof getProof(byte[] key) {
    Objects.requireNonNull(key, "key");
    byte[][] siblings = new byte[256][];
    byte[] keyHash = hashFn.digest(key);

    if (root == null) {
      // all siblings are empties
      for (int d = 0; d < 256; d++) siblings[d] = EmptyCommitments.EMPTY[d + 1];
      return new com.bloxbean.cardano.statetrees.api.SparseMerkleProof(
          com.bloxbean.cardano.statetrees.api.SparseMerkleProof.Type.NON_INCLUSION_EMPTY, siblings, null);
    }

    byte[] nodeDigest = root;
    for (int depth = 0; depth < 256; depth++) {
      SmtNode node = persistence.load(NodeHash.of(nodeDigest));
      if (!(node instanceof SmtInternalNode)) {
        // unexpected structure; treat as non-inclusion
        for (int d = depth; d < 256; d++) siblings[d] = EmptyCommitments.EMPTY[d + 1];
        return new com.bloxbean.cardano.statetrees.api.SparseMerkleProof(
            com.bloxbean.cardano.statetrees.api.SparseMerkleProof.Type.NON_INCLUSION_EMPTY, siblings, null);
      }
      SmtInternalNode internal = (SmtInternalNode) node;
      byte[] l = normalizeDigest(internal.getLeft(), depth + 1);
      byte[] r = normalizeDigest(internal.getRight(), depth + 1);
      int bit = bitAt(keyHash, depth);
      siblings[depth] = bit == 0 ? r : l;
      nodeDigest = bit == 0 ? l : r;
      if (isEmptyDigest(nodeDigest, depth + 1)) {
        // fill remaining siblings with empties
        for (int d = depth + 1; d < 256; d++) siblings[d] = EmptyCommitments.EMPTY[d + 1];
        return new com.bloxbean.cardano.statetrees.api.SparseMerkleProof(
            com.bloxbean.cardano.statetrees.api.SparseMerkleProof.Type.NON_INCLUSION_EMPTY, siblings, null);
      }
    }

    // Expect a leaf at the end
    SmtNode node = persistence.load(NodeHash.of(nodeDigest));
    if (node instanceof SmtLeafNode) {
      SmtLeafNode leaf = (SmtLeafNode) node;
      if (equals32(leaf.getKeyHash(), keyHash)) {
        return new com.bloxbean.cardano.statetrees.api.SparseMerkleProof(
            com.bloxbean.cardano.statetrees.api.SparseMerkleProof.Type.INCLUSION, siblings, leaf.getValue());
      }
    }

    // Fallback to non-inclusion
    return new com.bloxbean.cardano.statetrees.api.SparseMerkleProof(
        com.bloxbean.cardano.statetrees.api.SparseMerkleProof.Type.NON_INCLUSION_EMPTY, siblings, null);
  }

  private NodeHash insertAt(byte[] nodeHash, byte[] keyHash, byte[] value, int depth) {
    if (depth == 256) {
      // Should never happen: leaf level represented by leaf node directly
      SmtLeafNode leaf = SmtLeafNode.of(keyHash, value);
      return persistence.persist(leaf);
    }

    if (nodeHash == null) {
      // Build a leaf then build the chain up from depth to 255
      SmtLeafNode leaf = SmtLeafNode.of(keyHash, value);
      NodeHash leafHash = persistence.persist(leaf);
      return buildPathToDepth(leafHash, keyHash, depth);
    }

    SmtNode node = persistence.load(NodeHash.of(nodeHash));
    if (node == null) {
      // Treat as empty
      SmtLeafNode leaf = SmtLeafNode.of(keyHash, value);
      NodeHash leafHash = persistence.persist(leaf);
      return buildPathToDepth(leafHash, keyHash, depth);
    }

    if (node instanceof SmtLeafNode) {
      SmtLeafNode leaf = (SmtLeafNode) node;
      if (equals32(leaf.getKeyHash(), keyHash)) {
        // Update value
        SmtLeafNode updated = leaf.withValue(value);
        return persistence.persist(updated);
      }

      // Collision: build diverging subtree
      int diverge = firstDivergingBit(leaf.getKeyHash(), keyHash, depth);

      // Build subtree for existing leaf up to diverge+1
      NodeHash existingLeafHash = persistence.persist(leaf); // persists identical encoding
      NodeHash existingSub = buildPathToDepth(existingLeafHash, leaf.getKeyHash(), diverge + 1);

      // Build subtree for new leaf up to diverge+1
      SmtLeafNode newLeaf = SmtLeafNode.of(keyHash, value);
      NodeHash newLeafHash = persistence.persist(newLeaf);
      NodeHash newSub = buildPathToDepth(newLeafHash, keyHash, diverge + 1);

      // At diverge depth, create internal with children per diverge bit
      int bitExisting = bitAt(leaf.getKeyHash(), diverge);
      NodeHash left, right;
      if (bitExisting == 0) {
        left = existingSub; right = newSub;
      } else {
        left = newSub; right = existingSub;
      }
      SmtInternalNode internal = SmtInternalNode.of(left.toBytes(), right.toBytes());
      NodeHash internalHash = persistence.persist(internal);

      // If we are deeper than diverge, we need to build the remaining path up to current depth
      // by attaching this subtree along the path from depth to diverge.
      return buildPathFromSubtree(internalHash, keyHash, depth, diverge);
    } else {
      // Internal node
      SmtInternalNode internal = (SmtInternalNode) node;
      int bit = bitAt(keyHash, depth);
      byte[] leftDigest = childDigestOrEmpty(internal.getLeft(), depth + 1);
      byte[] rightDigest = childDigestOrEmpty(internal.getRight(), depth + 1);

      if (bit == 0) {
        NodeHash newLeft = insertAt(leftDigest == null ? null : leftDigest, keyHash, value, depth + 1);
        SmtInternalNode updated = SmtInternalNode.of(newLeft.toBytes(), rightDigest == null ? EmptyCommitments.EMPTY[depth + 1] : rightDigest);
        return persistence.persist(updated);
      } else {
        NodeHash newRight = insertAt(rightDigest == null ? null : rightDigest, keyHash, value, depth + 1);
        SmtInternalNode updated = SmtInternalNode.of(leftDigest == null ? EmptyCommitments.EMPTY[depth + 1] : leftDigest, newRight.toBytes());
        return persistence.persist(updated);
      }
    }
  }

  private byte[] getAt(byte[] nodeHash, byte[] keyHash, int depth) {
    if (nodeHash == null) return null;
    SmtNode node = persistence.load(NodeHash.of(nodeHash));
    if (node == null) return null;

    if (node instanceof SmtLeafNode) {
      SmtLeafNode leaf = (SmtLeafNode) node;
      return equals32(leaf.getKeyHash(), keyHash) ? leaf.getValue() : null;
    } else {
      SmtInternalNode internal = (SmtInternalNode) node;
      int bit = bitAt(keyHash, depth);
      byte[] childDigest = bit == 0 ? childDigestOrEmpty(internal.getLeft(), depth + 1)
                                    : childDigestOrEmpty(internal.getRight(), depth + 1);
      if (childDigest == null) return null;
      // If points to EMPTY commitment, there is no stored node; return null
      if (isEmptyDigest(childDigest, depth + 1)) return null;
      return getAt(childDigest, keyHash, depth + 1);
    }
  }

  // Build a chain of internal nodes from depth to 255 that lead to the leafHash according to keyHash bits.
  private NodeHash buildPathToDepth(NodeHash leafHash, byte[] keyHash, int depth) {
    NodeHash h = leafHash;
    for (int d = 255; d >= depth; d--) {
      int bit = bitAt(keyHash, d);
      byte[] empty = EmptyCommitments.EMPTY[d + 1];
      SmtInternalNode n;
      if (bit == 0) {
        n = SmtInternalNode.of(h.toBytes(), empty);
      } else {
        n = SmtInternalNode.of(empty, h.toBytes());
      }
      h = persistence.persist(n);
    }
    return h;
  }

  // Attach an already-built subtree at level (divergeDepth) into the path from current depth up to divergeDepth-1
  private NodeHash buildPathFromSubtree(NodeHash subtreeHash, byte[] keyHash, int currentDepth, int divergeDepth) {
    NodeHash h = subtreeHash;
    for (int d = divergeDepth - 1; d >= currentDepth; d--) {
      int bit = bitAt(keyHash, d);
      byte[] empty = EmptyCommitments.EMPTY[d + 1];
      SmtInternalNode n = (bit == 0)
        ? SmtInternalNode.of(h.toBytes(), empty)
        : SmtInternalNode.of(empty, h.toBytes());
      h = persistence.persist(n);
    }
    return h;
  }

  private static boolean equals32(byte[] a, byte[] b) {
    if (a == null || b == null) return false;
    if (a.length != b.length) return false;
    int r = 0;
    for (int i = 0; i < a.length; i++) r |= (a[i] ^ b[i]);
    return r == 0;
  }

  private static int bitAt(byte[] hash, int bitIndex) {
    int byteIndex = bitIndex >>> 3; // /8
    int bitInByte = 7 - (bitIndex & 7);
    return (hash[byteIndex] >>> bitInByte) & 1;
  }

  private static int firstDivergingBit(byte[] a, byte[] b, int fromDepth) {
    for (int i = fromDepth; i < 256; i++) {
      if (bitAt(a, i) != bitAt(b, i)) return i;
    }
    return 256; // identical
  }

  private static boolean isEmptyDigest(byte[] digest, int depth) {
    byte[] empty = EmptyCommitments.EMPTY[depth];
    if (digest.length != empty.length) return false;
    for (int i = 0; i < digest.length; i++) if (digest[i] != empty[i]) return false;
    return true;
  }

  private static byte[] childDigestOrEmpty(byte[] stored, int depth) {
    if (stored == null || stored.length == 0) return null; // treat as unknown (legacy)
    return stored;
  }

  private static byte[] normalizeDigest(byte[] stored, int depth) {
    if (stored == null || stored.length == 0) return EmptyCommitments.EMPTY[depth];
    return stored;
  }

  private static final class PathEntry {
    final int depth;
    final byte[] leftDigest;
    final byte[] rightDigest;
    final boolean wentLeft;
    PathEntry(int depth, byte[] leftDigest, byte[] rightDigest, boolean wentLeft) {
      this.depth = depth;
      this.leftDigest = leftDigest;
      this.rightDigest = rightDigest;
      this.wentLeft = wentLeft;
    }
  }
}
