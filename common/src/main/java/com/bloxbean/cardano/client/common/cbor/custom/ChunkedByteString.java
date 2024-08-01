package com.bloxbean.cardano.client.common.cbor.custom;

import co.nstant.in.cbor.model.ByteString;

import java.util.List;

/**
 * This class is used to represent a ByteString which is chunked into multiple byte arrays. Each chunk length is less than or equal to 64 bytes
 * This is used in PlutusData serialization/deserialization when the byte array is greater than 64 bytes and need to be chunked.
 * For more details, how chunking is done in Cardano, refer the below link
 * https://github.com/IntersectMBO/plutus/blob/441b76d9e9745dfedb2afc29920498bdf632f162/plutus-core/plutus-core/src/PlutusCore/Data.hs#L243
 */
public class ChunkedByteString extends ByteString {
    private List<byte[]> chunks;

    public ChunkedByteString(List<byte[]> chunks) {
        super(new byte[0]);
        this.chunks = chunks;
        setChunked(true);
    }

    public List<byte[]> getChunks() {
        return chunks;
    }

}
