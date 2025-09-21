package com.bloxbean.cardano.statetrees.smt;

import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import java.io.ByteArrayOutputStream;

/**
 * Precomputed empty subtree commitments for SMT (depth 0..256).
 *
 * <p>Empty commitments represent the hash values of completely empty subtrees
 * at each depth level in the SMT. These are computed once and cached for
 * efficiency, as they are frequently used during SMT operations.</p>
 *
 * <p><b>Computation Rules:</b></p>
 * <ul>
 *   <li>EMPTY[256] = H(0x00) - the base commitment for the deepest level</li>
 *   <li>EMPTY[d] = H([0, EMPTY[d+1], EMPTY[d+1]]) for d ∈ [0..255]</li>
 * </ul>
 *
 * <p>Where H([0, left, right]) represents the hash of an internal node
 * with tag=0 and the given left/right child hashes, encoded in CBOR format.</p>
 *
 * <p><b>Usage:</b> These commitments are used when:</p>
 * <ul>
 *   <li>A subtree is completely empty during traversal</li>
 *   <li>Computing proof siblings for non-existent paths</li>
 *   <li>Normalizing null child references in internal nodes</li>
 * </ul>
 *
 * @since 0.8.0
 */
final class EmptyCommitments {
    
    /** Array of empty commitments indexed by tree depth (0..256). */
    static final byte[][] EMPTY = new byte[257][];

    static {
        // Base at depth 256: hash of a single zero byte
        EMPTY[256] = Blake2b256.digest(new byte[] { 0x00 });
        
        // For each level, compute hash of internal node with identical empty children
        // EMPTY[d] = H( [0, EMPTY[d+1], EMPTY[d+1]] ) using SMT internal node encoding
        for (int d = 255; d >= 0; d--) {
            byte[] child = EMPTY[d + 1];
            EMPTY[d] = internalDigest(child, child);
        }
    }

    /**
     * Private constructor to prevent instantiation.
     */
    private EmptyCommitments() {}

    /**
     * Computes the digest of an internal node with the given left and right children.
     *
     * @param left the left child hash
     * @param right the right child hash
     * @return the Blake2b-256 hash of the CBOR-encoded internal node
     */
    private static byte[] internalDigest(byte[] left, byte[] right) {
        try {
            Array arr = new Array();
            arr.add(new ByteString(new byte[] { 0 }));  // tag=0 for internal node
            arr.add(new ByteString(left));
            arr.add(new ByteString(right));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new CborEncoder(baos).encode(arr);
            return Blake2b256.digest(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute empty commitment digest", e);
        }
    }
}
