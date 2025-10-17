package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple test to verify basic YAML serialization functionality.
 */
class SimpleYamlTest {

    @Test
    void testBasicToYamlWorks() {
        // Given
        Tx tx = new Tx()
            .from("addr1_test")
            .payToAddress("addr1_receiver", Amount.ada(5));

        // When
        String yaml = TxPlan.from(tx).toYaml();

        // Then
        System.out.println("Generated YAML:");
        System.out.println(yaml);

        assertThat(yaml).isNotNull();
        assertThat(yaml).contains("version: 1.0");
        assertThat(yaml).contains("transaction:");
        assertThat(yaml).contains("- tx:");
        assertThat(yaml).contains("from: addr1_test");
        assertThat(yaml).contains("intents:");
        assertThat(yaml).doesNotContain("variables:");  // Should not appear if empty
        assertThat(yaml).doesNotContain("null");  // No null values
        assertThat(yaml).doesNotContain("!<");  // No type tags
    }
}
