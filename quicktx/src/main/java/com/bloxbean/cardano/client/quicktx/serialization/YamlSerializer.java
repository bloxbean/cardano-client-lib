package com.bloxbean.cardano.client.quicktx.serialization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

/**
 * Utility class for YAML serialization/deserialization of transaction documents.
 */
public class YamlSerializer {
    
    private static final ObjectMapper YAML_MAPPER;
    
    static {
        YAMLFactory factory = new YAMLFactory();
        factory.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
        factory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        factory.disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID); // Disable !<type> tags
        
        YAML_MAPPER = new ObjectMapper(factory);
        // Exclude null values from serialization
        YAML_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
    
    /**
     * Get the configured YAML mapper instance.
     * @return ObjectMapper configured for YAML
     */
    public static ObjectMapper getYamlMapper() {
        return YAML_MAPPER;
    }
    
    /**
     * Serialize an object to YAML string.
     * @param obj the object to serialize
     * @return YAML string representation
     * @throws RuntimeException if serialization fails
     */
    public static String serialize(Object obj) {
        try {
            return YAML_MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize object to YAML", e);
        }
    }
    
    /**
     * Serialize an object to pretty-printed YAML string.
     * @param obj the object to serialize
     * @return pretty-printed YAML string
     * @throws RuntimeException if serialization fails
     */
    public static String serializePretty(Object obj) {
        try {
            return YAML_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize object to YAML", e);
        }
    }
    
    /**
     * Deserialize YAML string to specified type.
     * @param yaml the YAML string
     * @param type the target type class
     * @param <T> the target type
     * @return deserialized object
     * @throws RuntimeException if deserialization fails
     */
    public static <T> T deserialize(String yaml, Class<T> type) {
        try {
            return YAML_MAPPER.readValue(yaml, type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize YAML to " + type.getSimpleName(), e);
        }
    }
    
    /**
     * Deserialize YAML string to TransactionDocument.
     * @param yaml the YAML string
     * @return TransactionDocument instance
     * @throws RuntimeException if deserialization fails
     */
    public static TransactionDocument deserialize(String yaml) {
        return deserialize(yaml, TransactionDocument.class);
    }
}