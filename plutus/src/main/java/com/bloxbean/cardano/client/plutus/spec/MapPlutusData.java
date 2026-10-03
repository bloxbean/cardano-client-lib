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

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonSerialize(using = MapDataJsonSerializer.class)
@JsonDeserialize(using = MapDataJsonDeserializer.class)
public class MapPlutusData implements PlutusData {

    /**
     * The entries, in insertion order. By default each key's hash is computed once, when it is put, so data whose map keys
     * are maps hashes and decodes in linear time; as in any hash map, a key must not change while it is in the map.
     */
    @Builder.Default
    private java.util.Map<PlutusData, PlutusData> map = new PlutusDataMap();

    public static MapPlutusData deserialize(Map mapDI) throws CborDeserializationException {
        if (mapDI == null) {
            return null;
        }
        return (MapPlutusData) PlutusDataCodec.decode(mapDI);
    }

    public MapPlutusData put(PlutusData key, PlutusData value) {
        if (map == null)
            map = new PlutusDataMap();

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
        return PlutusDataCodec.valueEquals(this, o);
    }

    @Override
    public int hashCode() {
        return PlutusDataCodec.hash(this);
    }
}
