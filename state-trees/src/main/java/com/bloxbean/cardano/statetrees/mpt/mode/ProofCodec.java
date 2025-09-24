package com.bloxbean.cardano.statetrees.mpt.mode;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.mpt.mpf.MerklePatriciaProof;
import com.bloxbean.cardano.statetrees.mpt.commitment.CommitmentScheme;

/**
 * Codec for serialising and verifying MPT proofs in a specific mode.
 */
public interface ProofCodec {
    byte[] toWire(MerklePatriciaProof proof, byte[] key, HashFunction hashFn, CommitmentScheme commitments);

    boolean verify(byte[] expectedRoot, byte[] key, byte[] valueOrNull, boolean including,
                   byte[] wire, HashFunction hashFn, CommitmentScheme commitments);
}
