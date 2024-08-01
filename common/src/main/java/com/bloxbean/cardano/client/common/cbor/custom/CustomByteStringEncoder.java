package com.bloxbean.cardano.client.common.cbor.custom;

import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.encoder.ByteStringEncoder;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.MajorType;
import co.nstant.in.cbor.model.SimpleValue;

import java.io.OutputStream;
import java.util.List;

/**
 * A custom ByteStringEncoder impl to handle serialization of {@link ChunkedByteString} in PlutusData.
 */
public class CustomByteStringEncoder extends ByteStringEncoder {
    public CustomByteStringEncoder(CborEncoder encoder, OutputStream outputStream) {
        super(encoder, outputStream);
    }

    @Override
    public void encode(ByteString byteString) throws CborException {
        if (byteString instanceof ChunkedByteString) {
            List<byte[]> chunks = ((ChunkedByteString) byteString).getChunks();
            this.encodeTypeChunked(MajorType.BYTE_STRING);
            for (byte[] chunk : chunks) {
                this.encode(new ByteString(chunk));
            }
            this.encoder.encode(SimpleValue.BREAK);
        } else {
            super.encode(byteString);
        }
    }
}
