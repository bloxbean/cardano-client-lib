package com.bloxbean.cardano.client.transaction.spec.serializers;

import com.bloxbean.cardano.client.transaction.spec.BigIntPlutusData;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

import static com.bloxbean.cardano.client.transaction.spec.serializers.PlutusDataJsonKeys.INT;

public class BigIntDataJsonSerializer extends StdSerializer<BigIntPlutusData> {

    public BigIntDataJsonSerializer() {
        this(null);
    }

    public BigIntDataJsonSerializer(Class<BigIntPlutusData> clazz) {
        super(clazz);
    }

    @Override
    public void serialize(BigIntPlutusData value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStartObject();
        gen.writeNumberField(INT, value.getValue());
        gen.writeEndObject();
    }
}
