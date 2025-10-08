package com.bloxbean.cardano.client.util.serializers;

import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

/**
 * Jackson serializer that converts byte[] to hex string for JSON/YAML serialization.
 * Can be used with @JsonSerialize annotation on byte[] fields.
 */
public class ByteArrayToHexSerializer extends StdSerializer<byte[]> {

    public ByteArrayToHexSerializer() {
        this(null);
    }

    public ByteArrayToHexSerializer(Class<byte[]> clazz) {
        super(clazz);
    }

    @Override
    public void serialize(byte[] value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else {
            gen.writeString(HexUtil.encodeHexString(value));
        }
    }
}