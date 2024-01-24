package com.bloxbean.cardano.client.plutus.spec;

import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnicodeString;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.spec.serializers.BytesDataJsonDeserializer;
import com.bloxbean.cardano.client.plutus.spec.serializers.BytesDataJsonSerializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.*;

import java.nio.charset.StandardCharsets;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
@JsonSerialize(using = BytesDataJsonSerializer.class)
@JsonDeserialize(using = BytesDataJsonDeserializer.class)
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

    public static BytesPlutusData deserialize(UnicodeString valueDI) throws CborDeserializationException {
        if (valueDI == null)
            return null;

        return BytesPlutusData.of(valueDI.getString());
    }
}
