package com.bloxbean.cardano.client.quicktx.filter;

import com.bloxbean.cardano.client.quicktx.filter.yaml.UtxoFilterYaml;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests round-trip serialization of UtxoFilterSpec with variable placeholders.
 * This ensures that YAML with variables (${variable_name}) can be parsed,
 * processed, and serialized back while preserving structure.
 */
class UtxoFilterVariableRoundTripTest {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    @Test
    void roundtrip_with_address_variable() throws IOException {
        String yamlWithVariable = String.join("\n",
                "address: ${contract_addr}",
                "lovelace: { gte: 2000000 }",
                "selection:",
                "  order: [ \"lovelace desc\" ]",
                "  limit: 1");

        // Variables need to be resolved before parsing into AST
        String resolvedYaml = yamlWithVariable.replace("${contract_addr}", "addr_test1xyz");

        UtxoFilterSpec spec = UtxoFilterYaml.parse(resolvedYaml);
        JsonNode serialized = UtxoFilterYaml.toNode(spec);
        UtxoFilterSpec roundTrip = UtxoFilterYaml.parseNode(serialized);

        // Verify structure is preserved
        assertEquals(spec.root(), roundTrip.root());
        assertEquals(spec.selection().getLimit(), roundTrip.selection().getLimit());
        assertEquals(1, roundTrip.selection().getOrder().size());
    }

    @Test
    void roundtrip_with_multiple_variables() throws IOException {
        String yamlWithVariables = String.join("\n",
                "and:",
                "  - address: ${script_address}",
                "  - amount:",
                "      unit: ${token_unit}",
                "      gte: ${min_quantity}",
                "selection:",
                "  limit: ${max_results}");

        // Resolve variables
        Map<String, String> variables = Map.of(
                "${script_address}", "addr_test1contract",
                "${token_unit}", "policy123asset456",
                "${min_quantity}", "100",
                "${max_results}", "5"
        );

        String resolvedYaml = yamlWithVariables;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            resolvedYaml = resolvedYaml.replace(entry.getKey(), entry.getValue());
        }

        UtxoFilterSpec spec = UtxoFilterYaml.parse(resolvedYaml);
        JsonNode serialized = UtxoFilterYaml.toNode(spec);
        UtxoFilterSpec roundTrip = UtxoFilterYaml.parseNode(serialized);

        assertEquals(spec.root(), roundTrip.root());
        assertEquals(spec.selection().getLimit(), roundTrip.selection().getLimit());
    }

    @Test
    void complex_filter_with_logical_operations_roundtrip() throws IOException {
        String complexYaml = String.join("\n",
                "or:",
                "  - and:",
                "      - address: addr_test1specific",
                "      - lovelace: { gte: 1000000 }",
                "  - and:",
                "      - not: { inlineDatum: null }",
                "      - amount:",
                "          policyId: abcd1234",
                "          assetName: 746f6b656e",
                "          gt: 0",
                "selection:",
                "  order:",
                "    - \"lovelace desc\"",
                "    - \"address asc\"",
                "  limit: 10");

        UtxoFilterSpec spec = UtxoFilterYaml.parse(complexYaml);
        JsonNode serialized = UtxoFilterYaml.toNode(spec);
        UtxoFilterSpec roundTrip = UtxoFilterYaml.parseNode(serialized);

        assertEquals(spec.root(), roundTrip.root());
        assertEquals(spec.selection().getLimit(), roundTrip.selection().getLimit());
        assertEquals(2, roundTrip.selection().getOrder().size());
    }

    @Test
    void serialized_yaml_preserves_simplified_syntax() throws IOException {
        String simplifiedYaml = String.join("\n",
                "address: addr_test1xyz",
                "lovelace: 5000000",
                "inlineDatum: { ne: null }");

        UtxoFilterSpec spec = UtxoFilterYaml.parse(simplifiedYaml);
        JsonNode serialized = UtxoFilterYaml.toNode(spec);

        // Debug: print what was actually serialized
        System.out.println("Serialized: " + serialized.toString());

        // Verify that simple forms are preserved in serialization
        assertTrue(serialized.has("address"));
        assertTrue(serialized.has("lovelace"));
        assertTrue(serialized.has("inlineDatum"));

        // Address should be a simple string value
        assertTrue(serialized.get("address").isTextual());
        assertEquals("addr_test1xyz", serialized.get("address").asText());

        // Lovelace should be a simple numeric value (EQ is default)
        assertTrue(serialized.get("lovelace").isNumber());
        assertEquals(5000000, serialized.get("lovelace").asLong());

        // inlineDatum should be an object with 'ne' key
        assertTrue(serialized.get("inlineDatum").isObject());
        assertTrue(serialized.get("inlineDatum").has("ne"));
    }

    @Test
    void empty_selection_not_serialized() throws IOException {
        String yamlWithoutSelection = "address: addr_test1xyz";

        UtxoFilterSpec spec = UtxoFilterYaml.parse(yamlWithoutSelection);
        JsonNode serialized = UtxoFilterYaml.toNode(spec);

        // Selection should not be present in serialized output when null/empty
        assertFalse(serialized.has("selection"));
    }

    @Test
    void selection_with_all_limit_not_serialized() throws IOException {
        String yamlWithAllLimit = String.join("\n",
                "address: addr_test1xyz",
                "selection:",
                "  order: [ \"lovelace desc\" ]",
                "  limit: all");

        UtxoFilterSpec spec = UtxoFilterYaml.parse(yamlWithAllLimit);
        JsonNode serialized = UtxoFilterYaml.toNode(spec);

        assertTrue(serialized.has("selection"));
        assertTrue(serialized.get("selection").has("order"));
        // 'limit: all' means no limit, so it shouldn't be serialized
        assertFalse(serialized.get("selection").has("limit"));
    }
}