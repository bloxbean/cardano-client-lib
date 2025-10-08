package com.bloxbean.cardano.client.util.serializers;

import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

/**
 * Jackson deserializer that converts hex string to byte[] for JSON/YAML deserialization.
 * Can be used with @JsonDeserialize annotation on byte[] fields.
 */
public class HexToByteArrayDeserializer extends StdDeserializer<byte[]> {

    public HexToByteArrayDeserializer() {
        this(null);
    }

    public HexToByteArrayDeserializer(Class<?> clazz) {
        super(clazz);
    }

    @Override
    public byte[] deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        String hexString = jp.getValueAsString();
        if (hexString == null || hexString.isEmpty()) {
            return null;
        }
        return HexUtil.decodeHexString(hexString);
    }
}