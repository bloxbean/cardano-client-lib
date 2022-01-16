package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BytesPlutusData implements PlutusData {
    private byte[] value;

    public static BytesPlutusData deserialize(ByteString valueDI) throws CborDeserializationException {
        if (valueDI == null)
            return null;

        return new BytesPlutusData(valueDI.getBytes());
    }

    public static BytesPlutusData of(byte[] bytes) {
        return new BytesPlutusData(bytes);
    }

    public static BytesPlutusData of(String str) {
        return new BytesPlutusData(str.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public DataItem serialize() throws CborSerializationException {
        DataItem di = null;
        if (value != null) {
            di = new ByteString(value);
        }

        return di;
    }
}
