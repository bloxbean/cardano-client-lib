package com.bloxbean.cardano.client.quicktx.filter;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.quicktx.filter.runtime.memory.InMemoryUtxoFilterEngine;
import com.bloxbean.cardano.client.quicktx.filter.yaml.UtxoFilterYaml;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UtxoFilterEdgeCasesTest {
    private static Utxo u(String tx, int ix, String addr, Amount... amts) {
        return Utxo.builder().txHash(tx).outputIndex(ix).address(addr).amount(Arrays.asList(amts)).build();
    }
    private static Amount lovelace(long l) { return Amount.lovelace(BigInteger.valueOf(l)); }

    @Test
    void lovelace_all_numeric_ops() throws IOException {
        List<Utxo> utxos = new ArrayList<>();
        utxos.add(u("tx1", 0, "addr", lovelace(5)));
        utxos.add(u("tx2", 0, "addr", lovelace(10)));
        utxos.add(u("tx3", 0, "addr", lovelace(10)));

        // eq
        var specEq = UtxoFilterYaml.parse("lovelace: 10");
        var rEq = InMemoryUtxoFilterEngine.filter(utxos, specEq.root(), specEq.selection());
        assertEquals(2, rEq.size());

        // ne
        var specNe = UtxoFilterYaml.parse("lovelace: { ne: 10 }");
        var rNe = InMemoryUtxoFilterEngine.filter(utxos, specNe.root(), specNe.selection());
        assertEquals(1, rNe.size());
        assertEquals("tx1", rNe.get(0).getTxHash());

        // gt
        var specGt = UtxoFilterYaml.parse("lovelace: { gt: 5 }");
        var rGt = InMemoryUtxoFilterEngine.filter(utxos, specGt.root(), specGt.selection());
        assertEquals(2, rGt.size());

        // gte
        var specGte = UtxoFilterYaml.parse("lovelace: { gte: 5 }");
        var rGte = InMemoryUtxoFilterEngine.filter(utxos, specGte.root(), specGte.selection());
        assertEquals(3, rGte.size());

        // lt
        var specLt = UtxoFilterYaml.parse("lovelace: { lt: 10 }");
        var rLt = InMemoryUtxoFilterEngine.filter(utxos, specLt.root(), specLt.selection());
        assertEquals(1, rLt.size());

        // lte
        var specLte = UtxoFilterYaml.parse("lovelace: { lte: 10 }");
        var rLte = InMemoryUtxoFilterEngine.filter(utxos, specLte.root(), specLte.selection());
        assertEquals(3, rLte.size());
    }

    @Test
    void invalid_string_field_with_numeric_op() {
        assertThrows(IllegalArgumentException.class, () -> UtxoFilterYaml.parse("address: { gt: \"x\" }"));
        assertThrows(IllegalArgumentException.class, () -> UtxoFilterYaml.parse("dataHash: { lt: \"x\" }"));
        assertThrows(IllegalArgumentException.class, () -> UtxoFilterYaml.parse("inlineDatum: { gte: \"x\" }"));
    }

    @Test
    void invalid_address_non_string_value() {
        assertThrows(IllegalArgumentException.class, () -> UtxoFilterYaml.parse("address: 123"));
    }

    @Test
    void amount_missing_unit_and_policy_assetName() {
        assertThrows(IllegalArgumentException.class, () -> UtxoFilterYaml.parse("amount: { gte: 10 }"));
    }

    @Test
    void amount_multiple_ops_disallowed() {
        assertThrows(IllegalArgumentException.class, () -> UtxoFilterYaml.parse("amount: { unit: \"u\", gt: 1, lt: 2 }"));
    }

    @Test
    void selection_order_invalid_field() {
        assertThrows(IllegalArgumentException.class, () -> UtxoFilterYaml.parse("selection:\n  order: [ \"unknown desc\" ]"));
    }

    @Test
    void selection_order_non_string_entries() {
        assertThrows(IllegalArgumentException.class, () -> UtxoFilterYaml.parse("selection:\n  order: [ 1 ]"));
    }

    @Test
    void selection_limit_negative_disallowed() {
        assertThrows(IllegalArgumentException.class, () -> UtxoFilterYaml.parse("selection:\n  limit: -1"));
    }

    @Test
    void amount_nested_quantity_block_supported() throws IOException {
        String yaml = String.join("\n",
                "amount:",
                "  unit: myunit",
                "  quantity: { lt: 5 }");
        var spec = UtxoFilterYaml.parse(yaml);
        List<Utxo> utxos = new ArrayList<>();
        utxos.add(u("tx1", 0, "addr", Amount.asset("myunit", 4)));
        utxos.add(u("tx2", 0, "addr", Amount.asset("myunit", 6)));
        var res = InMemoryUtxoFilterEngine.filter(utxos, spec.root(), spec.selection());
        assertEquals(1, res.size());
        assertEquals("tx1", res.get(0).getTxHash());
    }

    @Test
    void selection_order_amount_unit_missing_paren_errors() {
        assertThrows(IllegalArgumentException.class, () -> UtxoFilterYaml.parse("selection:\n  order: [ \"amount.unit(u\" ]"));
    }

    @Test
    void malformed_yaml_missing_operator() {
        assertThrows(IllegalArgumentException.class, () -> UtxoFilterYaml.parse("address: { invalid: \"value\" }"));
        assertThrows(IllegalArgumentException.class, () -> UtxoFilterYaml.parse("lovelace: { unknown: 123 }"));
    }

    @Test
    void malformed_yaml_empty_filter() {
        assertThrows(IllegalArgumentException.class, () -> UtxoFilterYaml.parse(""));
        assertThrows(IllegalArgumentException.class, () -> UtxoFilterYaml.parse("{}"));
    }

    @Test
    void malformed_yaml_null_filter() {
        assertThrows(IllegalArgumentException.class, () -> UtxoFilterYaml.parse("filter: null"));
    }

    @Test
    void malformed_yaml_invalid_json_structure() {
        assertThrows(Exception.class, () -> UtxoFilterYaml.parse("invalid: [ unclosed"));
        assertThrows(Exception.class, () -> UtxoFilterYaml.parse("{ missing_quote: value }"));
    }

    @Test
    void malformed_yaml_mixed_type_in_logical_ops() {
        assertThrows(IllegalArgumentException.class, () -> UtxoFilterYaml.parse("and: [ { address: \"addr\" }, \"not_an_object\" ]"));
        assertThrows(IllegalArgumentException.class, () -> UtxoFilterYaml.parse("or: [ 123, { lovelace: 100 } ]"));
    }

    @Test
    void malformed_yaml_invalid_selection_structure() {
        assertThrows(IllegalArgumentException.class, () -> UtxoFilterYaml.parse("selection: \"not_an_object\""));
        assertThrows(IllegalArgumentException.class, () -> UtxoFilterYaml.parse("selection:\n  order: \"single_string_should_be_array\""));
        assertThrows(IllegalArgumentException.class, () -> UtxoFilterYaml.parse("selection:\n  limit: \"not_a_number\""));
    }

    @Test
    void malformed_yaml_deeply_nested_invalid_structure() {
        String deeplyNested = String.join("\n",
                "and:",
                "  - or:",
                "      - address: \"addr1\"",
                "      - invalid_field: \"should_fail\"",
                "  - lovelace: { gte: 1000 }");
        assertThrows(IllegalArgumentException.class, () -> UtxoFilterYaml.parse(deeplyNested));
    }

    @Test
    void malformed_yaml_amount_with_both_unit_and_policy_fields() {
        String conflicting = String.join("\n",
                "amount:",
                "  unit: \"unit1\"",
                "  policyId: \"policy1\"",
                "  assetName: \"name1\"",
                "  gte: 10");
        // Should accept both forms but unit takes precedence
        assertDoesNotThrow(() -> UtxoFilterYaml.parse(conflicting));
    }
}
