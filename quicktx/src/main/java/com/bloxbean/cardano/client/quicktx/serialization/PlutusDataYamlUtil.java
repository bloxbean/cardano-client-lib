package com.bloxbean.cardano.client.quicktx.serialization;

import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.serializers.PlutusDataJsonConverter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

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
                ArrayNode fields = (ArrayNode) obj.get("fields");
                ArrayNode newFields = MAPPER.createArrayNode();
                for (JsonNode field : fields) {
                    newFields.add(stripFieldNames(field));
                }
                obj.set("fields", newFields);
            }

            if (obj.has("list")) {
                ArrayNode list = (ArrayNode) obj.get("list");
                ArrayNode newList = MAPPER.createArrayNode();
                for (JsonNode item : list) {
                    newList.add(stripFieldNames(item));
                }
                obj.set("list", newList);
            }

            if (obj.has("map")) {
                ArrayNode map = (ArrayNode) obj.get("map");
                ArrayNode newMap = MAPPER.createArrayNode();
                for (JsonNode entry : map) {
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
        JsonNode resolved = VariableResolver.resolveInJsonNode(cleaned, vars);

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
