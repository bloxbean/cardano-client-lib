package com.bloxbean.cardano.client.transaction.spec.serializers;

import com.bloxbean.cardano.client.transaction.spec.BigIntPlutusData;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.math.BigInteger;

import static com.bloxbean.cardano.client.transaction.spec.serializers.PlutusDataJsonKeys.INT;

public class BigIntDataJsonDeserializer extends StdDeserializer<BigIntPlutusData> {

    public BigIntDataJsonDeserializer() {
        this(null);
    }

    public BigIntDataJsonDeserializer(Class<?> clazz) {
        super(clazz);
    }

    @Override
    public BigIntPlutusData deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException {
        JsonNode node = jp.getCodec().readTree(jp);

        BigInteger intVal = node.get(INT).bigIntegerValue();
        return BigIntPlutusData.of(intVal);
    }
}
