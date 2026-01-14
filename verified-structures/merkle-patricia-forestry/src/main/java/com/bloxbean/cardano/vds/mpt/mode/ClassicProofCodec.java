package com.bloxbean.cardano.vds.mpt.mode;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.mpt.mpf.MerklePatriciaProof;
import com.bloxbean.cardano.vds.mpt.commitment.CommitmentScheme;

/**
 * Placeholder for Classic (ADR-0001) node-encoding proofs.
 */
public final class ClassicProofCodec implements ProofCodec {
    @Override
    public byte[] toWire(MerklePatriciaProof proof, byte[] key, HashFunction hashFn, CommitmentScheme commitments) {
        throw new UnsupportedOperationException("Use trie.getProofWire for CLASSIC mode; codec encodes raw node lists only");
    }

    @Override
    public boolean verify(byte[] expectedRoot, byte[] key, byte[] valueOrNull, boolean including,
                          byte[] wire, HashFunction hashFn, CommitmentScheme commitments) {
        throw new UnsupportedOperationException("Classic verifier not implemented yet");
    }

    /**
     * Encodes a Classic proof as CBOR array of ByteStrings (each ByteString is a node's CBOR).
     */
    public static byte[] encodeNodeList(java.util.List<byte[]> nodes) {
        try {
            co.nstant.in.cbor.model.Array arr = new co.nstant.in.cbor.model.Array();
            for (byte[] n : nodes) arr.add(new co.nstant.in.cbor.model.ByteString(n));
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            new co.nstant.in.cbor.CborEncoder(baos).encode(arr);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode Classic proof", e);
        }
    }
}
