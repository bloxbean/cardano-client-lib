package com.bloxbean.cardano.client.transaction.spec.serializers;

import com.bloxbean.cardano.client.transaction.spec.ListPlutusData;
import com.bloxbean.cardano.client.transaction.spec.PlutusData;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.transaction.spec.serializers.PlutusDataJsonKeys.LIST;

public class ListDataJsonDeserializer extends StdDeserializer<ListPlutusData> {

    public ListDataJsonDeserializer() {
        this(null);
    }

    public ListDataJsonDeserializer(Class<?> clazz) {
        super(clazz);
    }

    @Override
    public ListPlutusData deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException {
        JsonNode node = jp.getCodec().readTree(jp);
        if (!node.has(LIST))
            throw new IllegalArgumentException("Invalid json for ListPlutusData : " + node);

        ArrayNode arrayNode = (ArrayNode) node.get(LIST);
        List<PlutusData> plutusDataList = new ArrayList<>();
        for (int i = 0; i < arrayNode.size(); i++) {
            JsonNode itemNode = arrayNode.get(i);
            plutusDataList.add(PlutusDataJsonConverter.toPlutusData(itemNode));
        }

        return ListPlutusData.of(plutusDataList.toArray(new PlutusData[0]));
    }
}
