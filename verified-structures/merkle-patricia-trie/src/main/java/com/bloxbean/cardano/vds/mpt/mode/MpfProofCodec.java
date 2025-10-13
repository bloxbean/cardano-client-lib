package com.bloxbean.cardano.vds.mpt.mode;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.mpt.mpf.MerklePatriciaProof;
import com.bloxbean.cardano.vds.mpt.commitment.CommitmentScheme;
import com.bloxbean.cardano.vds.mpt.mpf.MpfProofSerializer;
import com.bloxbean.cardano.vds.mpt.mpf.MpfProofVerifier;

/**
 * MPF CBOR proof codec.
 */
final class MpfProofCodec implements ProofCodec {
    @Override
    public byte[] toWire(MerklePatriciaProof proof, byte[] key, HashFunction hashFn, CommitmentScheme commitments) {
        return MpfProofSerializer.toCbor(proof, key, hashFn, commitments);
    }

    @Override
    public boolean verify(byte[] expectedRoot, byte[] key, byte[] valueOrNull, boolean including,
                          byte[] wire, HashFunction hashFn, CommitmentScheme commitments) {
        return MpfProofVerifier.verify(expectedRoot, key, valueOrNull, including, wire, hashFn, commitments);
    }
}
