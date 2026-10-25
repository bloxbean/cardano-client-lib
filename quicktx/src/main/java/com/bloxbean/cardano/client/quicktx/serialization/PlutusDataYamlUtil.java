package com.bloxbean.cardano.client.quicktx.serialization;

import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.serializers.PlutusDataJsonConverter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility class for handling PlutusData in YAML format with optional field name annotations.
 *
 * <p>This class provides support for structured YAML format for datums and redeemers with:
 * <ul>
 *   <li>Optional {@code @name} property for field documentation (stripped before serialization)</li>
 *   <li>Variable resolution using ${variable} syntax</li>
 *   <li>Full PlutusData construction via Jackson annotations</li>
 * </ul>
 *
 * <p><b>Important:</b> The {@code @name} property is purely syntactic sugar for human readability:
 * <ul>
 *   <li>NEVER included in CBOR serialization</li>
 *   <li>Stripped before PlutusData construction</li>
 *   <li>No impact on datum hash or transaction validity</li>
 *   <li>Not preserved in round-trip serialization (write-only)</li>
 * </ul>
 *
 * <p>Processing pipeline:
 * <pre>
 * YAML Input → Strip @name → Resolve Variables → Build PlutusData → CBOR
 * </pre>
 */
public class PlutusDataYamlUtil {

    private static final ObjectMapper MAPPER = YamlSerializer.getYamlMapper();
    private static final String NAME_ANNOTATION = "@name";

    /**
     * Strip all {@code @name} properties recursively from a JsonNode tree.
     *
     * <p>The {@code @name} property is write-only documentation - it is permanently discarded after parsing
     * and has zero impact on PlutusData construction or CBOR serialization.
     *
     * @param node the JsonNode to process
     * @return a new JsonNode with all @name properties removed
     */
    private static final Set<String> FORMS = Set.of("int", "bytes", "list", "map", "constructor");

    /**
     * Plutus data is exactly one of {@code {int: <integer>}}, {@code {bytes: <hex>}}, {@code {list: [..]}},
     * {@code {map: [{k: .., v: ..}]}} and {@code {constructor: <integer >= 0>, fields: [..]}}, optionally labelled with
     * {@code @name}.
     */
    static void requireWellFormed(JsonNode node, String path) {
        if (node == null || !node.isObject())
            throw invalid(path, "must be an object with one of " + FORMS, node);
        List<String> keys = new ArrayList<>();
        node.fieldNames().forEachRemaining(name -> {
            if (!NAME_ANNOTATION.equals(name)) keys.add(name);
        });
        String form = keys.stream().filter(FORMS::contains).findFirst().orElse(null);
        Set<String> expected = "constructor".equals(form) ? Set.of("constructor", "fields")
                : form == null ? Set.of() : Set.of(form);
        if (form == null || !new HashSet<>(keys).equals(expected))
            throw invalid(path, "must have exactly one of " + FORMS + " (constructor with fields)", node);
        JsonNode value = node.get(form);
        switch (form) {
            case "int" -> {
                if (!value.isIntegralNumber())
                    throw invalid(path + ".int", value.isTextual() && value.asText().contains("${")
                            ? "is an unresolved placeholder" : "must be an integer", value);
            }
            case "bytes" -> {
                if (!value.isTextual() && !value.isIntegralNumber()) throw invalid(path + ".bytes", "must be hex text", value);
            }
            case "constructor" -> {
                // ConstrPlutusData reads the alternative as a long; a larger value would silently wrap.
                if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0)
                    throw invalid(path + ".constructor", "must be a non-negative integer that fits in a long", value);
                JsonNode fields = node.get("fields");
                if (!fields.isArray()) throw invalid(path + ".fields", "must be a list", fields);
                for (int i = 0; i < fields.size(); i++) requireWellFormed(fields.get(i), path + ".fields[" + i + "]");
            }
            case "list" -> {
                if (!value.isArray()) throw invalid(path + ".list", "must be a list", value);
                for (int i = 0; i < value.size(); i++) requireWellFormed(value.get(i), path + ".list[" + i + "]");
            }
            default -> { // map
                if (!value.isArray()) throw invalid(path + ".map", "must be a list of {k, v}", value);
                for (int i = 0; i < value.size(); i++) {
                    JsonNode entry = value.get(i);
                    if (entry == null || !entry.isObject() || entry.size() != 2 || !entry.has("k") || !entry.has("v"))
                        throw invalid(path + ".map[" + i + "]", "must be {k, v}", entry);
                    requireWellFormed(entry.get("k"), path + ".map[" + i + "].k");
                    requireWellFormed(entry.get("v"), path + ".map[" + i + "].v");
                }
            }
        }
    }

    private static IllegalArgumentException invalid(String path, String problem, JsonNode value) {
        return new IllegalArgumentException("Plutus data at " + path + " " + problem + ", but was: " + value);
    }

    /** A malformed structure (e.g. {@code fields: 5}) is an input error, not a {@link ClassCastException}. */
    private static ArrayNode requireArray(ObjectNode obj, String key) {
        JsonNode value = obj.get(key);
        if (!(value instanceof ArrayNode array))
            throw new IllegalArgumentException("Plutus data '" + key + "' must be a list, but was: " + value);
        return array;
    }

    /** Each element of {@code fields}, {@code list} and {@code map} is itself Plutus data, so it must be an object. */
    private static JsonNode requireObject(JsonNode value, String key) {
        if (value == null || !value.isObject())
            throw new IllegalArgumentException("Plutus data '" + key + "' elements must be objects, but was: " + value);
        return value;
    }

    public static JsonNode stripFieldNames(JsonNode node) {
        if (node == null) {
            return null;
        }

        if (node.isObject()) {
            ObjectNode obj = ((ObjectNode) node).deepCopy();

            // Remove @name at this level using Jackson's built-in method
            obj.remove(NAME_ANNOTATION);

            // Recurse into known PlutusData structures
            if (obj.has("fields")) {
                ArrayNode fields = requireArray(obj, "fields");
                ArrayNode newFields = MAPPER.createArrayNode();
                for (JsonNode field : fields) {
                    newFields.add(stripFieldNames(requireObject(field, "fields")));
                }
                obj.set("fields", newFields);
            }

            if (obj.has("list")) {
                ArrayNode list = requireArray(obj, "list");
                ArrayNode newList = MAPPER.createArrayNode();
                for (JsonNode item : list) {
                    newList.add(stripFieldNames(requireObject(item, "list")));
                }
                obj.set("list", newList);
            }

            if (obj.has("map")) {
                ArrayNode map = requireArray(obj, "map");
                ArrayNode newMap = MAPPER.createArrayNode();
                for (JsonNode entry : map) {
                    requireObject(entry, "map");
                    if (!entry.hasNonNull("k") || !entry.hasNonNull("v"))
                        throw new IllegalArgumentException("Plutus data 'map' entries need both k and v, but was: " + entry);
                    ObjectNode newEntry = MAPPER.createObjectNode();
                    if (entry.has("k")) {
                        newEntry.set("k", stripFieldNames(entry.get("k")));
                    }
                    if (entry.has("v")) {
                        newEntry.set("v", stripFieldNames(entry.get("v")));
                    }
                    newMap.add(newEntry);
                }
                obj.set("map", newMap);
            }

            return obj;
        } else if (node.isArray()) {
            ArrayNode arr = MAPPER.createArrayNode();
            for (JsonNode item : node) {
                arr.add(stripFieldNames(item));
            }
            return arr;
        }

        return node;
    }

    /**
     * Complete 3-step pipeline: Strip @name → Resolve vars → Deserialize to PlutusData.
     *
     * <p>Uses PlutusDataJsonConverter which handles all PlutusData subtypes
     * (ConstrPlutusData, ListPlutusData, MapPlutusData, etc.).
     *
     * @param node the JsonNode containing PlutusData structure
     * @param vars the variables map for ${variable} substitution
     * @return PlutusData object
     * @throws JsonProcessingException if deserialization fails
     */
    public static PlutusData fromYamlNode(JsonNode node, Map<String, Object> vars)
            throws JsonProcessingException {
        if (node == null) {
            return null;
        }

        // Step 1: Strip all @name properties (permanently discarded)
        JsonNode cleaned = stripFieldNames(node);

        // Step 2: Resolve variables (delegate to existing infrastructure)
        JsonNode resolved = VariableResolver.resolveInPlutusDataNode(cleaned, vars);

        // The converter assumes a well-formed shape: a missing key is a NullPointerException there, and a mistyped
        // value silently becomes 0 (int: '5', constructor: abc). Check the shape first, in every reading mode.
        requireWellFormed(resolved, "$");

        // Step 3: Use PlutusDataJsonConverter which knows how to deserialize all PlutusData types
        return PlutusDataJsonConverter.toPlutusData(resolved);
    }

    /**
     * Serialize PlutusData to JsonNode (for YAML output).
     *
     * <p>Uses Jackson's {@code @JsonSerialize} annotations on PlutusData classes
     * (ConstrDataJsonSerializer, ListDataJsonSerializer, etc.).
     *
     * <p><b>Note:</b> {@code @name} annotations are NOT in output - they're write-only.
     *
     * @param plutusData the PlutusData to serialize
     * @return JsonNode representation
     */
    public static JsonNode toYamlNode(PlutusData plutusData) {
        if (plutusData == null) {
            return null;
        }

        // Use Jackson's @JsonSerialize annotations!
        // This automatically uses ConstrDataJsonSerializer, ListDataJsonSerializer, etc.
        return MAPPER.valueToTree(plutusData);
    }
}
