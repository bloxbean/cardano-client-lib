package com.bloxbean.cardano.client.quicktx.serialization;

import com.bloxbean.cardano.client.plutus.spec.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PlutusDataYamlUtil.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>stripFieldNames() removes all @name at all nesting levels</li>
 *   <li>Variable resolution in JsonNode structures</li>
 *   <li>fromYamlNode() complete pipeline</li>
 *   <li>toYamlNode() serialization</li>
 *   <li>CRITICAL: Verify @name has ZERO impact on CBOR output</li>
 * </ul>
 */
class PlutusDataYamlUtilTest {

    private static final ObjectMapper MAPPER = YamlSerializer.getYamlMapper();

    @Test
    void stripFieldNames_removesNameAtTopLevel() throws JsonProcessingException {
        String yaml = "constructor: 0\n" +
                "\"@name\": test_name\n" +
                "fields:\n" +
                "  - int: 100\n";

        JsonNode node = MAPPER.readTree(yaml);
        JsonNode stripped = PlutusDataYamlUtil.stripFieldNames(node);

        assertFalse(stripped.has("@name"), "@name should be removed");
        assertTrue(stripped.has("constructor"), "constructor should remain");
        assertTrue(stripped.has("fields"), "fields should remain");
    }

    @Test
    void stripFieldNames_removesNameInNestedFields() throws JsonProcessingException {
        String yaml = "constructor: 0\n" +
                "fields:\n" +
                "  - \"@name\": seller\n" +
                "    bytes: 48656c6c6f\n" +
                "  - \"@name\": price\n" +
                "    int: 100\n";

        JsonNode node = MAPPER.readTree(yaml);
        JsonNode stripped = PlutusDataYamlUtil.stripFieldNames(node);

        JsonNode fields = stripped.get("fields");
        assertFalse(fields.get(0).has("@name"), "@name in first field should be removed");
        assertFalse(fields.get(1).has("@name"), "@name in second field should be removed");
        assertTrue(fields.get(0).has("bytes"), "bytes should remain");
        assertTrue(fields.get(1).has("int"), "int should remain");
    }

    @Test
    void stripFieldNames_removesNameInDeeplyNestedStructures() throws JsonProcessingException {
        String yaml = "constructor: 1\n" +
                "fields:\n" +
                "  - \"@name\": user_data\n" +
                "    constructor: 0\n" +
                "    fields:\n" +
                "      - \"@name\": username\n" +
                "        bytes: 616c696365\n" +
                "      - \"@name\": balance\n" +
                "        int: 5000\n" +
                "  - \"@name\": nft_list\n" +
                "    list:\n" +
                "      - \"@name\": nft_1\n" +
                "        bytes: abcd1234\n" +
                "      - \"@name\": nft_2\n" +
                "        bytes: ef567890\n";

        JsonNode node = MAPPER.readTree(yaml);
        JsonNode stripped = PlutusDataYamlUtil.stripFieldNames(node);

        // Check top level
        JsonNode topFields = stripped.get("fields");
        assertFalse(topFields.get(0).has("@name"), "@name in first top field should be removed");

        // Check nested constructor
        JsonNode nestedConstructor = topFields.get(0);
        JsonNode nestedFields = nestedConstructor.get("fields");
        assertFalse(nestedFields.get(0).has("@name"), "@name in nested field should be removed");
        assertFalse(nestedFields.get(1).has("@name"), "@name in nested field should be removed");

        // Check list items
        JsonNode listField = topFields.get(1);
        JsonNode list = listField.get("list");
        assertFalse(list.get(0).has("@name"), "@name in list item should be removed");
        assertFalse(list.get(1).has("@name"), "@name in list item should be removed");
    }

    @Test
    void stripFieldNames_removesNameInMapStructures() throws JsonProcessingException {
        String yaml = "map:\n" +
                "  - k:\n" +
                "      \"@name\": key1\n" +
                "      bytes: 636f6c6f72\n" +
                "    v:\n" +
                "      \"@name\": value1\n" +
                "      bytes: 626c7565\n";

        JsonNode node = MAPPER.readTree(yaml);
        JsonNode stripped = PlutusDataYamlUtil.stripFieldNames(node);

        JsonNode map = stripped.get("map");
        JsonNode entry = map.get(0);
        assertFalse(entry.get("k").has("@name"), "@name in map key should be removed");
        assertFalse(entry.get("v").has("@name"), "@name in map value should be removed");
    }

    @Test
    void fromYamlNode_createsConstrPlutusData() throws JsonProcessingException {
        String yaml = "constructor: 0\n" +
                "fields:\n" +
                "  - int: 100\n" +
                "  - bytes: 48656c6c6f\n";

        JsonNode node = MAPPER.readTree(yaml);
        PlutusData result = PlutusDataYamlUtil.fromYamlNode(node, new HashMap<>());

        assertNotNull(result);
        assertTrue(result instanceof ConstrPlutusData);
        ConstrPlutusData constr = (ConstrPlutusData) result;
        assertEquals(0, constr.getAlternative());
        assertEquals(2, constr.getData().getPlutusDataList().size());
    }

    @Test
    void fromYamlNode_withVariables() throws JsonProcessingException {
        String yaml = "constructor: 0\n" +
                "fields:\n" +
                "  - int: \"${price}\"\n" +
                "  - bytes: \"${seller}\"\n";

        Map<String, Object> vars = new HashMap<>();
        vars.put("price", "10000000");
        vars.put("seller", "48656c6c6f");

        JsonNode node = MAPPER.readTree(yaml);
        PlutusData result = PlutusDataYamlUtil.fromYamlNode(node, vars);

        assertNotNull(result);
        assertTrue(result instanceof ConstrPlutusData);
        ConstrPlutusData constr = (ConstrPlutusData) result;

        // Verify first field (int)
        PlutusData firstField = constr.getData().getPlutusDataList().get(0);
        assertTrue(firstField instanceof BigIntPlutusData);
        assertEquals(new BigInteger("10000000"), ((BigIntPlutusData) firstField).getValue());

        // Verify second field (bytes)
        PlutusData secondField = constr.getData().getPlutusDataList().get(1);
        assertTrue(secondField instanceof BytesPlutusData);
    }

    @Test
    void fromYamlNode_withNameAnnotations_producesIdenticalResult() throws JsonProcessingException {
        String yamlWith = "constructor: 0\n" +
                "fields:\n" +
                "  - \"@name\": seller\n" +
                "    bytes: 48656c6c6f\n" +
                "  - \"@name\": price\n" +
                "    int: 100\n";

        String yamlWithout = "constructor: 0\n" +
                "fields:\n" +
                "  - bytes: 48656c6c6f\n" +
                "  - int: 100\n";

        JsonNode nodeWith = MAPPER.readTree(yamlWith);
        JsonNode nodeWithout = MAPPER.readTree(yamlWithout);

        PlutusData resultWith = PlutusDataYamlUtil.fromYamlNode(nodeWith, new HashMap<>());
        PlutusData resultWithout = PlutusDataYamlUtil.fromYamlNode(nodeWithout, new HashMap<>());

        // CRITICAL: @name must have ZERO impact
        assertEquals(resultWith, resultWithout);
        assertEquals(resultWith.serializeToHex(), resultWithout.serializeToHex());
    }

    @Test
    void toYamlNode_serializesConstrPlutusData() {
        ConstrPlutusData constr = ConstrPlutusData.builder()
                .alternative(0)
                .data(ListPlutusData.of(
                        BigIntPlutusData.of(100),
                        BytesPlutusData.of("48656c6c6f")
                ))
                .build();

        JsonNode result = PlutusDataYamlUtil.toYamlNode(constr);

        assertNotNull(result);
        assertTrue(result.has("constructor"));
        assertEquals(0, result.get("constructor").asInt());
        assertTrue(result.has("fields"));
        assertEquals(2, result.get("fields").size());
    }

    @Test
    void toYamlNode_doesNotIncludeNameAnnotations() {
        ConstrPlutusData constr = ConstrPlutusData.builder()
                .alternative(0)
                .data(ListPlutusData.of(
                        BytesPlutusData.of("48656c6c6f"),
                        BigIntPlutusData.of(100)
                ))
                .build();

        JsonNode result = PlutusDataYamlUtil.toYamlNode(constr);

        // @name should NOT be in serialized output
        assertFalse(result.has("@name"));
        JsonNode fields = result.get("fields");
        assertFalse(fields.get(0).has("@name"));
        assertFalse(fields.get(1).has("@name"));
    }

    @Test
    void roundTrip_preservesPlutusData() throws JsonProcessingException {
        ConstrPlutusData original = ConstrPlutusData.builder()
                .alternative(1)
                .data(ListPlutusData.of(
                        BigIntPlutusData.of(5000),
                        BytesPlutusData.of("616c696365"),
                        ListPlutusData.of(
                                BytesPlutusData.of("abcd1234"),
                                BytesPlutusData.of("ef567890")
                        )
                ))
                .build();

        // PlutusData → JsonNode → PlutusData
        JsonNode yamlNode = PlutusDataYamlUtil.toYamlNode(original);
        PlutusData roundTripped = PlutusDataYamlUtil.fromYamlNode(yamlNode, new HashMap<>());

        assertEquals(original, roundTripped);
        assertEquals(original.serializeToHex(), roundTripped.serializeToHex());
    }

    @Test
    void nameAnnotations_haveZeroImpactOnCBOR() throws JsonProcessingException {
        String yamlWith = "constructor: 0\n" +
                "fields:\n" +
                "  - \"@name\": seller\n" +
                "    bytes: 48656c6c6f\n" +
                "  - \"@name\": price\n" +
                "    int: 100\n" +
                "  - \"@name\": nft_list\n" +
                "    list:\n" +
                "      - \"@name\": nft1\n" +
                "        bytes: abcd1234\n" +
                "      - \"@name\": nft2\n" +
                "        bytes: ef567890\n";

        String yamlWithout = "constructor: 0\n" +
                "fields:\n" +
                "  - bytes: 48656c6c6f\n" +
                "  - int: 100\n" +
                "  - list:\n" +
                "      - bytes: abcd1234\n" +
                "      - bytes: ef567890\n";

        JsonNode nodeWith = MAPPER.readTree(yamlWith);
        JsonNode nodeWithout = MAPPER.readTree(yamlWithout);

        PlutusData pd1 = PlutusDataYamlUtil.fromYamlNode(nodeWith, new HashMap<>());
        PlutusData pd2 = PlutusDataYamlUtil.fromYamlNode(nodeWithout, new HashMap<>());

        // MUST produce identical CBOR
        assertEquals(pd1.serializeToHex(), pd2.serializeToHex());

        // MUST be equal objects
        assertEquals(pd1, pd2);
    }

    @Test
    void fromYamlNode_handlesListPlutusData() throws JsonProcessingException {
        String yaml = "list:\n" +
                "  - int: 1\n" +
                "  - int: 2\n" +
                "  - int: 3\n";

        JsonNode node = MAPPER.readTree(yaml);
        PlutusData result = PlutusDataYamlUtil.fromYamlNode(node, new HashMap<>());

        assertNotNull(result);
        assertTrue(result instanceof ListPlutusData);
        ListPlutusData list = (ListPlutusData) result;
        assertEquals(3, list.getPlutusDataList().size());
    }

    @Test
    void fromYamlNode_handlesMapPlutusData() throws JsonProcessingException {
        String yaml = "map:\n" +
                "  - k:\n" +
                "      bytes: 6b6579\n" +
                "    v:\n" +
                "      int: 100\n";

        JsonNode node = MAPPER.readTree(yaml);
        PlutusData result = PlutusDataYamlUtil.fromYamlNode(node, new HashMap<>());

        assertNotNull(result);
        assertTrue(result instanceof MapPlutusData);
        MapPlutusData map = (MapPlutusData) result;
        assertEquals(1, map.getMap().size());
    }

    @Test
    void fromYamlNode_returnsNullForNullInput() throws JsonProcessingException {
        PlutusData result = PlutusDataYamlUtil.fromYamlNode(null, new HashMap<>());
        assertNull(result);
    }

    @Test
    void toYamlNode_returnsNullForNullInput() {
        JsonNode result = PlutusDataYamlUtil.toYamlNode(null);
        assertNull(result);
    }

    @Test
    void variableResolution_inNestedStructures() throws JsonProcessingException {
        String yaml = "constructor: 1\n" +
                "fields:\n" +
                "  - constructor: 0\n" +
                "    fields:\n" +
                "      - bytes: \"${username}\"\n" +
                "      - int: \"${balance}\"\n" +
                "  - list:\n" +
                "      - bytes: \"${nft1}\"\n" +
                "      - bytes: \"${nft2}\"\n";

        Map<String, Object> vars = new HashMap<>();
        vars.put("username", "616c696365");
        vars.put("balance", "5000");
        vars.put("nft1", "abcd1234");
        vars.put("nft2", "ef567890");

        JsonNode node = MAPPER.readTree(yaml);
        PlutusData result = PlutusDataYamlUtil.fromYamlNode(node, vars);

        assertNotNull(result);
        assertTrue(result instanceof ConstrPlutusData);

        // Verify the structure was created correctly
        ConstrPlutusData constr = (ConstrPlutusData) result;
        assertEquals(1, constr.getAlternative());
        assertEquals(2, constr.getData().getPlutusDataList().size());
    }
}
