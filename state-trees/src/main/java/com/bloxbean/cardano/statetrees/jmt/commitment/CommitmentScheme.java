package com.bloxbean.cardano.statetrees.jmt.commitment;

import com.bloxbean.cardano.statetrees.common.NibblePath;

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
     * Hashes a leaf node using the compressed suffix and the hashed value.
     *
     * @param suffix    remaining path from the branch to the leaf (HP suffix)
     * @param valueHash digest of the value stored at the leaf
     * @return commitment for the leaf node
     */
    byte[] commitLeaf(NibblePath suffix, byte[] valueHash);

    /**
     * Commitment representing an empty subtree.
     */
    byte[] nullHash();
}

