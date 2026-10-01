package com.bloxbean.cardano.client.plutus.spec;

import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.spec.serializers.MapDataJsonDeserializer;
import com.bloxbean.cardano.client.plutus.spec.serializers.MapDataJsonSerializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.*;

import java.util.LinkedHashMap;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonSerialize(using = MapDataJsonSerializer.class)
@JsonDeserialize(using = MapDataJsonDeserializer.class)
public class MapPlutusData implements PlutusData {

    @Builder.Default
    private java.util.Map<PlutusData, PlutusData> map = new LinkedHashMap<>();

    public static MapPlutusData deserialize(Map mapDI) throws CborDeserializationException {
        if (mapDI == null) {
            return null;
        }
        return (MapPlutusData) PlutusDataCodec.decode(mapDI);
    }

    public MapPlutusData put(PlutusData key, PlutusData value) {
        if (map == null)
            map = new LinkedHashMap<>();

        map.put(key, value);

        return this;
    }

    @Override
    public DataItem serialize() throws CborSerializationException {
        return PlutusDataCodec.encode(this);
    }

    /**
     * Value equality, computed without recursion so that data of any nesting depth can be compared.
     */
    @Override
    public boolean equals(Object o) {
        return PlutusDataCodec.equal(this, o);
    }

    @Override
    public int hashCode() {
        return PlutusDataCodec.hash(this);
    }
}
