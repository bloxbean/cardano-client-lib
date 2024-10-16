package com.bloxbean.cardano.client.cip.cip8;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.MajorType;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class ProtectedHeaderMap implements COSEItem {
    private final byte[] bytes;

    public ProtectedHeaderMap() {
        this.bytes = new byte[0];
    }

    public ProtectedHeaderMap(byte[] bytes) {
        this.bytes = bytes;
    }

    public ProtectedHeaderMap(HeaderMap headerMap) {
        bytes = headerMap.serializeAsBytes();
    }

    public static ProtectedHeaderMap deserialize(DataItem dataItem) {
        if (MajorType.BYTE_STRING.equals(dataItem.getMajorType())) {
            byte[] bytes = ((ByteString) dataItem).getBytes();
            return new ProtectedHeaderMap(bytes);
        } else {
            throw new CborRuntimeException(
                    String.format("Deserialization error: Invalid type for ProtectedHeaderMap, type: %s, " +
                            "expected type: ByteString",  dataItem.getMajorType()));
        }
    }

    public DataItem serialize() {
        if (bytes != null)
            return new ByteString(bytes);
        else
            return new ByteString(new byte[0]);
    }

    public HeaderMap getAsHeaderMap() {
        if (bytes == null)
            return null;

        try {
            DataItem dataItem = CborDecoder.decode(bytes).get(0);
            return HeaderMap.deserialize(dataItem);
        } catch (CborException e) {
            throw new CborRuntimeException("De-serialization error", e);
        }
    }
}
