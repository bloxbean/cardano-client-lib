package com.bloxbean.cardano.statetrees.api;

/**
 * Sparse Merkle proof for inclusion or non-inclusion (empty path).
 *
 * <p>A proof consists of the sibling digests for each tree depth (0..255).
 * For inclusion proofs, the associated value is present; for non-inclusion,
 * value is null and the path leads to an empty commitment.</p>
 *
 * @since 0.8.0
 */
public final class SparseMerkleProof {

  /** Indicates whether this is an inclusion or non-inclusion proof. */
  public enum Type { INCLUSION, NON_INCLUSION_EMPTY }

  private final Type type;
  private final byte[][] siblings; // length 256, siblings[depth] = sibling at depth
  private final byte[] value; // only for inclusion; null for non-inclusion

  /**
   * Constructs a proof.
   *
   * @param type proof type
   * @param siblings array of 256 sibling hashes
   * @param value value for inclusion, null for non-inclusion
   */
  public SparseMerkleProof(Type type, byte[][] siblings, byte[] value) {
    this.type = type;
    this.siblings = siblings;
    this.value = value;
  }

  /** @return the proof type */
  public Type getType() { return type; }

  /** @return array of 256 sibling digests */
  public byte[][] getSiblings() { return siblings; }

/** @return associated value for inclusion proofs; null otherwise */
  public byte[] getValue() { return value; }
}

