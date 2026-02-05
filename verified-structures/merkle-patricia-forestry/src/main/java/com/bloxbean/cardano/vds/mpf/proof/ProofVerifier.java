package com.bloxbean.cardano.vds.mpf.proof;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.hash.Blake2b256;
import com.bloxbean.cardano.vds.mpf.commitment.CommitmentScheme;
import com.bloxbean.cardano.vds.mpf.commitment.MpfCommitmentScheme;

import java.util.Arrays;
import java.util.Objects;

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
        WireProof proof = ProofDecoder.decode(proofCbor);
        byte[] computed = proof.computeRoot(key, value, including, hashFn, commitments);
        byte[] normalizedComputed = computed == null ? commitments.nullHash() : computed;
        byte[] normalizedExpected = expectedRoot == null ? commitments.nullHash() : expectedRoot;
        return Arrays.equals(normalizedExpected, normalizedComputed);
    }
}
