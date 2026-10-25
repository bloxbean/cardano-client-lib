package com.bloxbean.cardano.client.quicktx.serialization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.util.StringQuotingChecker;
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
        // rebuild() keeps the constructor's defaults (e.g. EMPTY_STRING_AS_NULL); builder() alone does not.
        YAMLFactory factory = new YAMLFactory().rebuild().stringQuotingChecker(new RoundTripQuotingChecker()).build();
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
     * Quotes a string value whenever YAML would read the unquoted text back as a number with a different value.
     * Jackson's default check misses YAML 1.1 forms, so the string {@code "0xabc"} was written unquoted and read back as
     * {@code 2748}, and {@code ".inf"} as a float. Only those values change: strings that read back as the same number
     * (digit-only hex) stay unquoted, and text that cannot be plain is still single-quoted by the emitter itself.
     */
    static final class RoundTripQuotingChecker extends StringQuotingChecker.Default {
        private static final long serialVersionUID = 1L;
        private static final YAMLFactory PLAIN = new YAMLFactory();

        @Override
        public boolean needToQuoteValue(String value) {
            return super.needToQuoteValue(value) || readsBackDifferently(value);
        }

        private static boolean readsBackDifferently(String value) {
            if (value.isEmpty()) return false;
            // The default check has already quoted anything with special characters, so the value embeds safely.
            try (JsonParser parser = PLAIN.createParser("v: " + value)) {
                parser.nextToken();
                parser.nextToken();
                JsonToken token = parser.nextToken();
                if (token != JsonToken.VALUE_NUMBER_INT && token != JsonToken.VALUE_NUMBER_FLOAT) return false;
                try {
                    return !value.equals(parser.getNumberValue().toString());
                } catch (IOException | RuntimeException notANumberValue) {
                    return true; // e.g. .inf: a float token that does not convert, so it cannot be left plain
                }
            } catch (IOException | RuntimeException notPlain) {
                // Not valid as a plain scalar: the emitter falls back to quoting it by itself.
                return false;
            }
        }
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
            JsonNode raw = STRICT_YAML_MAPPER.readTree(yaml);
            // Check the declared variables before textual substitution rewrites them.
            if (raw != null) {
                StrictReading.checkVariables(raw.get("variables"));
                StrictReading.checkKeys(raw);
            }
            String resolved = resolveVariables(STRICT_YAML_MAPPER, yaml);
            StrictReading.checkYamlText(resolved);
            JsonNode tree = STRICT_YAML_MAPPER.readTree(resolved);
            StrictReading.checkDocument(tree);
            return STRICT_YAML_MAPPER.treeToValue(tree, type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize YAML to " + type.getSimpleName() + ": " + rootMessage(e), e);
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

    /** The innermost message, so a strict-reading error names the offending field or value. */
    static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        // Jackson's full message includes the reference chain, i.e. the field the problem is in.
        String message = current.getMessage();
        return message == null ? current.getClass().getSimpleName() : message;
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
