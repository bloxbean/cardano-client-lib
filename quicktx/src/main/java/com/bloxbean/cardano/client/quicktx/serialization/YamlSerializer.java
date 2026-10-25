package com.bloxbean.cardano.client.quicktx.serialization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;

/**
 * Utility class for YAML serialization/deserialization of transaction documents.
 */
@Slf4j
public class YamlSerializer {

    private static final ObjectMapper YAML_MAPPER;
    private static final ObjectMapper STRICT_YAML_MAPPER;

    static {
        YAMLFactory factory = new YAMLFactory();
        factory.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
        factory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
        factory.disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID); // Disable !<type> tags
        factory.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

        YAML_MAPPER = new ObjectMapper(factory);
        // Exclude null values from serialization
        YAML_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        STRICT_YAML_MAPPER = StrictReading.apply(YAML_MAPPER.copy());
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
     * Performs template expansion by replacing ${variable} placeholders before deserialization.
     *
     * @param yaml the YAML string (may contain ${variable} placeholders)
     * @param type the target type class
     * @param <T> the target type
     * @return deserialized object
     * @throws RuntimeException if deserialization fails
     */
    public static <T> T deserialize(String yaml, Class<T> type) {
        return deserialize(YAML_MAPPER, yaml, type);
    }

    /**
     * Like {@link #deserialize(String, Class)}, but core intents are read strictly (see {@link StrictReading}).
     */
    static <T> T deserializeStrict(String yaml, Class<T> type) {
        try {
            JsonNode tree = STRICT_YAML_MAPPER.readTree(resolveVariables(STRICT_YAML_MAPPER, yaml));
            StrictReading.rejectAliasConflicts(tree);
            return STRICT_YAML_MAPPER.treeToValue(tree, type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize YAML to " + type.getSimpleName(), e);
        }
    }

    private static <T> T deserialize(ObjectMapper mapper, String yaml, Class<T> type) {
        try {
            // Deserialize the expanded YAML
            return mapper.readValue(resolveVariables(mapper, yaml), type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize YAML to " + type.getSimpleName(), e);
        }
    }

    /** Expands {@code ${variable}} placeholders from the document's {@code variables} section, if any. */
    private static String resolveVariables(ObjectMapper mapper, String yaml) throws IOException {
        JsonNode tree = mapper.readTree(yaml);
        JsonNode varsNode = tree.get("variables");
        if (varsNode != null && varsNode.isObject()) {
            Map<String, Object> variables = mapper.convertValue(varsNode, Map.class);
            return VariableResolver.resolve(yaml, variables);
        }
        return yaml;
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
