package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashMap;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MapPlutusData implements PlutusData {

    @Builder.Default
    private java.util.Map<PlutusData, PlutusData> map = new HashMap<>();

    public static MapPlutusData deserialize(Map mapDI) throws CborDeserializationException {
        if (mapDI == null) {
            return null;
        }

        MapPlutusData mapPlutusData = new MapPlutusData();
        for (DataItem keyDI : mapDI.getKeys()) {
            PlutusData key = PlutusData.deserialize(keyDI);
            PlutusData value = PlutusData.deserialize(mapDI.get(keyDI));

            mapPlutusData.put(key, value);
        }

        return mapPlutusData;
    }

    public MapPlutusData put(PlutusData key, PlutusData value) {
        if (map == null)
            map = new HashMap<>();

        map.put(key, value);

        return this;
    }

    @Override
    public DataItem serialize() throws CborSerializationException {
        if (map == null)
            return null;

        Map plutusDataMap = new Map();
        for (java.util.Map.Entry<PlutusData, PlutusData> entry : map.entrySet()) {
            DataItem key = entry.getKey().serialize();
            DataItem value = entry.getValue().serialize();

            if (key == null)
                throw new CborSerializationException("Cbor serialization failed for PlutusData.  NULL serialized value found for key");

            if (value == null)
                throw new CborSerializationException("Cbor serialization failed for PlutusData.  NULL serialized value found for value");

            plutusDataMap.put(key, value);
        }

        return plutusDataMap;
    }
}
