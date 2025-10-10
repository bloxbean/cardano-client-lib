package com.bloxbean.cardano.client.quicktx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.quicktx.intent.ScriptCollectFromIntent;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Minimal YAML serialization/deserialization tests for ScriptTx collectFrom intentions.
 */
class ScriptTxYamlRoundTripTest {

    @Test
    void scriptTx_collectFrom_intention_serializes_to_yaml() {
        // Given a simple ScriptTx with a script collectFrom intention
        Utxo scriptUtxo = Utxo.builder()
                .txHash("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
                .outputIndex(0)
                .address("addr_test1_script_address")
                .amount(List.of(Amount.ada(2)))
                .build();

        PlutusData redeemer = BigIntPlutusData.of(2);
        PlutusData datum = BigIntPlutusData.of(42);

        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(scriptUtxo, redeemer, datum)
                .withChangeAddress("addr_test1_change", datum)
                .payToAddress("addr_test1_receiver", Amount.ada(1));

        // When
        String yaml = TxPlan.from(scriptTx).toYaml();

        // Then
        assertThat(yaml).isNotNull();
        assertThat(yaml).contains("transaction:");
        assertThat(yaml).contains("- scriptTx:");
        // Check for new inputs section structure
        assertThat(yaml).contains("inputs:");
        assertThat(yaml).contains("type: script_collect_from");
        assertThat(yaml).contains("utxo_refs:");
        assertThat(yaml).contains("tx_hash: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(yaml).contains("output_index: 0");
        assertThat(yaml).contains("redeemer_hex:");
        assertThat(yaml).contains("datum_hex:");
        assertThat(yaml).contains("intents:");
        assertThat(yaml).contains("type: payment"); // Regular intents should be in intents section

        // Structure sanity
        Yaml parser = new Yaml();
        Map<String, Object> doc = parser.load(yaml);
        assertThat(doc).containsKey("transaction");
        List<Map<String, Object>> txs = (List<Map<String, Object>>) doc.get("transaction");
        Map<String, Object> entry = txs.get(0);
        assertThat(entry).containsKey("scriptTx");
        Map<String, Object> content = (Map<String, Object>) entry.get("scriptTx");
        assertThat(content).containsKey("intents");
    }

    @Test
    void scriptTx_collectFrom_without_redeemer_or_datum_serializes_intention() {
        // Given - bare collectFrom without redeemer/datum
        Utxo scriptUtxo = Utxo.builder()
                .txHash("cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc")
                .outputIndex(2)
                .build();

        ScriptTx scriptTx = new ScriptTx()
                .collectFrom(scriptUtxo)
                .payToAddress("addr_test1_receiver_c", Amount.ada(3));

        // When
        String yaml = TxPlan.from(scriptTx).toYaml();

        // Then
        assertThat(yaml).contains("type: script_collect_from");
        assertThat(yaml).contains("utxo_refs:");
        assertThat(yaml).contains("tx_hash: cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc");
        assertThat(yaml).contains("output_index: 2");
        // No redeemer/datum entries expected when not provided
        assertThat(yaml).doesNotContain("redeemer_hex:");
        assertThat(yaml).doesNotContain("datum_hex:");
    }
    @Test
    void scriptTx_collectFrom_yaml_round_trip_restores_intention() {
        // Given
        Utxo scriptUtxo = Utxo.builder()
                .txHash("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
                .outputIndex(1)
                .build();

        PlutusData redeemer = BigIntPlutusData.of(9);
        PlutusData datum = BigIntPlutusData.of(7);

        ScriptTx original = new ScriptTx()
                .collectFrom(scriptUtxo, redeemer, datum)
                .payToAddress("addr_test1_receiver_b", Amount.ada(2));

        String yaml = TxPlan.from(original).toYaml();

        // When
        ScriptTx restored = (ScriptTx) TxPlan.getTxs(yaml).get(0);

        // Then
        assertThat(restored).isNotNull();
        assertThat(restored.getIntentions()).isNotEmpty();
        assertThat(restored.getIntentions().get(0)).isInstanceOf(ScriptCollectFromIntent.class);

        ScriptCollectFromIntent intent = (ScriptCollectFromIntent) restored.getIntentions().get(0);
        // From YAML path, utxoRefs/redeemerHex/datumHex should be present
        assertThat(intent.getUtxoRefs()).isNotNull();
        assertThat(intent.getUtxoRefs()).hasSize(1);
        assertThat(intent.getUtxoRefs().get(0).getTxHash()).isEqualTo("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        assertThat(intent.getUtxoRefs().get(0).asIntOutputIndex()).isEqualTo(1);
        assertThat(intent.getRedeemerHex()).isNotBlank();
        assertThat(intent.getDatumHex()).isNotBlank();
    }
}
