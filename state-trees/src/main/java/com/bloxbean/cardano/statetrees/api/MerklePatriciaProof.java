package com.bloxbean.cardano.statetrees.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Proof for Merkle Patricia Trie inclusion or non-inclusion queries.
 *
 * <p>The proof stores the CBOR-encoded nodes encountered while traversing the trie
 * from the root towards a requested key. Depending on how the traversal terminates,
 * the proof type communicates whether the key was found or why it is absent.</p>
 *
 * @since 0.9.0
 */
public final class MerklePatriciaProof {

  /**
   * Classification of proof outcome.
   */
  public enum Type {
    /** Key and value exist in the trie. */
    INCLUSION,
    /** Traversal required a child branch that does not exist. */
    NON_INCLUSION_MISSING_BRANCH,
    /** Traversal reached a leaf whose HP suffix does not match the requested key. */
    NON_INCLUSION_DIFFERENT_LEAF
  }

  private final Type type;
  private final List<byte[]> nodes;
  private final byte[] value;

  /**
   * Creates a proof instance.
   *
   * @param type proof outcome
   * @param nodes ordered list of CBOR-encoded nodes from root to terminal node
   * @param value associated value for inclusion proofs, null otherwise
   */
  public MerklePatriciaProof(Type type, List<byte[]> nodes, byte[] value) {
    this.type = Objects.requireNonNull(type, "type");
    this.nodes = deepCopy(nodes);
    this.value = value == null ? null : value.clone();
  }

  /**
   * Returns the proof type.
   */
  public Type getType() {
    return type;
  }

  /**
   * Returns the CBOR-encoded nodes that make up the proof.
   *
   * <p>A defensive copy is returned to preserve immutability.</p>
   */
  public List<byte[]> getNodes() {
    return deepCopy(nodes);
  }

  /**
   * Returns the associated value for inclusion proofs; null otherwise.
   */
  public byte[] getValue() {
    return value == null ? null : value.clone();
  }

  private static List<byte[]> deepCopy(List<byte[]> source) {
    Objects.requireNonNull(source, "nodes");
    List<byte[]> copy = new ArrayList<>(source.size());
    for (byte[] entry : source) {
      if (entry == null) {
        throw new IllegalArgumentException("Proof nodes must not contain null entries");
      }
      copy.add(entry.clone());
    }
    return Collections.unmodifiableList(copy);
  }
}
