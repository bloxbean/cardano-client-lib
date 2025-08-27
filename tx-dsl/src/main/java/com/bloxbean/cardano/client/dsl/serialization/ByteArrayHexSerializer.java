package com.bloxbean.cardano.client.dsl.serialization;

import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;

import java.io.IOException;

/**
 * Generic Jackson serializer/deserializer for byte arrays.
 * Converts byte arrays to/from hex strings for better YAML readability.
 * Used for fields like scriptRefBytes in PaymentIntention.
 */
public class ByteArrayHexSerializer {

    /**
     * Serializes byte[] to hex string.
     */
    public static class Serializer extends JsonSerializer<byte[]> {
        @Override
        public void serialize(byte[] bytes, JsonGenerator gen, SerializerProvider serializers) 
                throws IOException {
            if (bytes != null) {
                gen.writeString(HexUtil.encodeHexString(bytes));
            } else {
                gen.writeNull();
            }
        }
    }

    /**
     * Deserializes hex string to byte[].
     */
    public static class Deserializer extends JsonDeserializer<byte[]> {
        @Override
        public byte[] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String hexString = p.getValueAsString();
            
            if (hexString == null || hexString.trim().isEmpty()) {
                return null;
            }
            
            try {
                return HexUtil.decodeHexString(hexString);
            } catch (Exception e) {
                throw InvalidDefinitionException.from(p, 
                    "Invalid hex string for byte array: " + hexString, e);
            }
        }
    }
}