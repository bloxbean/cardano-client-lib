package com.bloxbean.cardano.vds.jmt.commitment;

import com.bloxbean.cardano.vds.core.NibblePath;

/**
 * Computes node commitments (hashes) for the Jellyfish Merkle Tree.
 */
public interface CommitmentScheme {

    /**
     * Hashes a branch/internal node using its prefix and child commitments.
     *
     * @param prefix      nibble path accumulated at the node
     * @param childHashes ordered array of 16 child commitments (nullable entries treated as empty)
     * @return commitment for the branch node
     */
    byte[] commitBranch(NibblePath prefix, byte[][] childHashes);

    /**
     * Hashes a leaf node by binding both the key hash and the value hash.
     *
     * <p>The key hash MUST be part of the leaf commitment (using the Diem-style
     * {@code H(tag || keyHash || valueHash)}). Committing only to a path suffix
     * leaves the leaf unbound to its key and makes inclusion/non-inclusion proofs
     * forgeable, so this contract deliberately takes the full key hash.</p>
     *
     * @param keyHash   digest of the key stored at the leaf (never null)
     * @param valueHash digest of the value stored at the leaf (never null)
     * @return commitment for the leaf node
     */
    byte[] commitLeaf(byte[] keyHash, byte[] valueHash);

    /**
     * Commitment representing an empty subtree.
     */
    byte[] nullHash();
}
