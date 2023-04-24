package com.bloxbean.cardano.client.transaction.spec.serializers;

import com.bloxbean.cardano.client.transaction.spec.ListPlutusData;
import com.bloxbean.cardano.client.transaction.spec.PlutusData;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.util.List;

import static com.bloxbean.cardano.client.transaction.spec.serializers.PlutusDataJsonKeys.LIST;

public class ListDataJsonSerializer extends StdSerializer<ListPlutusData> {

    public ListDataJsonSerializer() {
        this(null);
    }

    public ListDataJsonSerializer(Class<ListPlutusData> clazz) {
        super(clazz);
    }

    @Override
    public void serialize(ListPlutusData value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStartObject();
        gen.writeFieldName(LIST);
        gen.writeStartArray();
        List<PlutusData> items = value.getPlutusDataList();
        for (PlutusData item : items) {
            gen.writeObject(item);
        }
        gen.writeEndArray();
        gen.writeEndObject();
    }
}
