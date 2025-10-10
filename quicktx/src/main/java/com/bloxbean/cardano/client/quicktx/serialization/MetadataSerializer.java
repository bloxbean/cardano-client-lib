package com.bloxbean.cardano.client.quicktx.serialization;

import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * Custom Jackson serializer for Metadata objects.
 * Serializes Metadata to YAML format by default for human readability.
 * Can be configured to use CBOR hex format for lossless serialization.
 */
public class MetadataSerializer extends JsonSerializer<Metadata> {
    
    private static final String FORMAT_HINT_KEY = "metadata.format";
    
    public enum Format {
        YAML,      // Human-readable YAML format (default)
        CBOR_HEX   // Lossless CBOR hex format
    }
    
    @Override
    public void serialize(Metadata metadata, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (metadata == null) {
            gen.writeNull();
            return;
        }
        
        try {
            Format format = getFormat(provider);
            String serialized;
            
            switch (format) {
                case CBOR_HEX:
                    // Use CBOR hex for lossless serialization
                    serialized = HexUtil.encodeHexString(metadata.serialize());
                    break;
                case YAML:
                default:
                    // Use YAML for human readability (default)
                    serialized = MetadataBuilder.toYaml(metadata);
                    break;
            }
            
            gen.writeString(serialized);
        } catch (Exception e) {
            throw new IOException("Failed to serialize Metadata", e);
        }
    }
    
    /**
     * Determine the serialization format from the provider context.
     * Default is YAML for human readability.
     */
    private Format getFormat(SerializerProvider provider) {
        if (provider == null) {
            return Format.YAML;
        }
        
        // Check if a format hint is provided in the context
        Object formatHint = provider.getAttribute(FORMAT_HINT_KEY);
        if (formatHint instanceof Format) {
            return (Format) formatHint;
        } else if (formatHint instanceof String) {
            try {
                return Format.valueOf(formatHint.toString().toUpperCase());
            } catch (IllegalArgumentException e) {
                // Invalid format, use default
            }
        }
        
        return Format.YAML;
    }
    
    /**
     * Helper method to set format hint in serialization context.
     */
    public static void setFormatHint(SerializerProvider provider, Format format) {
        if (provider != null) {
            provider.setAttribute(FORMAT_HINT_KEY, format);
        }
    }
}