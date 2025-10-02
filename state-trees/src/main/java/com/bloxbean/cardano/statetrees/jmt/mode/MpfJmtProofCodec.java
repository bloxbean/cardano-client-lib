package com.bloxbean.cardano.statetrees.jmt.mode;

import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.jmt.JmtProof;
import com.bloxbean.cardano.statetrees.jmt.commitment.CommitmentScheme;
import com.bloxbean.cardano.statetrees.jmt.mpf.MpfProofSerializer;
import com.bloxbean.cardano.statetrees.jmt.mpf.MpfProofVerifier;

/**
 * MPF wire codec adapter for JMT.
 */
final class MpfJmtProofCodec implements JmtProofCodec {

    @Override
    public byte[] toWire(JmtProof proof, byte[] key, HashFunction hashFn, CommitmentScheme cs) {
        return MpfProofSerializer.toCbor(proof, key, hashFn, cs);
    }

    @Override
    public boolean verify(byte[] expectedRoot, byte[] key, byte[] valueOrNull, boolean including,
                          byte[] wire, HashFunction hashFn, CommitmentScheme cs) {
        return MpfProofVerifier.verify(expectedRoot, key, valueOrNull, including, wire, hashFn, cs);
    }
}
