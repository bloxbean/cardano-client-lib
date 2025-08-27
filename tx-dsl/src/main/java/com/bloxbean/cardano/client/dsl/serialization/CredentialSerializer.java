package com.bloxbean.cardano.client.dsl.serialization;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.address.CredentialType;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;

import java.io.IOException;

/**
 * Custom Jackson serializer/deserializer for Credential objects.
 * Serializes Credential as a readable object with string type and hex bytes.
 * Supports case-insensitive deserialization for user-friendly YAML editing.
 */
public class CredentialSerializer {

    /**
     * Serializes Credential to JSON/YAML with proper case and hex bytes.
     * Always outputs consistent format: "Key" or "Script" with hex bytes.
     */
    public static class Serializer extends JsonSerializer<Credential> {
        @Override
        public void serialize(Credential credential, JsonGenerator gen, SerializerProvider serializers) 
                throws IOException {
            gen.writeStartObject();
            gen.writeStringField("type", credential.getType().name()); // "Key" or "Script"
            gen.writeStringField("bytes", HexUtil.encodeHexString(credential.getBytes()));
            gen.writeEndObject();
        }
    }

    /**
     * Deserializes Credential from JSON/YAML with case-insensitive type matching.
     * Accepts "key", "KEY", "Key", "script", "SCRIPT", "Script", etc.
     */
    public static class Deserializer extends JsonDeserializer<Credential> {
        @Override
        public Credential deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            
            // Extract type (case-insensitive)
            JsonNode typeNode = node.get("type");
            if (typeNode == null) {
                throw InvalidDefinitionException.from(p, "Missing 'type' field for Credential", (Throwable) null);
            }
            
            String typeStr = typeNode.asText().toLowerCase(); // Convert to lowercase for comparison
            CredentialType type;
            switch (typeStr) {
                case "key":
                    type = CredentialType.Key;
                    break;
                case "script":
                    type = CredentialType.Script;
                    break;
                default:
                    throw InvalidDefinitionException.from(p, 
                        "Invalid credential type: '" + typeNode.asText() + "'. Valid values are 'Key' or 'Script' (case-insensitive)", 
                        (Throwable) null);
            }
            
            // Extract bytes (hex)
            JsonNode bytesNode = node.get("bytes");
            if (bytesNode == null) {
                throw InvalidDefinitionException.from(p, "Missing 'bytes' field for Credential", (Throwable) null);
            }
            
            String hexBytes = bytesNode.asText();
            if (hexBytes == null || hexBytes.trim().isEmpty()) {
                throw InvalidDefinitionException.from(p, "Empty 'bytes' field for Credential", (Throwable) null);
            }
            
            byte[] bytes;
            try {
                bytes = HexUtil.decodeHexString(hexBytes);
            } catch (Exception e) {
                throw InvalidDefinitionException.from(p, 
                    "Invalid hex string in 'bytes' field: " + hexBytes, e);
            }
            
            // Create appropriate Credential instance
            if (type == CredentialType.Key) {
                return Credential.fromKey(bytes);
            } else {
                return Credential.fromScript(bytes);
            }
        }
    }
}