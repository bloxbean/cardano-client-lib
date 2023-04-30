package com.bloxbean.cardano.client.plutus.spec.serializers;

import com.bloxbean.cardano.client.plutus.spec.MapPlutusData;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;

import static com.bloxbean.cardano.client.plutus.spec.serializers.PlutusDataJsonKeys.*;

public class MapDataJsonDeserializer extends StdDeserializer<MapPlutusData> {

    public MapDataJsonDeserializer() {
        this(null);
    }

    public MapDataJsonDeserializer(Class<?> clazz) {
        super(clazz);
    }

    @Override
    public MapPlutusData deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException {
        JsonNode node = jp.getCodec().readTree(jp);
        if (!node.has(MAP))
            throw new IllegalArgumentException("Invalid json for MapPlutusData : " + node);

        ArrayNode arrayNode = (ArrayNode) node.get(MAP);
        MapPlutusData mapPlutusData = new MapPlutusData();
        for (int i = 0; i < arrayNode.size(); i++) {
            JsonNode itemNode = arrayNode.get(i);
            JsonNode keyNode = itemNode.get(MAP_KEY);
            JsonNode valueNode = itemNode.get(MAP_VALUE);
            mapPlutusData.put(PlutusDataJsonConverter.toPlutusData(keyNode), PlutusDataJsonConverter.toPlutusData(valueNode));
        }

        return mapPlutusData;
    }
}
