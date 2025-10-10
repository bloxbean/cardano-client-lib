package com.bloxbean.cardano.statetrees.mpt.commitment;

import com.bloxbean.cardano.statetrees.common.NibblePath;

/**
 * Computes MPF-compatible commitments for Merkle Patricia Trie nodes.
 */
public interface CommitmentScheme {

    /**
     * Computes the commitment for a branch node.
     *
     * @param prefix      nibble path (possibly empty) skipped at this branch
     * @param childHashes ordered array of 16 child commitments (null entries treated as empty subtrees)
     * @param valueHash   optional commitment for a value terminating at this branch (null if unused)
     * @return 32-byte commitment digest
     */
    byte[] commitBranch(NibblePath prefix, byte[][] childHashes, byte[] valueHash);

    /**
     * Computes the commitment for a leaf node.
     *
     * @param suffix    nibble path representing the remaining key segment stored in the leaf
     * @param valueHash digest of the value stored at the leaf (must match the scheme's digest size)
     * @return 32-byte commitment digest
     */
    byte[] commitLeaf(NibblePath suffix, byte[] valueHash);

    /**
     * Applies a compressed path prefix to a child commitment (extension node).
     *
     * @param path      compressed nibble path leading to the child
     * @param childHash commitment of the child subtree
     * @return 32-byte commitment digest representing the extension node
     */
    byte[] commitExtension(NibblePath path, byte[] childHash);

    /**
     * Returns the canonical commitment representing an empty subtree.
     */
    byte[] nullHash();

    /**
     * Indicates whether this scheme encodes a branch-terminal value inside the branch
     * commitment. If true, proofs should include the branch value hash when present;
     * if false, the branch value slot is ignored at commitment time and must not be
     * represented in MPF proof steps.
     *
     * <p>Default: true (classic MPT-like behavior). MPF mode overrides to false.</p>
     */
    default boolean encodesBranchValueInBranchCommitment() {
        return true;
    }
}
