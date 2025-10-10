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

class UtxoFilterYamlAndEngineTest {

    private static Utxo utxo(String tx, int ix, String address, String inlineDatum, String dataHash, Amount... amts) {
        return Utxo.builder()
                .txHash(tx)
                .outputIndex(ix)
                .address(address)
                .inlineDatum(inlineDatum)
                .dataHash(dataHash)
                .amount(Arrays.asList(amts))
                .build();
    }

    private static Amount lovelace(long adaLovelace) {
        return Amount.lovelace(BigInteger.valueOf(adaLovelace));
    }

    private static Amount asset(String unit, long qty) {
        return Amount.asset(unit, qty);
    }

    @Test
    void filter_address_and_min_ada_with_order_limit() throws IOException {
        String yaml = String.join("\n",
                "address: addr_test1xyz",
                "lovelace: { gte: 2000000 }",
                "selection:",
                "  order: [ \"lovelace desc\" ]",
                "  limit: 2");

        UtxoFilterSpec spec = UtxoFilterYaml.parse(yaml);

        List<Utxo> utxos = new ArrayList<>();
        utxos.add(utxo("tx1", 0, "addr_test1xyz", null, null, lovelace(1_500_000)));
        utxos.add(utxo("tx2", 0, "addr_test1xyz", null, null, lovelace(2_000_000)));
        utxos.add(utxo("tx3", 0, "addr_test1xyz", null, null, lovelace(5_000_000)));
        utxos.add(utxo("tx4", 0, "addr_other", null, null, lovelace(10_000_000)));

        List<Utxo> result = InMemoryUtxoFilterEngine.filter(utxos, spec.root(), spec.selection());
        assertEquals(2, result.size());
        assertEquals("tx3", result.get(0).getTxHash());
        assertEquals("tx2", result.get(1).getTxHash());
    }

    @Test
    void filter_inlineDatum_present_or_equals_value() throws IOException {
        String yaml = String.join("\n",
                "or:",
                "  - not: { inlineDatum: null }",
                "  - inlineDatum: \"0xabcd\"");

        UtxoFilterSpec spec = UtxoFilterYaml.parse(yaml);

        List<Utxo> utxos = new ArrayList<>();
        utxos.add(utxo("tx1", 0, "addr", null, null, lovelace(1)));
        utxos.add(utxo("tx2", 0, "addr", "0xdead", null, lovelace(1)));
        utxos.add(utxo("tx3", 0, "addr", null, null, lovelace(1)));
        utxos.add(utxo("tx4", 0, "addr", "0xabcd", null, lovelace(1)));

        List<Utxo> result = InMemoryUtxoFilterEngine.filter(utxos, spec.root(), spec.selection());
        // tx2 has any inlineDatum (not null), tx4 equals 0xabcd
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(u -> u.getTxHash().equals("tx2")));
        assertTrue(result.stream().anyMatch(u -> u.getTxHash().equals("tx4")));
    }

    @Test
    void filter_native_asset_by_unit() throws IOException {
        String unit = "policy123asset456";
        String yaml = String.join("\n",
                "amount:",
                "  unit: " + unit,
                "  gte: 10");

        UtxoFilterSpec spec = UtxoFilterYaml.parse(yaml);

        List<Utxo> utxos = new ArrayList<>();
        utxos.add(utxo("tx1", 0, "addr", null, null, lovelace(1_000_000)));
        utxos.add(utxo("tx2", 0, "addr", null, null, lovelace(1_000_000), asset(unit, 5)));
        utxos.add(utxo("tx3", 0, "addr", null, null, lovelace(1_000_000), asset(unit, 10)));

        List<Utxo> result = InMemoryUtxoFilterEngine.filter(utxos, spec.root(), spec.selection());
        assertEquals(1, result.size());
        assertEquals("tx3", result.get(0).getTxHash());
    }

    @Test
    void not_inlineDatum_null_selects_present() throws IOException {
        String yaml = "not: { inlineDatum: null }";
        UtxoFilterSpec spec = UtxoFilterYaml.parse(yaml);

        List<Utxo> utxos = new ArrayList<>();
        utxos.add(utxo("tx1", 0, "addr", null, null, lovelace(1)));
        utxos.add(utxo("tx2", 0, "addr", "0xdead", null, lovelace(1)));

        List<Utxo> result = InMemoryUtxoFilterEngine.filter(utxos, spec.root(), spec.selection());
        assertEquals(1, result.size());
        assertEquals("tx2", result.get(0).getTxHash());
    }

    @Test
    void order_by_asset_desc_then_limit() throws IOException {
        String unit = "pidXYZnameABC";
        String yaml = String.join("\n",
                "selection:",
                "  order: [ \"amount.unit(" + unit + ") desc\" ]",
                "  limit: 2");
        UtxoFilterSpec spec = UtxoFilterYaml.parse(yaml);

        List<Utxo> utxos = new ArrayList<>();
        utxos.add(utxo("tx1", 0, "addr", null, null, asset(unit, 1)));
        utxos.add(utxo("tx2", 0, "addr", null, null, asset(unit, 3)));
        utxos.add(utxo("tx3", 0, "addr", null, null));

        List<Utxo> result = InMemoryUtxoFilterEngine.filter(utxos, spec.root(), spec.selection());
        assertEquals(2, result.size());
        assertEquals("tx2", result.get(0).getTxHash());
        assertEquals("tx1", result.get(1).getTxHash());
    }
}
