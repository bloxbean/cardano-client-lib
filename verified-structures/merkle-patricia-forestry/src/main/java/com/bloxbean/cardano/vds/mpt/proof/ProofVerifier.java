package com.bloxbean.cardano.vds.mpt.proof;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.mpt.commitment.CommitmentScheme;

import java.util.Arrays;
import java.util.Objects;

public final class ProofVerifier {

    private ProofVerifier() {
        throw new AssertionError("Utility class");
    }

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
