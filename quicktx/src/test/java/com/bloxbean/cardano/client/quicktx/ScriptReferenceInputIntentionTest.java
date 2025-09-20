package com.bloxbean.cardano.client.quicktx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;

import com.bloxbean.cardano.client.api.model.Utxo;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptReferenceInputIntentionTest {

    @Test
    void reference_inputs_serialize_to_yaml() {
        Utxo refUtxo = Utxo.builder()
                .txHash("dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd")
                .outputIndex(3)
                .build();

        ScriptTx scriptTx = new ScriptTx()
                .readFrom(refUtxo);

        String yaml = TxPlan.from(scriptTx).toYaml();
        assertThat(yaml).contains("type: reference_input");
        assertThat(yaml).contains("tx_hash: dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd");
        assertThat(yaml).contains("output_index: 3");

        Yaml parser = new Yaml();
        Map<String, Object> doc = parser.load(yaml);
        List<Map<String, Object>> txs = (List<Map<String, Object>>) doc.get("transaction");
        Map<String, Object> content = (Map<String, Object>) txs.get(0).get("scriptTx");
        List<Map<String, Object>> intentions = (List<Map<String, Object>>) content.get("inputs");
        assertThat(intentions.stream().anyMatch(i -> "reference_input".equals(i.get("type")))).isTrue();
    }

    @Test
    void reference_inputs_round_trip_restores_intention() {
        ScriptTx original = new ScriptTx()
                .readFrom("eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee", 4);

        String yaml = TxPlan.from(original).toYaml();
        ScriptTx restored = (ScriptTx) TxPlan.fromYaml(yaml).get(0);

        assertThat(restored).isNotNull();
        assertThat(restored.getIntentions().stream().anyMatch(i -> "reference_input".equals(i.getType()))).isTrue();
    }
}

