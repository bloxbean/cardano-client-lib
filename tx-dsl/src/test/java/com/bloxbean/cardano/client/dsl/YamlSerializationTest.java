package com.bloxbean.cardano.client.dsl;

import com.bloxbean.cardano.client.api.model.Amount;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class YamlSerializationTest {

    @Test
    void testToYamlFormat() {
        // Given
        TxDsl txDsl = new TxDsl()
            .from("addr1_sender...")
            .payToAddress("addr1_receiver...", Amount.ada(5));

        // When
        String yaml = txDsl.toYaml();

        System.out.println(yaml);

        // Then
        assertThat(yaml).isNotNull();
        assertThat(yaml).contains("version:");
        assertThat(yaml).contains("transaction:");
        assertThat(yaml).contains("- tx:");
        assertThat(yaml).contains("intentions:");
    }

    @Test
    void testYamlStructure() {
        // Given
        TxDsl txDsl = new TxDsl()
            .from("addr1_sender...")
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
        assertThat(tx).containsKey("intentions");
    }

    @Test
    void testFromYamlReconstruction() {
        // Given
        TxDsl original = new TxDsl()
            .from("addr1_sender...")
            .payToAddress("addr1_receiver...", Amount.ada(10));

        // When
        String yaml = original.toYaml();
        TxDsl restored = TxDsl.fromYaml(yaml);

        // Then
        assertThat(restored).isNotNull();
        assertThat(restored.getIntentions()).hasSize(2);
        assertThat(restored.getIntentions().get(0).getType()).isEqualTo("from");
        assertThat(restored.getIntentions().get(1).getType()).isEqualTo("payment");
    }

    @Test
    void testRoundTripSerialization() {
        // Given
        TxDsl original = new TxDsl()
            .from("addr1_treasury...")
            .payToAddress("addr1_alice...", Amount.ada(5))
            .payToAddress("addr1_bob...", Amount.ada(3));

        // When
        String yaml1 = original.toYaml();
        TxDsl restored = TxDsl.fromYaml(yaml1);
        String yaml2 = restored.toYaml();

        // Then
        assertThat(restored.getIntentions()).hasSize(3);
        // YAML should be equivalent (may have different formatting)
        Yaml yamlParser = new Yaml();
        Map<String, Object> doc1 = yamlParser.load(yaml1);
        Map<String, Object> doc2 = yamlParser.load(yaml2);
        assertThat(doc2).isEqualTo(doc1);
    }
}
