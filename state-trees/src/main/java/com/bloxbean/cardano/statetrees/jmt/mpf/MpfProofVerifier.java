package com.bloxbean.cardano.statetrees.jmt.mpf;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;

import java.util.Arrays;
import java.util.Objects;

public final class MpfProofVerifier {

  private MpfProofVerifier() {
    throw new AssertionError("Utility class");
  }

  public static boolean verify(byte[] expectedRoot, byte[] key, byte[] value,
                               boolean including, byte[] proofCbor,
                               HashFunction hashFn, CommitmentScheme commitments) {
    Objects.requireNonNull(proofCbor, "proofCbor");
    MpfProof proof = MpfProofDecoder.decode(proofCbor);
    byte[] computed = proof.computeRoot(key, value, including, hashFn, commitments);
    byte[] normalizedComputed = computed == null ? commitments.nullHash() : computed;
    byte[] normalizedExpected = expectedRoot == null ? commitments.nullHash() : expectedRoot;
    return Arrays.equals(normalizedExpected, normalizedComputed);
  }
}

