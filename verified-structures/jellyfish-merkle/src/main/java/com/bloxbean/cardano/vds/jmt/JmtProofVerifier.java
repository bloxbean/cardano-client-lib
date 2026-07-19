package com.bloxbean.cardano.vds.jmt;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.NibblePath;
import com.bloxbean.cardano.vds.core.nibbles.Nibbles;
import com.bloxbean.cardano.vds.jmt.commitment.CommitmentScheme;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Verifies {@link JmtProof} instances against a root commitment.
 */
public final class JmtProofVerifier {

    private JmtProofVerifier() {
        throw new AssertionError("Utility class");
    }

    public static boolean verify(byte[] rootHash, byte[] key, byte[] value,
                                 JmtProof proof, HashFunction hashFn, CommitmentScheme commitments) {
        Objects.requireNonNull(rootHash, "rootHash");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(proof, "proof");
        Objects.requireNonNull(hashFn, "hashFn");
        Objects.requireNonNull(commitments, "commitments");

        byte[] keyHash = hashFn.digest(key);
        int[] nibbles = Nibbles.toNibbles(keyHash);

        // Fail LOUDLY on genuine misconfiguration (a caller bug, not attacker input): the hash
        // function's digest length must match the commitment scheme's. Detecting this before the
        // catch below means such a setup error is not silently reported as "proof invalid".
        int schemeDigestLength = commitments.nullHash().length;
        if (keyHash.length != schemeDigestLength) {
            throw new IllegalArgumentException("hashFn digest length " + keyHash.length
                    + " does not match commitment scheme digest length " + schemeDigestLength);
        }
        if (rootHash.length != schemeDigestLength) return false;

        // The proof contents are untrusted: a malformed/structurally invalid proof is "invalid"
        // (false), never an unchecked exception surfaced to the caller.
        try {
            switch (proof.type()) {
                case INCLUSION:
                    if (value == null) return false;
                    return verifyInclusion(rootHash, value, proof, hashFn, commitments, keyHash, nibbles);
                case NON_INCLUSION_EMPTY:
                    return verifyEmpty(rootHash, proof, commitments, nibbles);
                case NON_INCLUSION_DIFFERENT_LEAF:
                    return verifyDifferentLeaf(rootHash, proof, commitments, keyHash, nibbles);
                default:
                    return false;
            }
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean verifyInclusion(byte[] rootHash, byte[] value, JmtProof proof,
                                           HashFunction hashFn, CommitmentScheme commitments,
                                           byte[] keyHash, int[] nibbles) {
        if (proof.steps().size() > nibbles.length) return false;
        byte[] valueHash = hashFn.digest(value);
        // Derive the leaf commitment from the QUERIED key + supplied value. We never trust
        // proof.suffix()/proof.leafKeyHash(): a sound proof is one whose reconstruction from
        // the queried key alone reproduces the trusted root.
        byte[] hash = commitments.commitLeaf(keyHash, valueHash);
        hash = ascend(hash, proof.steps(), commitments, nibbles);
        return Arrays.equals(rootHash, hash);
    }

    private static boolean verifyEmpty(byte[] rootHash, JmtProof proof,
                                       CommitmentScheme commitments, int[] nibbles) {
        if (proof.steps().size() > nibbles.length) return false;
        byte[] hash = commitments.nullHash();
        hash = ascend(hash, proof.steps(), commitments, nibbles);
        return Arrays.equals(rootHash, hash);
    }

    private static boolean verifyDifferentLeaf(byte[] rootHash, JmtProof proof,
                                               CommitmentScheme commitments,
                                               byte[] keyHash, int[] nibbles) {
        byte[] conflictingKeyHash = proof.conflictingKeyHash();
        byte[] conflictingValueHash = proof.conflictingValueHash();
        if (conflictingKeyHash == null || conflictingValueHash == null) return false;
        // The conflicting leaf must be a DIFFERENT key that lies on the queried key's path.
        if (Arrays.equals(keyHash, conflictingKeyHash)) return false;
        int depth = proof.steps().size();
        if (depth > nibbles.length) return false;
        int[] conflictingNibbles = Nibbles.toNibbles(conflictingKeyHash);
        if (conflictingNibbles.length < depth) return false;
        for (int i = 0; i < depth; i++) {
            if (conflictingNibbles[i] != nibbles[i]) return false;
        }
        // Leaf commitment binds the conflicting key hash, so it cannot be swapped for the
        // real leaf of a present key without failing the != check above.
        byte[] hash = commitments.commitLeaf(conflictingKeyHash, conflictingValueHash);
        hash = ascend(hash, proof.steps(), commitments, nibbles);
        return Arrays.equals(rootHash, hash);
    }

    private static byte[] ascend(byte[] leafHash, List<JmtProof.BranchStep> steps,
                                 CommitmentScheme commitments, int[] nibbles) {
        byte[] hash = Arrays.copyOf(leafHash, leafHash.length);
        byte[] nullHash = commitments.nullHash();
        for (int i = steps.size() - 1; i >= 0; i--) {
            JmtProof.BranchStep step = steps.get(i);
            byte[][] childHashes = step.childHashes();
            if (childHashes.length != 16) {
                throw new IllegalArgumentException("Branch step must contain 16 child hashes");
            }
            int nibble = nibbles[i];
            if (step.prefix().length() != i || step.childIndex() != nibble) {
                throw new IllegalArgumentException("Branch step does not match queried key path");
            }
            if (childHashes[nibble] != null || !Arrays.equals(hash, nullHash)) {
                childHashes[nibble] = Arrays.copyOf(hash, hash.length);
            }
            hash = commitments.commitBranch(step.prefix(), childHashes);
        }
        return hash;
    }
}
