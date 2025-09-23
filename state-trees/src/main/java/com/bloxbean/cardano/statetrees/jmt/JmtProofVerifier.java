package com.bloxbean.cardano.statetrees.jmt;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.NibblePath;
import com.bloxbean.cardano.statetrees.common.nibbles.Nibbles;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;

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
        List<JmtProof.BranchStep> steps = proof.steps();

        switch (proof.type()) {
            case INCLUSION:
                if (value == null) return false;
                return verifyInclusion(rootHash, value, proof, hashFn, commitments, keyHash, nibbles);
            case NON_INCLUSION_EMPTY:
                return verifyEmpty(rootHash, proof, commitments, nibbles);
            case NON_INCLUSION_DIFFERENT_LEAF:
                return verifyDifferentLeaf(rootHash, proof, commitments, keyHash, nibbles);
            default:
                throw new IllegalStateException("Unhandled proof type: " + proof.type());
        }
    }

    private static boolean verifyInclusion(byte[] rootHash, byte[] value, JmtProof proof,
                                           HashFunction hashFn, CommitmentScheme commitments,
                                           byte[] keyHash, int[] nibbles) {
        if (!Arrays.equals(keyHash, proof.leafKeyHash())) return false;
        byte[] valueHash = hashFn.digest(value);
        if (!Arrays.equals(valueHash, proof.valueHash())) return false;

        byte[] hash = commitments.commitLeaf(proof.suffix(), valueHash);
        hash = ascend(hash, proof.steps(), commitments, nibbles);
        return Arrays.equals(rootHash, hash);
    }

    private static boolean verifyEmpty(byte[] rootHash, JmtProof proof,
                                       CommitmentScheme commitments, int[] nibbles) {
        byte[] hash = commitments.nullHash();
        hash = ascend(hash, proof.steps(), commitments, nibbles);
        return Arrays.equals(rootHash, hash);
    }

    private static boolean verifyDifferentLeaf(byte[] rootHash, JmtProof proof,
                                               CommitmentScheme commitments,
                                               byte[] keyHash, int[] nibbles) {
        if (Arrays.equals(keyHash, proof.conflictingKeyHash())) return false;
        byte[] hash = commitments.commitLeaf(proof.conflictingSuffix(), proof.conflictingValueHash());
        hash = ascend(hash, proof.steps(), commitments, nibbles);
        return Arrays.equals(rootHash, hash);
    }

    private static byte[] ascend(byte[] leafHash, List<JmtProof.BranchStep> steps,
                                 CommitmentScheme commitments, int[] nibbles) {
        byte[] hash = Arrays.copyOf(leafHash, leafHash.length);
        for (int i = steps.size() - 1; i >= 0; i--) {
            JmtProof.BranchStep step = steps.get(i);
            byte[][] childHashes = step.childHashes();
            if (childHashes.length != 16) {
                throw new IllegalArgumentException("Branch step must contain 16 child hashes");
            }
            int nibble = nibbles[i];
            childHashes[nibble] = Arrays.copyOf(hash, hash.length);
            hash = commitments.commitBranch(step.prefix(), childHashes);
        }
        return hash;
    }
}

