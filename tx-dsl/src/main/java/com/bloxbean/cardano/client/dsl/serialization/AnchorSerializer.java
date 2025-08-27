package com.bloxbean.cardano.client.dsl.serialization;

import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
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
 * Custom Jackson serializer/deserializer for Anchor objects.
 * Serializes Anchor with hex representation of anchorDataHash for better readability.
 */
public class AnchorSerializer {

    /**
     * Serializes Anchor to JSON/YAML with hex anchorDataHash.
     */
    public static class Serializer extends JsonSerializer<Anchor> {
        @Override
        public void serialize(Anchor anchor, JsonGenerator gen, SerializerProvider serializers) 
                throws IOException {
            gen.writeStartObject();
            gen.writeStringField("anchor_url", anchor.getAnchorUrl());
            
            if (anchor.getAnchorDataHash() != null) {
                gen.writeStringField("anchor_data_hash", HexUtil.encodeHexString(anchor.getAnchorDataHash()));
            } else {
                gen.writeNullField("anchor_data_hash");
            }
            
            gen.writeEndObject();
        }
    }

    /**
     * Deserializes Anchor from JSON/YAML with hex anchorDataHash.
     */
    public static class Deserializer extends JsonDeserializer<Anchor> {
        @Override
        public Anchor deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            
            // Extract anchor_url
            JsonNode urlNode = node.get("anchor_url");
            if (urlNode == null) {
                throw InvalidDefinitionException.from(p, "Missing 'anchor_url' field for Anchor", (Throwable) null);
            }
            
            String anchorUrl = urlNode.asText();
            if (anchorUrl == null) {
                throw InvalidDefinitionException.from(p, "Missing 'anchor_url' value for Anchor", (Throwable) null);
            }
            
            // Extract anchor_data_hash (optional)
            JsonNode hashNode = node.get("anchor_data_hash");
            byte[] anchorDataHash = null;
            
            if (hashNode != null && !hashNode.isNull()) {
                String hexHash = hashNode.asText();
                if (hexHash != null && !hexHash.trim().isEmpty()) {
                    try {
                        anchorDataHash = HexUtil.decodeHexString(hexHash);
                    } catch (Exception e) {
                        throw InvalidDefinitionException.from(p, 
                            "Invalid hex string in 'anchor_data_hash' field: " + hexHash, e);
                    }
                }
            }
            
            return new Anchor(anchorUrl, anchorDataHash);
        }
    }
}