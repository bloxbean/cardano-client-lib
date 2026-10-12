package com.bloxbean.cardano.client.common.cbor.custom;

import co.nstant.in.cbor.model.ByteString;

import java.util.Arrays;
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

    /**
     * Equal to another chunked byte string with the same chunks (and tags). {@link ByteString#equals(Object)} compares
     * the bytes passed to its constructor, which are empty here, so any two chunked byte strings were equal, and two
     * map keys over 64 bytes collided.
     */
    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (!(object instanceof ChunkedByteString) || !super.equals(object))
            return false;
        List<byte[]> otherChunks = ((ChunkedByteString) object).chunks;
        if (chunks.size() != otherChunks.size())
            return false;
        for (int i = 0; i < chunks.size(); i++) {
            if (!Arrays.equals(chunks.get(i), otherChunks.get(i)))
                return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        for (byte[] chunk : chunks)
            hash = 31 * hash + Arrays.hashCode(chunk);
        return hash;
    }

}
