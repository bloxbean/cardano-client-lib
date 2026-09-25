package com.bloxbean.cardano.client.quicktx.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.math.BigInteger;
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
        if (template == null) {
            return template;
        }

        Map<String, Object> availableVariables = variables == null ? Map.of() : variables;

        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String variableName = matcher.group(1);
            Object value = availableVariables.get(variableName);

            if (value == null) {
                throw new IllegalArgumentException("Variable not found: " + variableName);
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(value.toString()));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Resolve variables structurally in a parsed document tree.
     *
     * <p>Only text nodes are templates. A text node that is exactly one {@code ${name}}
     * placeholder is replaced by the variable's value converted to a tree, so numbers, booleans,
     * lists and maps keep their native type. A placeholder embedded in a longer string is
     * interpolated as text and stays a string. Replacement values are never re-read as
     * templates, so a value containing quotes, newlines or {@code ${...}} cannot alter the
     * document. A missing variable fails with the document path of the node that needed it.</p>
     *
     * @param node      the parsed tree; not modified
     * @param variables the variables available for substitution
     * @param path      the document path of {@code node}, used in diagnostics
     * @return a resolved copy of the tree
     */
    public static JsonNode resolveTree(JsonNode node, Map<String, Object> variables, String path) {
        if (node == null) return null;
        Map<String, Object> availableVariables = variables == null ? Map.of() : variables;

        if (node.isObject()) {
            ObjectNode resolved = MAPPER.createObjectNode();
            node.fields().forEachRemaining(entry -> resolved.set(entry.getKey(),
                    resolveTree(entry.getValue(), availableVariables, path + "." + entry.getKey())));
            return resolved;
        }
        if (node.isArray()) {
            ArrayNode resolved = MAPPER.createArrayNode();
            int index = 0;
            for (JsonNode item : node) {
                resolved.add(resolveTree(item, availableVariables, path + "[" + index++ + "]"));
            }
            return resolved;
        }
        if (!node.isTextual()) return node;

        String text = node.asText();
        Matcher exact = EXACT_VARIABLE_PATTERN.matcher(text);
        if (exact.matches()) {
            String variableName = exact.group(1);
            Object value = availableVariables.get(variableName);
            if (value == null)
                throw new IllegalArgumentException("Variable not found: " + variableName + " at " + path);
            return MAPPER.valueToTree(value);
        }
        try {
            return new TextNode(resolve(text, availableVariables));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(e.getMessage() + " at " + path, e);
        }
    }

    private static final Pattern EXACT_VARIABLE_PATTERN = Pattern.compile("^\\$\\{([^}]+)\\}$");

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
    public static JsonNode resolveInPlutusDataNode(JsonNode node, Map<String, Object> variables) {
        if (node == null) {
            return node;
        }
        Map<String, Object> availableVariables = variables == null ? Map.of() : variables;

        if (node.isObject()) {
            ObjectNode obj = ((ObjectNode) node).deepCopy();

            // Iterate over all fields and resolve recursively
            obj.fields().forEachRemaining(entry -> {
                String fieldName = entry.getKey();
                JsonNode fieldValue = entry.getValue();
                JsonNode resolved = resolveInPlutusDataNode(fieldValue, availableVariables);

                // Special handling for "int" field in PlutusData
                // If it's a string that represents a number, convert to numeric node
                if ("int".equals(fieldName) && resolved.isTextual()) {
                    String text = resolved.asText();
                    try {
                        // Try to parse as a number and convert to numeric node
                        obj.put(fieldName, new BigInteger(text));
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
                arr.add(resolveInPlutusDataNode(item, availableVariables));
            }
            return arr;
        } else if (node.isTextual()) {
            // Resolve variables in text nodes
            String text = node.asText();
            String resolved = resolve(text, availableVariables);
            return new TextNode(resolved);
        }

        // Primitives (numbers, booleans, null) unchanged
        return node;
    }

}
