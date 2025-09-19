package com.bloxbean.cardano.client.metadata.helper;

import com.bloxbean.cardano.client.metadata.exception.MetadataDeSerializationException;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;

/**
 * Converter for transforming CBOR metadata bytes to YAML format.
 * This class extends AbstractMetadataConverter to reuse common CBOR processing logic.
 */
public class MetadataToYamlNoSchemaConverter extends AbstractMetadataConverter {
    
    private final static YAMLMapper yamlMapper;
    
    static {
        YAMLFactory yamlFactory = new YAMLFactory();
        yamlFactory.configure(YAMLGenerator.Feature.MINIMIZE_QUOTES, false); // Changed to false to ensure hex strings are quoted
        yamlFactory.configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false);
        yamlFactory.configure(YAMLGenerator.Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS, false);
        yamlMapper = new YAMLMapper(yamlFactory);
        
        // Add custom serializer for strings that start with "0x"
        SimpleModule module = new SimpleModule();
        module.addSerializer(String.class, new JsonSerializer<String>() {
            @Override
            public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                // Always quote strings that look like hex values
                if (value != null && value.startsWith("0x")) {
                    gen.writeString(value);
                } else {
                    gen.writeString(value);
                }
            }
        });
        yamlMapper.registerModule(module);
    }

    /**
     * Convert cbor metadata bytes to yaml string
     * @param cborBytes CBOR bytes to convert
     * @return YAML string representation
     */
    public static String cborBytesToYaml(byte[] cborBytes) {
        try {
            return cborHexToYaml(HexUtil.encodeHexString(cborBytes));
        } catch (Exception e) {
            throw new MetadataDeSerializationException("Deserialization error", e);
        }
    }

    /**
     * Converts cbor metadata bytes in hex format to yaml string
     * @param hex CBOR data in hexadecimal string format
     * @return YAML string representation
     */
    public static String cborHexToYaml(String hex) {
        try {
            java.util.Map<Object, Object> result = cborHexToJavaMap(hex);
            return yamlMapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new MetadataDeSerializationException("Deserialization error", e);
        }
    }
}