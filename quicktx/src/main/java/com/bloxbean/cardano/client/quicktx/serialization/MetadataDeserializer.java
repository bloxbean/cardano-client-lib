package com.bloxbean.cardano.client.quicktx.serialization;

import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * Custom Jackson deserializer for Metadata objects.
 * Automatically detects the format (YAML, JSON, or CBOR hex) and deserializes accordingly.
 */
public class MetadataDeserializer extends JsonDeserializer<Metadata> {
    
    @Override
    public Metadata deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
        String value = parser.getText();
        
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        value = value.trim();
        
        // Handle variable references - return null to be resolved later
        if (value.startsWith("${") && value.endsWith("}")) {
            return null;
        }
        
        try {
            // Auto-detect format and deserialize
            if (isCborHex(value)) {
                // CBOR hex format - lossless deserialization
                byte[] cborBytes = HexUtil.decodeHexString(value);
                return MetadataBuilder.deserialize(cborBytes);
            } else if (isJson(value)) {
                // JSON format - legacy support
                return MetadataBuilder.metadataFromJson(value);
            } else {
                // Assume YAML format - human-readable
                return MetadataBuilder.metadataFromYaml(value);
            }
        } catch (Exception e) {
            // If YAML parsing fails, try other formats as fallback
            try {
                // Try as CBOR hex if YAML fails
                if (isPossibleHex(value)) {
                    byte[] cborBytes = HexUtil.decodeHexString(value);
                    return MetadataBuilder.deserialize(cborBytes);
                }
            } catch (Exception hexError) {
                // Not hex either
            }
            
            // Throw the original error
            throw new IOException("Failed to deserialize Metadata: " + e.getMessage(), e);
        }
    }
    
    /**
     * Check if the string is likely CBOR hex format.
     * CBOR hex is pure hexadecimal characters with even length.
     */
    private boolean isCborHex(String value) {
        // Must be non-empty and even length
        if (value.isEmpty() || value.length() % 2 != 0) {
            return false;
        }
        
        // Check if it's pure hex and reasonably long (metadata CBOR is typically longer)
        // Minimum reasonable length for metadata CBOR
        if (value.length() < 10) {
            return false;
        }
        
        // Must be all hex characters
        return value.matches("^[0-9a-fA-F]+$");
    }
    
    /**
     * Check if the string is JSON format.
     */
    private boolean isJson(String value) {
        // JSON starts with { or [ and ends with } or ]
        return (value.startsWith("{") && value.endsWith("}")) ||
               (value.startsWith("[") && value.endsWith("]"));
    }
    
    /**
     * Check if string could possibly be hex (less strict than isCborHex).
     * Used as a fallback check.
     */
    private boolean isPossibleHex(String value) {
        return !value.isEmpty() && 
               value.length() % 2 == 0 && 
               value.matches("^[0-9a-fA-F]+$");
    }
}