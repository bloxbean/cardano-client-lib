package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for TxPlan context properties serialization/deserialization.
 */
class TxPlanContextPropertiesTest {

    @Test
    void context_properties_serialize_to_yaml() {
        // Given
        Tx tx = new Tx()
            .from("addr1_sender")
            .payToAddress("addr1_receiver", Amount.ada(5));

        TxPlan plan = TxPlan.from(tx)
            .feePayer("addr1_fee_payer")
            .collateralPayer("addr1_collateral_payer")
            .withRequiredSigners("ab123def", "cd456efg")
            .validFrom(1000L)
            .validTo(2000L);

        // When
        String yaml = plan.toYaml();
        System.out.println("Generated YAML with context:");
        System.out.println(yaml);

        // Then
        assertThat(yaml).contains("context:");
        assertThat(yaml).contains("fee_payer: addr1_fee_payer");
        assertThat(yaml).contains("collateral_payer: addr1_collateral_payer");
        assertThat(yaml).contains("required_signers:");
        assertThat(yaml).contains("- ab123def");
        assertThat(yaml).contains("- cd456efg");
        assertThat(yaml).contains("valid_from_slot: 1000");
        assertThat(yaml).contains("valid_to_slot: 2000");
    }

    @Test
    void context_properties_round_trip_restoration() {
        // Given
        Tx tx = new Tx()
            .from("addr1_sender")
            .payToAddress("addr1_receiver", Amount.ada(5));

        TxPlan original = TxPlan.from(tx)
            .feePayer("addr1_fee_payer")
            .collateralPayer("addr1_collateral_payer")
            .withRequiredSigners("ab123def", "cd456efg")
            .validFrom(1000L)
            .validTo(2000L);

        // When - serialize and deserialize
        String yaml = original.toYaml();
        TxPlan restored = TxPlan.fromYamlWithContext(yaml);

        // Then - verify all context properties are restored
        assertThat(restored.getFeePayer()).isEqualTo("addr1_fee_payer");
        assertThat(restored.getCollateralPayer()).isEqualTo("addr1_collateral_payer");
        assertThat(restored.getRequiredSigners()).containsExactlyInAnyOrder("ab123def", "cd456efg");
        assertThat(restored.getValidFromSlot()).isEqualTo(1000L);
        assertThat(restored.getValidToSlot()).isEqualTo(2000L);

        // Verify transactions are also restored
        assertThat(restored.getTransactions()).hasSize(1);
        assertThat(restored.getTransactions().get(0)).isInstanceOf(Tx.class);
    }

    @Test
    void partial_context_properties_serialize_correctly() {
        // Given - only some context properties set
        Tx tx = new Tx()
            .from("addr1_sender")
            .payToAddress("addr1_receiver", Amount.ada(5));

        TxPlan plan = TxPlan.from(tx)
            .feePayer("addr1_fee_payer")
            .validTo(2000L);  // Only fee payer and valid to

        // When
        String yaml = plan.toYaml();

        // Then - only specified properties appear in YAML
        assertThat(yaml).contains("context:");
        assertThat(yaml).contains("fee_payer: addr1_fee_payer");
        assertThat(yaml).contains("valid_to_slot: 2000");

        // Should not contain unset properties
        assertThat(yaml).doesNotContain("collateral_payer:");
        assertThat(yaml).doesNotContain("required_signers:");
        assertThat(yaml).doesNotContain("valid_from_slot:");
    }

    @Test
    void empty_context_properties_omitted_from_yaml() {
        // Given - no context properties set
        Tx tx = new Tx()
            .from("addr1_sender")
            .payToAddress("addr1_receiver", Amount.ada(5));

        TxPlan plan = TxPlan.from(tx);

        // When
        String yaml = plan.toYaml();

        // Then - context section should not appear
        assertThat(yaml).doesNotContain("context:");
    }

    @Test
    void required_signers_handled_correctly() {
        // Given
        Tx tx = new Tx()
            .from("addr1_sender")
            .payToAddress("addr1_receiver", Amount.ada(5));

        TxPlan plan = TxPlan.from(tx)
            .setRequiredSigners(Set.of("ab123def", "cd456efg", "ef789abc"));

        // When - serialize and deserialize
        String yaml = plan.toYaml();
        TxPlan restored = TxPlan.fromYamlWithContext(yaml);

        // Then
        assertThat(restored.getRequiredSigners()).containsExactlyInAnyOrder("ab123def", "cd456efg", "ef789abc");
    }

    @Test
    void null_validity_slots_handled_correctly() {
        // Given
        Tx tx = new Tx()
            .from("addr1_sender")
            .payToAddress("addr1_receiver", Amount.ada(5));

        TxPlan plan = TxPlan.from(tx)
            .feePayer("addr1_fee_payer");
        // Note: Cannot explicitly set null for primitive long slots

        // When
        String yaml = plan.toYaml();

        // Then - null values should not appear in YAML
        assertThat(yaml).contains("fee_payer: addr1_fee_payer");
        assertThat(yaml).doesNotContain("valid_from_slot:");
        assertThat(yaml).doesNotContain("valid_to_slot:");
    }

    @Test
    void variables_and_context_work_together() {
        // Given
        Tx tx = new Tx()
            .from("addr1_sender")
            .payToAddress("addr1_receiver", Amount.ada(5));

        TxPlan plan = TxPlan.from(tx)
            .addVariable("sender", "addr1_sender")
            .addVariable("receiver", "addr1_receiver")
            .feePayer("addr1_fee_payer")
            .validTo(2000L);

        // When
        String yaml = plan.toYaml();
        TxPlan restored = TxPlan.fromYamlWithContext(yaml);

        // Then - both variables and context are restored
        assertThat(restored.getVariables()).containsEntry("sender", "addr1_sender");
        assertThat(restored.getVariables()).containsEntry("receiver", "addr1_receiver");
        assertThat(restored.getFeePayer()).isEqualTo("addr1_fee_payer");
        assertThat(restored.getValidToSlot()).isEqualTo(2000L);
    }
}
