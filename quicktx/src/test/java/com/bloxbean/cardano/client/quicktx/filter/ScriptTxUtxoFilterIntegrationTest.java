package com.bloxbean.cardano.client.quicktx.filter;

import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.quicktx.AbstractTx;
import com.bloxbean.cardano.client.quicktx.ScriptTx;
import com.bloxbean.cardano.client.quicktx.intent.ScriptCollectFromIntent;
import com.bloxbean.cardano.client.quicktx.intent.TxIntent;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.bloxbean.cardano.client.quicktx.filter.dsl.UtxoFilters.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests verifying that UtxoFilterSpec serializes and deserializes correctly
 * when used with ScriptTx in YAML round-trips through TxPlan.
 *
 * These tests ensure the critical serialization path works end-to-end.
 */
class ScriptTxUtxoFilterIntegrationTest {

    private static final String SCRIPT_ADDRESS = "addr_test1wqag3rt979nep9g2wtdwu8mr4gz6m4kjdpp37wx8pnh8dqq9pqm7e";
    private static final PlutusData REDEEMER = PlutusData.unit();
    private static final PlutusData DATUM = PlutusData.unit();

    // ========== Basic Serialization Tests ==========

    @Test
    void scriptTx_withSimpleFilter_serializesToYaml() {
        UtxoFilterSpec filter = com.bloxbean.cardano.client.quicktx.filter.dsl.Spec.of(
                lovelace().gte(2_000_000)
        ).limit(5).build();

        ScriptTx tx = new ScriptTx()
                .collectFrom(SCRIPT_ADDRESS, filter, REDEEMER, DATUM);

        String yaml = TxPlan.toYaml(tx);

        // Verify YAML contains filter specification
        assertTrue(yaml.contains("lovelace"));
        assertTrue(yaml.contains("gte: 2000000"));
        assertTrue(yaml.contains("limit: 5"));
    }

    @Test
    void scriptTx_withNewFields_serializesToYaml() {
        UtxoFilterSpec filter = com.bloxbean.cardano.client.quicktx.filter.dsl.Spec.of(
                and(
                        referenceScriptHash().notNull(),
                        outputIndex().lt(5)
                )
        ).build();

        ScriptTx tx = new ScriptTx()
                .collectFrom(SCRIPT_ADDRESS, filter, REDEEMER, DATUM);

        String yaml = TxPlan.toYaml(tx);

        // Verify YAML contains new field filters
        assertTrue(yaml.contains("referenceScriptHash"));
        assertTrue(yaml.contains("outputIndex"));
        assertTrue(yaml.contains("lt: 5"));
    }

    // ========== Deserialization Tests ==========

    @Test
    void scriptTx_withFilter_deserializesFromYaml() {
        UtxoFilterSpec filter = com.bloxbean.cardano.client.quicktx.filter.dsl.Spec.of(
                and(
                        lovelace().gte(2_000_000),
                        dataHash().eq("0xabc")
                )
        ).orderBy(lovelaceDesc()).limit(2).build();

        ScriptTx tx = new ScriptTx()
                .collectFrom(SCRIPT_ADDRESS, filter, REDEEMER, DATUM);

        String yaml = TxPlan.toYaml(tx);
        List<AbstractTx<?>> restored = TxPlan.getTxs(yaml);

        assertEquals(1, restored.size());
        assertTrue(restored.get(0) instanceof ScriptTx);

        ScriptTx restoredTx = (ScriptTx) restored.get(0);
        assertNotNull(restoredTx.getIntentions());
        assertTrue(restoredTx.getIntentions().stream()
                .anyMatch(i -> i instanceof ScriptCollectFromIntent));
    }

    @Test
    void scriptTx_withNewFieldsFilter_deserializesFromYaml() {
        UtxoFilterSpec filter = com.bloxbean.cardano.client.quicktx.filter.dsl.Spec.of(
                txHash().eq("abc123")
        ).build();

        ScriptTx tx = new ScriptTx()
                .collectFrom(SCRIPT_ADDRESS, filter, REDEEMER, DATUM);

        String yaml = TxPlan.toYaml(tx);
        List<AbstractTx<?>> restored = TxPlan.getTxs(yaml);

        assertEquals(1, restored.size());
        ScriptTx restoredTx = (ScriptTx) restored.get(0);
        assertNotNull(restoredTx.getIntentions());
    }

    // ========== Round-Trip Tests ==========

    @Test
    void scriptTx_complexFilter_roundTrip() {
        UtxoFilterSpec filter = com.bloxbean.cardano.client.quicktx.filter.dsl.Spec.of(
                or(
                        and(address().eq("addr1"), lovelace().gte(1_000_000)),
                        and(referenceScriptHash().notNull(), lovelace().gte(2_000_000))
                )
        ).build();

        ScriptTx original = new ScriptTx()
                .collectFrom(SCRIPT_ADDRESS, filter, REDEEMER, DATUM);

        String yaml = TxPlan.toYaml(original);
        List<AbstractTx<?>> restored = TxPlan.getTxs(yaml);

        // Verify structure preserved
        ScriptTx restoredTx = (ScriptTx) restored.get(0);
        assertNotNull(restoredTx.getIntentions());
        assertEquals(1, restoredTx.getIntentions().size());

        // Verify it's a ScriptCollectFromIntent
        TxIntent intent = restoredTx.getIntentions().get(0);
        assertTrue(intent instanceof ScriptCollectFromIntent);
    }

    @Test
    void scriptTx_filterWithSelection_roundTrip() {
        UtxoFilterSpec filter = com.bloxbean.cardano.client.quicktx.filter.dsl.Spec.of(
                lovelace().gte(1_000_000)
        ).orderBy(lovelaceDesc(), outputIndexAsc()).limit(3).build();

        ScriptTx original = new ScriptTx()
                .collectFrom(SCRIPT_ADDRESS, filter, REDEEMER, DATUM);

        String yaml = TxPlan.toYaml(original);

        // Verify order and limit in YAML
        assertTrue(yaml.contains("order:"));
        assertTrue(yaml.contains("lovelace desc"));
        assertTrue(yaml.contains("outputIndex asc"));
        assertTrue(yaml.contains("limit: 3"));

        List<AbstractTx<?>> restored = TxPlan.getTxs(yaml);
        ScriptTx restoredTx = (ScriptTx) restored.get(0);
        assertNotNull(restoredTx.getIntentions());
    }

    @Test
    void scriptTx_specificUtxoFilter_roundTrip() {
        // Filter for specific UTXO by txHash + outputIndex
        UtxoFilterSpec filter = com.bloxbean.cardano.client.quicktx.filter.dsl.Spec.of(
                and(
                        txHash().eq("abc123"),
                        outputIndex().eq(1)
                )
        ).build();

        ScriptTx original = new ScriptTx()
                .collectFrom(SCRIPT_ADDRESS, filter, REDEEMER, DATUM);

        String yaml = TxPlan.toYaml(original);

        // Verify specific UTXO identification in YAML
        assertTrue(yaml.contains("txHash: \"abc123\"") || yaml.contains("txHash: abc123"));
        assertTrue(yaml.contains("outputIndex: 1"));

        List<AbstractTx<?>> restored = TxPlan.getTxs(yaml);
        ScriptTx restoredTx = (ScriptTx) restored.get(0);
        assertNotNull(restoredTx.getIntentions());
    }

    // ========== Multiple CollectFrom Tests ==========

    @Test
    void multipleCollectFrom_differentFilters() {
        UtxoFilterSpec filter1 = com.bloxbean.cardano.client.quicktx.filter.dsl.Spec.of(
                referenceScriptHash().notNull()
        ).build();

        UtxoFilterSpec filter2 = com.bloxbean.cardano.client.quicktx.filter.dsl.Spec.of(
                lovelace().gte(5_000_000)
        ).build();

        String scriptAddr1 = "addr_test1wqag3rt979nep9g2wtdwu8mr4gz6m4kjdpp37wx8pnh8dqq9pqm7e";
        String scriptAddr2 = "addr_test1wqag3rt979nep9g2wtdwu8mr4gz6m4kjdpp37wx8pnh8dqqyyyyyy";

        ScriptTx tx = new ScriptTx()
                .collectFrom(scriptAddr1, filter1, REDEEMER, DATUM)
                .collectFrom(scriptAddr2, filter2, REDEEMER, DATUM);

        String yaml = TxPlan.toYaml(tx);

        // Verify both filters in YAML
        assertTrue(yaml.contains("referenceScriptHash"));
        assertTrue(yaml.contains("lovelace"));

        List<AbstractTx<?>> restored = TxPlan.getTxs(yaml);
        ScriptTx restoredTx = (ScriptTx) restored.get(0);

        // Should have 2 ScriptCollectFromIntent instances
        long collectFromCount = restoredTx.getIntentions().stream()
                .filter(i -> i instanceof ScriptCollectFromIntent)
                .count();
        assertEquals(2, collectFromCount);
    }

    // ========== Complex Scenario Tests ==========

    @Test
    void scriptTx_withAllNewFields_roundTrip() {
        UtxoFilterSpec filter = com.bloxbean.cardano.client.quicktx.filter.dsl.Spec.of(
                and(
                        referenceScriptHash().eq("script123"),
                        txHash().ne("excluded_tx"),
                        outputIndex().gte(0)
                )
        ).orderBy(referenceScriptHashAsc(), txHashDesc(), outputIndexAsc())
                .limit(10)
                .build();

        ScriptTx original = new ScriptTx()
                .collectFrom(SCRIPT_ADDRESS, filter, REDEEMER, DATUM);

        String yaml = TxPlan.toYaml(original);

        // Verify all new fields present
        assertTrue(yaml.contains("referenceScriptHash"));
        assertTrue(yaml.contains("txHash"));
        assertTrue(yaml.contains("outputIndex"));
        assertTrue(yaml.contains("order:"));
        assertTrue(yaml.contains("limit: 10"));

        List<AbstractTx<?>> restored = TxPlan.getTxs(yaml);
        ScriptTx restoredTx = (ScriptTx) restored.get(0);
        assertNotNull(restoredTx.getIntentions());
        assertEquals(1, restoredTx.getIntentions().size());
    }

    @Test
    void scriptTx_withTxPlanContext_roundTrip() {
        UtxoFilterSpec filter = com.bloxbean.cardano.client.quicktx.filter.dsl.Spec.of(
                referenceScriptHash().notNull()
        ).build();

        ScriptTx tx = new ScriptTx()
                .collectFrom(SCRIPT_ADDRESS, filter, REDEEMER, DATUM);

        // Use TxPlan with context
        TxPlan plan = TxPlan.from(tx)
                .feePayer("addr_test1...")
                .collateralPayer("addr_test1...");

        String yaml = plan.toYaml();

        // Verify context and filter both present
        assertTrue(yaml.contains("fee_payer"));
        assertTrue(yaml.contains("collateral_payer"));
        assertTrue(yaml.contains("referenceScriptHash"));

        TxPlan restoredPlan = TxPlan.from(yaml);
        assertEquals(1, restoredPlan.getTxs().size());
        assertNotNull(restoredPlan.getFeePayer());
        assertNotNull(restoredPlan.getCollateralPayer());
    }

    // ========== Edge Cases ==========

    @Test
    void scriptTx_emptyFilter_works() {
        // Selection only (no filter conditions)
        UtxoFilterSpec filter = com.bloxbean.cardano.client.quicktx.filter.dsl.Spec.of(
                lovelace().gte(0) // Always true
        ).limit(1).build();

        ScriptTx tx = new ScriptTx()
                .collectFrom(SCRIPT_ADDRESS, filter, REDEEMER, DATUM);

        String yaml = TxPlan.toYaml(tx);
        List<AbstractTx<?>> restored = TxPlan.getTxs(yaml);

        assertEquals(1, restored.size());
    }

    @Test
    void scriptTx_onlyNewFields_roundTrip() {
        // Filter using ONLY new fields (no existing fields)
        UtxoFilterSpec filter = com.bloxbean.cardano.client.quicktx.filter.dsl.Spec.of(
                and(
                        referenceScriptHash().notNull(),
                        outputIndex().lt(10)
                )
        ).build();

        ScriptTx original = new ScriptTx()
                .collectFrom(SCRIPT_ADDRESS, filter, REDEEMER, DATUM);

        String yaml = TxPlan.toYaml(original);
        List<AbstractTx<?>> restored = TxPlan.getTxs(yaml);

        ScriptTx restoredTx = (ScriptTx) restored.get(0);
        assertNotNull(restoredTx.getIntentions());
    }

    @Test
    void scriptTx_filterWithNullValues_roundTrip() {
        // Test null value handling with new fields
        UtxoFilterSpec filter = com.bloxbean.cardano.client.quicktx.filter.dsl.Spec.of(
                and(
                        referenceScriptHash().isNull(),
                        lovelace().gte(1_000_000)
                )
        ).build();

        ScriptTx original = new ScriptTx()
                .collectFrom(SCRIPT_ADDRESS, filter, REDEEMER, DATUM);

        String yaml = TxPlan.toYaml(original);

        // Verify null serialization
        assertTrue(yaml.contains("referenceScriptHash: null") ||
                yaml.contains("referenceScriptHash:\n"));

        List<AbstractTx<?>> restored = TxPlan.getTxs(yaml);
        ScriptTx restoredTx = (ScriptTx) restored.get(0);
        assertNotNull(restoredTx.getIntentions());
    }
}
