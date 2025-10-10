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

        // Handle variable references by returning placeholder metadata
        if (value.startsWith("${") && value.endsWith("}")) {
            return new PlaceholderMetadata(value);
        }

        return parseMetadata(value);
    }

    /**
     * Check if the string is likely CBOR hex format.
     * CBOR hex is pure hexadecimal characters with even length.
     */
    private static boolean isCborHex(String value) {
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
    private static boolean isJson(String value) {
        // JSON starts with { or [ and ends with } or ]
        return (value.startsWith("{") && value.endsWith("}")) ||
               (value.startsWith("[") && value.endsWith("]"));
    }

    /**
     * Check if string could possibly be hex (less strict than isCborHex).
     * Used as a fallback check.
     */
    private static boolean isPossibleHex(String value) {
        return !value.isEmpty() && 
               value.length() % 2 == 0 && 
               value.matches("^[0-9a-fA-F]+$");
    }

    /**
     * Parse metadata string (YAML / JSON / CBOR hex) into Metadata.
     */
    public static Metadata parseMetadata(String value) throws IOException {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String trimmed = value.trim();

        try {
            if (isCborHex(trimmed)) {
                byte[] cborBytes = HexUtil.decodeHexString(trimmed);
                return MetadataBuilder.deserialize(cborBytes);
            } else if (isJson(trimmed)) {
                return MetadataBuilder.metadataFromJson(trimmed);
            } else {
                return MetadataBuilder.metadataFromYaml(trimmed);
            }
        } catch (Exception e) {
            try {
                if (isPossibleHex(trimmed)) {
                    byte[] cborBytes = HexUtil.decodeHexString(trimmed);
                    return MetadataBuilder.deserialize(cborBytes);
                }
            } catch (Exception hexError) {
                // Ignore and fall through
            }

            throw new IOException("Failed to deserialize Metadata: " + e.getMessage(), e);
        }
    }
}
