package com.bloxbean.cardano.client.dsl;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TxDslCollectFromTest {

    @Test
    void testCollectFromWithAttributes() {
        // Create test UTXOs
        Utxo utxo1 = new Utxo();
        utxo1.setTxHash("abc123");
        utxo1.setOutputIndex(0);

        Utxo utxo2 = new Utxo();
        utxo2.setTxHash("def456");
        utxo2.setOutputIndex(1);

        // Given
        TxDsl txDsl = new TxDsl()
            .from("addr1_sender...")
            .collectFrom(List.of(utxo1, utxo2))
            .payToAddress("addr1_receiver...", Amount.ada(5));

        // When
        String yaml = txDsl.toYaml();

        System.out.println(yaml);

        // Then
        assertThat(yaml).isNotNull();
        assertThat(yaml).contains("from: addr1_sender...");
        assertThat(yaml).contains("collect_from:");
        assertThat(yaml).contains("tx_hash: abc123");
        assertThat(yaml).contains("output_index: 0");
        assertThat(yaml).contains("tx_hash: def456");
        assertThat(yaml).contains("output_index: 1");
        assertThat(yaml).contains("intentions:");

        // Verify only payment intention (no collectFrom intention)
        assertThat(txDsl.getIntentions()).hasSize(1);
        assertThat(txDsl.getIntentions().get(0).getType()).isEqualTo("payment");
    }

    @Test
    void testCollectFromYamlStructure() {
        // Create test UTXO
        Utxo utxo = new Utxo();
        utxo.setTxHash("test123");
        utxo.setOutputIndex(0);

        // Given
        TxDsl txDsl = new TxDsl()
            .from("addr1_sender...")
            .collectFrom(List.of(utxo))
            .payToAddress("addr1_receiver...", Amount.ada(5));

        // When
        String yamlString = txDsl.toYaml();

        // Then parse YAML to verify structure
        Yaml yaml = new Yaml();
        Map<String, Object> doc = yaml.load(yamlString);

        assertThat(doc).containsKey("version");
        assertThat(doc.get("version")).isEqualTo(1.0);

        // Check for unified transaction structure
        assertThat(doc).containsKey("transaction");
        java.util.List<Map<String, Object>> transaction = (java.util.List<Map<String, Object>>) doc.get("transaction");
        assertThat(transaction).hasSize(1);

        Map<String, Object> firstTx = transaction.get(0);
        assertThat(firstTx).containsKey("tx");
        Map<String, Object> tx = (Map<String, Object>) firstTx.get("tx");

        // Verify attributes
        assertThat(tx).containsKey("from");
        assertThat(tx.get("from")).isEqualTo("addr1_sender...");
        assertThat(tx).containsKey("collect_from");

        // Verify intentions
        assertThat(tx).containsKey("intentions");
        java.util.List<Map<String, Object>> intentions = (java.util.List<Map<String, Object>>) tx.get("intentions");
        assertThat(intentions).hasSize(1);
        assertThat(intentions.get(0).get("type")).isEqualTo("payment");

        // Verify collect_from structure
        java.util.List<Map<String, Object>> collectFrom = (java.util.List<Map<String, Object>>) tx.get("collect_from");
        assertThat(collectFrom).hasSize(1);
        assertThat(collectFrom.get(0).get("tx_hash")).isEqualTo("test123");
        assertThat(collectFrom.get(0).get("output_index")).isEqualTo(0);
    }

    @Test
    void testCollectFromDeserialization() {
        // Create test UTXO
        Utxo utxo = new Utxo();
        utxo.setTxHash("serialize123");
        utxo.setOutputIndex(2);

        // Given
        TxDsl original = new TxDsl()
            .from("addr1_sender...")
            .collectFrom(List.of(utxo))
            .payToAddress("addr1_receiver...", Amount.ada(10));

        // When
        String yaml = original.toYaml();
        TxDsl restored = TxDsl.fromYaml(yaml);

        // Then
        assertThat(restored).isNotNull();
        assertThat(restored.getIntentions()).hasSize(1);
        assertThat(restored.getIntentions().get(0).getType()).isEqualTo("payment");

        // Note: collectFromInputs are preserved as attributes but not as UTXOs
        // (since full UTXO reconstruction requires UtxoSupplier)
    }
}
