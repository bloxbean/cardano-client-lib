package com.bloxbean.cardano.client.quicktx.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for resolving variable placeholders in YAML content.
 * Supports ${variable} syntax for variable substitution.
 */
public class VariableResolver {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    private static final ObjectMapper MAPPER = YamlSerializer.getYamlMapper();

    /**
     * Resolve variables in the given template string.
     *
     * @param template the template string containing ${variable} placeholders
     * @param variables the variables map for substitution
     * @return resolved string with variables substituted
     * @throws IllegalArgumentException if a required variable is not found
     */
    public static String resolve(String template, Map<String, Object> variables) {
        if (template == null || variables == null || variables.isEmpty()) {
            return template;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String variableName = matcher.group(1);
            Object value = variables.get(variableName);

            if (value == null) {
                throw new IllegalArgumentException("Variable not found: " + variableName);
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(value.toString()));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Resolve variables in a JsonNode tree recursively.
     *
     * <p>This method traverses the entire JsonNode tree and resolves ${variable} placeholders
     * in all string values (TextNode). It handles all PlutusData structures including:
     * <ul>
     *   <li>Object nodes (fields, map entries)</li>
     *   <li>Array nodes (lists, fields arrays)</li>
     *   <li>Text nodes (bytes, int values as strings)</li>
     * </ul>
     *
     * <p>Special handling for PlutusData int fields: After variable resolution, if we're in an
     * "int" field and the resolved value is numeric, we convert it to a numeric node so that
     * PlutusDataJsonConverter can deserialize it correctly.
     *
     * @param node the JsonNode to process
     * @param variables the variables map for substitution
     * @return a new JsonNode with all variables resolved
     * @throws IllegalArgumentException if a required variable is not found
     */
    public static JsonNode resolveInJsonNode(JsonNode node, Map<String, Object> variables) {
        if (node == null || variables == null || variables.isEmpty()) {
            return node;
        }

        if (node.isObject()) {
            ObjectNode obj = ((ObjectNode) node).deepCopy();

            // Iterate over all fields and resolve recursively
            obj.fields().forEachRemaining(entry -> {
                String fieldName = entry.getKey();
                JsonNode fieldValue = entry.getValue();
                JsonNode resolved = resolveInJsonNode(fieldValue, variables);

                // Special handling for "int" field in PlutusData
                // If it's a string that represents a number, convert to numeric node
                if ("int".equals(fieldName) && resolved.isTextual()) {
                    String text = resolved.asText();
                    try {
                        // Try to parse as a number and convert to numeric node
                        obj.put(fieldName, Long.parseLong(text));
                        return;
                    } catch (NumberFormatException e) {
                        // Not a number, keep as text
                    }
                }

                obj.set(fieldName, resolved);
            });

            return obj;
        } else if (node.isArray()) {
            ArrayNode arr = MAPPER.createArrayNode();
            for (JsonNode item : node) {
                arr.add(resolveInJsonNode(item, variables));
            }
            return arr;
        } else if (node.isTextual()) {
            // Resolve variables in text nodes
            String text = node.asText();
            String resolved = resolve(text, variables);
            return new TextNode(resolved);
        }

        // Primitives (numbers, booleans, null) unchanged
        return node;
    }

}
