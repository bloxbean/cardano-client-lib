package com.bloxbean.cardano.vds.mpf.proof;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.mpf.commitment.CommitmentScheme;
import com.bloxbean.cardano.vds.mpf.commitment.MpfCommitmentScheme;

import java.util.Arrays;
import java.util.Objects;

/**
 * Verifies MPF wire proofs with the same semantics as Aiken's {@code aiken-lang/merkle-patricia-forestry}
 * ({@code has} for inclusion, {@code miss} for non-inclusion).
 *
 * <p>Verification never throws for a malformed or mismatching proof; it returns {@code false}. A {@code null}
 * expected root and the all-zero root (Aiken's {@code mpf.empty}) both denote the empty trie.</p>
 */
public final class ProofVerifier {

    private ProofVerifier() {
        throw new AssertionError("Utility class");
    }

    /**
     * Verifies a proof using Cardano/Aiken compatible defaults (Blake2b-256, MPF commitment scheme).
     *
     * @param expectedRoot the expected root hash
     * @param key          the key being proven
     * @param value        the expected value (null for non-inclusion proofs)
     * @param including    true for inclusion proof, false for non-inclusion proof
     * @param proofCbor    the CBOR-encoded proof bytes
     * @return true if the proof is valid
     */
    public static boolean verify(byte[] expectedRoot, byte[] key, byte[] value,
                                 boolean including, byte[] proofCbor) {
        return verify(expectedRoot, key, value, including, proofCbor,
                Blake2b256::digest, new MpfCommitmentScheme(Blake2b256::digest));
    }

    /**
     * Verifies a proof with custom hash function and commitment scheme.
     *
     * @param expectedRoot the expected root hash
     * @param key          the key being proven
     * @param value        the expected value (null for non-inclusion proofs)
     * @param including    true for inclusion proof, false for non-inclusion proof
     * @param proofCbor    the CBOR-encoded proof bytes
     * @param hashFn       the hash function to use
     * @param commitments  the commitment scheme to use
     * @return true if the proof is valid
     */
    public static boolean verify(byte[] expectedRoot, byte[] key, byte[] value,
                                 boolean including, byte[] proofCbor,
                                 HashFunction hashFn, CommitmentScheme commitments) {
        Objects.requireNonNull(proofCbor, "proofCbor");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(hashFn, "hashFn");
        Objects.requireNonNull(commitments, "commitments");
        byte[] nullHash = commitments.nullHash();
        byte[] computed;
        try {
            WireProof proof = ProofDecoder.decode(proofCbor, nullHash.length);
            if (including && value == null) {
                return false;
            }
            computed = proof.computeRoot(key, value, including, hashFn, commitments);
        } catch (IllegalArgumentException e) {
            return false;
        }
        byte[] normalizedComputed = computed == null ? nullHash : computed;
        byte[] normalizedExpected = expectedRoot == null || expectedRoot.length == 0 ? nullHash : expectedRoot;
        return Arrays.equals(normalizedExpected, normalizedComputed);
    }
}
