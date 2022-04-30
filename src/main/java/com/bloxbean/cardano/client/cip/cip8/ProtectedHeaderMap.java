package com.bloxbean.cardano.client.cip.cip8;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.MajorType;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.transaction.util.CborSerializationUtil;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProtectedHeaderMap {
    private byte[] bytes;

    public ProtectedHeaderMap(HeaderMap headerMap) {
        try {
            bytes = CborSerializationUtil.serialize(headerMap.serialize());
        } catch (CborException e) {
            throw new CborRuntimeException("HeaderMap serialization error", e);
        }
    }

    public static ProtectedHeaderMap deserialize(DataItem dataItem) {
        if (MajorType.BYTE_STRING.equals(dataItem.getMajorType())) {
            byte[] bytes = ((ByteString) dataItem).getBytes();
            return new ProtectedHeaderMap(bytes);
        } else {
            throw new CborRuntimeException(
                    String.format("Deserialization error: Invalid type for ProtectedHeaderMap, type: %s, " +
                            "expected type: ByteString" + dataItem.getMajorType()));
        }
    }

    public DataItem serialize() {
        if (bytes != null)
            return new ByteString(bytes);
        else
            return null;
    }
}
