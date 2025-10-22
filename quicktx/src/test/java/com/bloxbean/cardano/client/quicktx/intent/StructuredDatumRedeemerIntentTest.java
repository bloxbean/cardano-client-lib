package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.quicktx.serialization.YamlSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for structured datum/redeemer support in Intent classes.
 * Tests YAML serialization/deserialization, variable resolution, and precedence.
 */
class StructuredDatumRedeemerIntentTest {

    private static final ObjectMapper YAML_MAPPER = YamlSerializer.getYamlMapper();

    // ==================== ScriptCollectFromIntent Tests ====================

    @Test
    void scriptCollectFromIntent_deserializesStructuredDatum() throws Exception {
        String yaml = "type: script_collect_from\n" +
                "utxo_refs:\n" +
                "  - tx_hash: abc123def456abc123def456abc123def456abc123def456abc123def456abcd\n" +
                "    output_index: 0\n" +
                "datum:\n" +
                "  constructor: 0\n" +
                "  fields:\n" +
                "    - \"@name\": seller\n" +
                "      bytes: 48656c6c6f\n" +
                "    - \"@name\": price\n" +
                "      int: 100\n";

        ScriptCollectFromIntent intent = YAML_MAPPER.readValue(yaml, ScriptCollectFromIntent.class);

        // Check that structured datum was deserialized
        assertNotNull(intent.getDatumStructured(), "Structured datum should be deserialized from YAML");

        // Resolve variables to trigger datum construction
        Map<String, Object> vars = new HashMap<>();
        TxIntent resolved = intent.resolveVariables(vars);

        ScriptCollectFromIntent resolvedIntent = (ScriptCollectFromIntent) resolved;
        PlutusData datum = resolvedIntent.getDatum();

        assertNotNull(datum, "Datum should be constructed from structured format");
        assertTrue(datum instanceof ConstrPlutusData);
        ConstrPlutusData constr = (ConstrPlutusData) datum;
        assertEquals(0, constr.getAlternative());
        assertEquals(2, constr.getData().getPlutusDataList().size());
    }

    @Test
    void scriptCollectFromIntent_deserializesStructuredRedeemer() throws Exception {
        String yaml = "type: script_collect_from\n" +
                "utxo_refs:\n" +
                "  - tx_hash: abc123def456abc123def456abc123def456abc123def456abc123def456abcd\n" +
                "    output_index: 0\n" +
                "redeemer:\n" +
                "  constructor: 1\n" +
                "  fields:\n" +
                "    - \"@name\": action\n" +
                "      int: 0\n";

        ScriptCollectFromIntent intent = YAML_MAPPER.readValue(yaml, ScriptCollectFromIntent.class);

        Map<String, Object> vars = new HashMap<>();
        TxIntent resolved = intent.resolveVariables(vars);

        ScriptCollectFromIntent resolvedIntent = (ScriptCollectFromIntent) resolved;
        PlutusData redeemer = resolvedIntent.getRedeemerData();

        assertNotNull(redeemer);
        assertTrue(redeemer instanceof ConstrPlutusData);
        ConstrPlutusData constr = (ConstrPlutusData) redeemer;
        assertEquals(1, constr.getAlternative());
    }

    @Test
    void scriptCollectFromIntent_variableResolutionInDatum() throws Exception {
        String yaml = "type: script_collect_from\n" +
                "utxo_refs:\n" +
                "  - tx_hash: abc123def456abc123def456abc123def456abc123def456abc123def456abcd\n" +
                "    output_index: 0\n" +
                "datum:\n" +
                "  constructor: 0\n" +
                "  fields:\n" +
                "    - bytes: \"${seller}\"\n" +
                "    - int: \"${price}\"\n";

        ScriptCollectFromIntent intent = YAML_MAPPER.readValue(yaml, ScriptCollectFromIntent.class);

        Map<String, Object> vars = new HashMap<>();
        vars.put("seller", "48656c6c6f");
        vars.put("price", "10000000");

        TxIntent resolved = intent.resolveVariables(vars);
        ScriptCollectFromIntent resolvedIntent = (ScriptCollectFromIntent) resolved;
        PlutusData datum = resolvedIntent.getDatum();

        assertNotNull(datum);
        ConstrPlutusData constr = (ConstrPlutusData) datum;

        // Verify first field (bytes)
        PlutusData firstField = constr.getData().getPlutusDataList().get(0);
        assertTrue(firstField instanceof BytesPlutusData);

        // Verify second field (int)
        PlutusData secondField = constr.getData().getPlutusDataList().get(1);
        assertTrue(secondField instanceof BigIntPlutusData);
        assertEquals(new BigInteger("10000000"), ((BigIntPlutusData) secondField).getValue());
    }

    @Test
    void scriptCollectFromIntent_roundTripPreservesData() throws Exception {
        // Create intent with runtime PlutusData
        ConstrPlutusData originalDatum = ConstrPlutusData.builder()
                .alternative(0)
                .data(ListPlutusData.of(
                        BytesPlutusData.of("48656c6c6f"),
                        BigIntPlutusData.of(100)
                ))
                .build();

        ScriptCollectFromIntent originalIntent = ScriptCollectFromIntent.builder()
                .utxoRefs(java.util.List.of(
                    UtxoRef.builder()
                        .txHash("abc123def456abc123def456abc123def456abc123def456abc123def456abcd")
                        .outputIndex(0)
                        .build()
                ))
                .datum(originalDatum)
                .build();

        // Serialize to YAML
        String yaml = YAML_MAPPER.writeValueAsString(originalIntent);

        // Deserialize back
        ScriptCollectFromIntent deserialized = YAML_MAPPER.readValue(yaml, ScriptCollectFromIntent.class);

        // Resolve to construct PlutusData from structured format
        ScriptCollectFromIntent resolved = (ScriptCollectFromIntent) deserialized.resolveVariables(new HashMap<>());

        // Verify datum is preserved
        assertNotNull(resolved.getDatumStructured());
    }

    @Test
    void scriptCollectFromIntent_hexTakesPrecedenceOverStructured() throws Exception {
        // Create datum
        ConstrPlutusData datum = ConstrPlutusData.builder()
                .alternative(0)
                .data(ListPlutusData.of(BigIntPlutusData.of(100)))
                .build();
        String datumHex = datum.serializeToHex();

        String yaml = "type: script_collect_from\n" +
                "utxo_refs:\n" +
                "  - tx_hash: abc123def456abc123def456abc123def456abc123def456abc123def456abcd\n" +
                "    output_index: 0\n" +
                "datum_hex: " + datumHex + "\n" +
                "datum:\n" +
                "  constructor: 0\n" +
                "  fields:\n" +
                "    - int: 999\n";

        ScriptCollectFromIntent intent = YAML_MAPPER.readValue(yaml, ScriptCollectFromIntent.class);

        // getDatumStructured should return null when datum_hex is present
        assertNull(intent.getDatumStructured());
    }

    // ==================== PaymentIntent Tests ====================

    @Test
    void paymentIntent_deserializesStructuredDatum() throws Exception {
        String yaml = "type: payment\n" +
                "address: addr_test1_receiver\n" +
                "amounts:\n" +
                "  - unit: lovelace\n" +
                "    quantity: 10000000\n" +
                "datum:\n" +
                "  constructor: 0\n" +
                "  fields:\n" +
                "    - \"@name\": metadata\n" +
                "      bytes: 48656c6c6f\n";

        PaymentIntent intent = YAML_MAPPER.readValue(yaml, PaymentIntent.class);

        Map<String, Object> vars = new HashMap<>();
        TxIntent resolved = intent.resolveVariables(vars);

        PaymentIntent resolvedIntent = (PaymentIntent) resolved;
        PlutusData datum = resolvedIntent.getDatum();

        assertNotNull(datum);
        assertTrue(datum instanceof ConstrPlutusData);
    }

    @Test
    void paymentIntent_variableResolutionInDatum() throws Exception {
        String yaml = "type: payment\n" +
                "address: addr_test1_receiver\n" +
                "amounts:\n" +
                "  - unit: lovelace\n" +
                "    quantity: 10000000\n" +
                "datum:\n" +
                "  constructor: 0\n" +
                "  fields:\n" +
                "    - bytes: \"${metadata}\"\n";

        PaymentIntent intent = YAML_MAPPER.readValue(yaml, PaymentIntent.class);

        Map<String, Object> vars = new HashMap<>();
        vars.put("metadata", "48656c6c6f");

        TxIntent resolved = intent.resolveVariables(vars);
        PaymentIntent resolvedIntent = (PaymentIntent) resolved;
        PlutusData datum = resolvedIntent.getDatum();

        assertNotNull(datum);
        assertTrue(datum instanceof ConstrPlutusData);
    }

    // ==================== ScriptMintingIntent Tests ====================

    @Test
    void scriptMintingIntent_deserializesStructuredRedeemer() throws Exception {
        String yaml = "type: script_minting\n" +
                "policyId: abc123\n" +
                "assets:\n" +
                "  - name: token1\n" +
                "    value: 1000\n" +
                "redeemer:\n" +
                "  constructor: 0\n" +
                "  fields:\n" +
                "    - \"@name\": action\n" +
                "      int: 1\n";

        ScriptMintingIntent intent = YAML_MAPPER.readValue(yaml, ScriptMintingIntent.class);

        Map<String, Object> vars = new HashMap<>();
        TxIntent resolved = intent.resolveVariables(vars);

        ScriptMintingIntent resolvedIntent = (ScriptMintingIntent) resolved;
        PlutusData redeemer = resolvedIntent.getRedeemer();

        assertNotNull(redeemer);
        assertTrue(redeemer instanceof ConstrPlutusData);
    }

    @Test
    void scriptMintingIntent_deserializesStructuredOutputDatum() throws Exception {
        String yaml = "type: script_minting\n" +
                "policyId: abc123\n" +
                "assets:\n" +
                "  - name: token1\n" +
                "    value: 1000\n" +
                "receiver: addr_test1_receiver\n" +
                "output_datum:\n" +
                "  constructor: 0\n" +
                "  fields:\n" +
                "    - \"@name\": token_data\n" +
                "      bytes: abcd1234\n";

        ScriptMintingIntent intent = YAML_MAPPER.readValue(yaml, ScriptMintingIntent.class);

        Map<String, Object> vars = new HashMap<>();
        TxIntent resolved = intent.resolveVariables(vars);

        ScriptMintingIntent resolvedIntent = (ScriptMintingIntent) resolved;
        PlutusData outputDatum = resolvedIntent.getOutputDatum();

        assertNotNull(outputDatum);
        assertTrue(outputDatum instanceof ConstrPlutusData);
    }

    @Test
    void scriptMintingIntent_variableResolutionInBothFields() throws Exception {
        String yaml = "type: script_minting\n" +
                "policyId: abc123\n" +
                "assets:\n" +
                "  - name: token1\n" +
                "    value: 1000\n" +
                "receiver: addr_test1_receiver\n" +
                "redeemer:\n" +
                "  constructor: 0\n" +
                "  fields:\n" +
                "    - int: \"${action}\"\n" +
                "output_datum:\n" +
                "  constructor: 0\n" +
                "  fields:\n" +
                "    - bytes: \"${token_data}\"\n";

        ScriptMintingIntent intent = YAML_MAPPER.readValue(yaml, ScriptMintingIntent.class);

        Map<String, Object> vars = new HashMap<>();
        vars.put("action", "1");
        vars.put("token_data", "abcd1234");

        TxIntent resolved = intent.resolveVariables(vars);
        ScriptMintingIntent resolvedIntent = (ScriptMintingIntent) resolved;

        assertNotNull(resolvedIntent.getRedeemer());
        assertNotNull(resolvedIntent.getOutputDatum());
    }

    // ==================== CBOR Identity Tests ====================

    @Test
    void datumWithNameAnnotations_producesIdenticalCBOR() throws Exception {
        String yamlWith = "type: script_collect_from\n" +
                "utxo_refs:\n" +
                "  - tx_hash: abc123def456abc123def456abc123def456abc123def456abc123def456abcd\n" +
                "    output_index: 0\n" +
                "datum:\n" +
                "  constructor: 0\n" +
                "  fields:\n" +
                "    - \"@name\": seller\n" +
                "      bytes: 48656c6c6f\n" +
                "    - \"@name\": price\n" +
                "      int: 100\n";

        String yamlWithout = "type: script_collect_from\n" +
                "utxo_refs:\n" +
                "  - tx_hash: abc123def456abc123def456abc123def456abc123def456abc123def456abcd\n" +
                "    output_index: 0\n" +
                "datum:\n" +
                "  constructor: 0\n" +
                "  fields:\n" +
                "    - bytes: 48656c6c6f\n" +
                "    - int: 100\n";

        ScriptCollectFromIntent intentWith = YAML_MAPPER.readValue(yamlWith, ScriptCollectFromIntent.class);
        ScriptCollectFromIntent intentWithout = YAML_MAPPER.readValue(yamlWithout, ScriptCollectFromIntent.class);

        ScriptCollectFromIntent resolvedWith = (ScriptCollectFromIntent) intentWith.resolveVariables(new HashMap<>());
        ScriptCollectFromIntent resolvedWithout = (ScriptCollectFromIntent) intentWithout.resolveVariables(new HashMap<>());

        PlutusData datumWith = resolvedWith.getDatum();
        PlutusData datumWithout = resolvedWithout.getDatum();

        // CRITICAL: Must produce identical CBOR
        assertEquals(datumWith.serializeToHex(), datumWithout.serializeToHex());
        assertEquals(datumWith, datumWithout);
    }
}
