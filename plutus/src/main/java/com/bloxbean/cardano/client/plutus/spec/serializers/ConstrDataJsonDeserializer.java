package com.bloxbean.cardano.client.plutus.spec.serializers;

import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.plutus.spec.serializers.PlutusDataJsonKeys.CONSTRUCTOR;
import static com.bloxbean.cardano.client.plutus.spec.serializers.PlutusDataJsonKeys.FIELDS;

public class ConstrDataJsonDeserializer extends StdDeserializer<ConstrPlutusData> {

    public ConstrDataJsonDeserializer() {
        this(null);
    }

    public ConstrDataJsonDeserializer(Class<?> clazz) {
        super(clazz);
    }

    @Override
    public ConstrPlutusData deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException {
        JsonNode node = jp.getCodec().readTree(jp);
        if (!node.has(CONSTRUCTOR))
            throw new IllegalArgumentException("Invalid json for ConstrPlutusData. " + node);

        long alternative = node.get(CONSTRUCTOR).asLong();

        ArrayNode fieldsNode = (ArrayNode) node.get(FIELDS);
        List<PlutusData> plutusDataList = new ArrayList<>();
        for (int i = 0; i < fieldsNode.size(); i++) {
            plutusDataList.add(PlutusDataJsonConverter.toPlutusData(fieldsNode.get(i)));
        }

        return ConstrPlutusData.of(alternative, plutusDataList.toArray(new PlutusData[0]));
    }
}
