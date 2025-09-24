package com.bloxbean.cardano.statetrees.jmt.mode;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.jmt.JmtProof;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;

/**
 * Serializes and verifies wire proofs for a given JMT mode.
 */
public interface JmtProofCodec {
    byte[] toWire(JmtProof proof, byte[] key, HashFunction hashFn, CommitmentScheme cs);

    boolean verify(byte[] expectedRoot, byte[] key, byte[] valueOrNull, boolean including,
                   byte[] wire, HashFunction hashFn, CommitmentScheme cs);
}

