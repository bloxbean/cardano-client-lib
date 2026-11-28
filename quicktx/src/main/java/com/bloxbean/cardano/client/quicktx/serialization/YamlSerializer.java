package com.bloxbean.cardano.client.quicktx.serialization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.util.StringQuotingChecker;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

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
        // Variables pass through a tree before binding. Preserve decimal precision and scale in both reading modes
        // and in valueToTree snapshots instead of silently rounding through double or stripping trailing zeroes.
        YAML_MAPPER.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        YAML_MAPPER.configure(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false);
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
            char first = value.charAt(0);
            if ((first < '0' || first > '9') && first != '+' && first != '-' && first != '.') return false;
            // The default check has already quoted anything with special characters, so the value embeds safely.
            try (JsonParser parser = PLAIN.createParser("v: " + value)) {
                parser.nextToken();
                parser.nextToken();
                JsonToken token = parser.nextToken();
                if (token != JsonToken.VALUE_NUMBER_INT && token != JsonToken.VALUE_NUMBER_FLOAT) return false;
                try {
                    if (!value.equals(parser.getNumberValue().toString())) return true;
                    // The strict reader compares a float with its BigDecimal text (StrictReading.checkYamlText), which
                    // differs for values such as 1.0E-5; quote those so the output always reads back.
                    return token == JsonToken.VALUE_NUMBER_FLOAT && !value.equals(parser.getDecimalValue().toString());
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
     * Like {@link #deserialize(String, Class)}, but core intents are read strictly (see {@link StrictReading})
     * and variables are resolved structurally on the parsed tree so variable values cannot alter YAML structure.
     */
    static <T> T deserializeStrict(String yaml, Class<T> type) {
        return deserializeStrict(yaml, type, null, null);
    }

    /**
     * Strict deserialization with an optional pre-check on the parsed raw tree (avoiding a redundant parse).
     * Binds the resolved tree without {@code variables} and passes any declared variables to {@code variablesConsumer}
     * so callers can attach them after reconstruction without triggering a second variable-resolution pass.
     */
    static <T> T deserializeStrict(String yaml, Class<T> type, Consumer<JsonNode> preCheck,
                                   Consumer<Map<String, Object>> variablesConsumer) {
        JsonNode raw;
        try {
            raw = STRICT_YAML_MAPPER.readTree(yaml);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize YAML to " + type.getSimpleName() + ": " + rootMessage(e), e);
        }
        if (preCheck != null) preCheck.accept(raw);
        try {
            StrictReading.checkYamlText(yaml);
            JsonNode tree = raw;
            Map<String, Object> variables = null;
            if (raw instanceof ObjectNode obj) {
                JsonNode varsNode = obj.get("variables");
                StrictReading.checkVariables(varsNode);
                if (varsNode != null && varsNode.isObject()) {
                    variables = STRICT_YAML_MAPPER.convertValue(varsNode,
                            new TypeReference<Map<String, Object>>() { });
                    Map<String, Object> resolvedVars = variables;
                    ObjectNode resolved = STRICT_YAML_MAPPER.createObjectNode();
                    obj.fields().forEachRemaining(entry -> {
                        if (!"variables".equals(entry.getKey())) {
                            resolved.set(entry.getKey(),
                                    VariableResolver.resolveTree(entry.getValue(), resolvedVars, entry.getKey()));
                        }
                    });
                    if (VariableResolver.containsResolvedPlaceholder(resolved)) {
                        throw new IllegalArgumentException("A resolved value still contains a ${...}"
                                + " placeholder; it is kept literal and this field cannot hold it");
                    }
                    tree = resolved;
                }
            }
            StrictReading.checkDocument(tree);
            T value = STRICT_YAML_MAPPER.treeToValue(tree, type);
            if (variablesConsumer != null && variables != null) {
                variablesConsumer.accept(variables);
            }
            return value;
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
