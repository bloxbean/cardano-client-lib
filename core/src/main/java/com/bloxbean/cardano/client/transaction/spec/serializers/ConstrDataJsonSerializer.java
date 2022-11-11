package com.bloxbean.cardano.client.transaction.spec.serializers;

import com.bloxbean.cardano.client.transaction.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.transaction.spec.ListPlutusData;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.util.Collections;

import static com.bloxbean.cardano.client.transaction.spec.serializers.PlutusDataJsonKeys.CONSTRUCTOR;
import static com.bloxbean.cardano.client.transaction.spec.serializers.PlutusDataJsonKeys.FIELDS;

public class ConstrDataJsonSerializer extends StdSerializer<ConstrPlutusData> {

    public ConstrDataJsonSerializer() {
        this(null);
    }

    public ConstrDataJsonSerializer(Class<ConstrPlutusData> clazz) {
        super(clazz);
    }

    @Override
    public void serialize(ConstrPlutusData value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStartObject();
        gen.writeNumberField(CONSTRUCTOR, value.getAlternative());
        if (value.getData() != null) {
            ListPlutusData listPlutusData = value.getData();
            gen.writeObjectField(FIELDS, listPlutusData.getPlutusDataList());
        } else
            gen.writeObjectField(FIELDS, Collections.emptyList());
        gen.writeEndObject();
    }
}
