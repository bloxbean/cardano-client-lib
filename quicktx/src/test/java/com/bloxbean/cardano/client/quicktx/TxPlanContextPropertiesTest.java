package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlanCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        TxPlan restored = TxPlan.from(yaml);

        // Then - verify all context properties are restored
        assertThat(restored.getFeePayer()).isEqualTo("addr1_fee_payer");
        assertThat(restored.getCollateralPayer()).isEqualTo("addr1_collateral_payer");
        assertThat(restored.getRequiredSigners()).containsExactlyInAnyOrder("ab123def", "cd456efg");
        assertThat(restored.getValidFromSlot()).isEqualTo(1000L);
        assertThat(restored.getValidToSlot()).isEqualTo(2000L);

        // Verify transactions are also restored
        assertThat(restored.getTxs()).hasSize(1);
        assertThat(restored.getTxs().get(0)).isInstanceOf(Tx.class);
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
        TxPlan restored = TxPlan.from(yaml);

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
        TxPlan restored = TxPlan.from(yaml);

        // Then - both variables and context are restored
        assertThat(restored.getVariables()).containsEntry("sender", "addr1_sender");
        assertThat(restored.getVariables()).containsEntry("receiver", "addr1_receiver");
        assertThat(restored.getFeePayer()).isEqualTo("addr1_fee_payer");
        assertThat(restored.getValidToSlot()).isEqualTo(2000L);
    }

    @Test
    void additional_signers_count_round_trip() {
        Tx tx = new Tx()
            .from("addr1_sender")
            .payToAddress("addr1_receiver", Amount.ada(5));

        TxPlan plan = TxPlan.from(tx)
            .additionalSignersCount(3);

        String yaml = plan.toYaml();
        TxPlan restored = TxPlan.from(yaml);

        assertThat(yaml).contains("context:");
        assertThat(yaml).contains("additional_signers_count: 3");
        assertThat(restored.getAdditionalSignersCount()).isEqualTo(3);
    }

    @Test
    void additional_signers_count_zero_round_trips_and_unset_is_omitted() {
        Tx tx = new Tx()
            .from("addr1_sender")
            .payToAddress("addr1_receiver", Amount.ada(5));

        TxPlan zero = TxPlan.from(tx).additionalSignersCount(0);
        assertThat(TxPlan.from(zero.toYaml()).getAdditionalSignersCount()).isZero();

        String unsetYaml = TxPlan.from(tx).feePayer("addr1_fee_payer").toYaml();
        assertThat(unsetYaml).doesNotContain("additional_signers_count");
        assertThat(TxPlan.from(unsetYaml).getAdditionalSignersCount()).isNull();
    }

    @Test
    void negative_additional_signers_count_is_rejected() {
        Tx tx = new Tx()
            .from("addr1_sender")
            .payToAddress("addr1_receiver", Amount.ada(5));

        assertThatThrownBy(() -> TxPlan.from(tx).additionalSignersCount(-1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("additional_signers_count");

        String yaml = TxPlan.from(tx).additionalSignersCount(1).toYaml()
            .replace("additional_signers_count: 1", "additional_signers_count: -1");
        assertThatThrownBy(() -> TxPlan.from(yaml))
            .hasStackTraceContaining("additional_signers_count must not be negative");
    }

    @ParameterizedTest
    @ValueSource(ints = {TxPlan.MAX_ADDITIONAL_SIGNERS_COUNT + 1, Integer.MAX_VALUE})
    void oversized_additional_signers_count_is_rejected(int count) {
        Tx tx = new Tx()
            .from("addr1_sender")
            .payToAddress("addr1_receiver", Amount.ada(5));

        assertThatThrownBy(() -> TxPlan.from(tx).additionalSignersCount(count))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("additional_signers_count must not exceed " + TxPlan.MAX_ADDITIONAL_SIGNERS_COUNT);

        String yaml = "context:\n  additional_signers_count: " + count + "\ntransaction: []\n";
        assertThatThrownBy(() -> TxPlan.from(yaml))
            .hasStackTraceContaining("additional_signers_count must not exceed");
        assertThatThrownBy(() -> TxPlanCodec.builder().build().fromYaml(yaml))
            .hasStackTraceContaining("additional_signers_count must not exceed");
    }

    @Test
    void max_additional_signers_count_is_accepted() {
        String yaml = "context:\n  additional_signers_count: " + TxPlan.MAX_ADDITIONAL_SIGNERS_COUNT + "\ntransaction: []\n";

        assertThat(TxPlan.from(yaml).getAdditionalSignersCount()).isEqualTo(TxPlan.MAX_ADDITIONAL_SIGNERS_COUNT);
    }

    @Test
    void additional_signers_count_variable_accepts_any_integer_type_in_range() {
        String yaml = "context:\n  additional_signers_count: '${count}'\ntransaction: []\n";
        TxPlanCodec codec = TxPlanCodec.builder().build();

        for (Object count : List.of(2, 2L, BigInteger.valueOf(2), TxPlan.MAX_ADDITIONAL_SIGNERS_COUNT,
                (long) TxPlan.MAX_ADDITIONAL_SIGNERS_COUNT)) {
            assertThat(codec.fromYaml(yaml, Map.of("count", count)).getAdditionalSignersCount())
                .as("%s %s", count.getClass().getSimpleName(), count)
                .isEqualTo(((Number) count).intValue());
        }

        assertThatThrownBy(() -> codec.fromYaml(yaml, Map.of("count", TxPlan.MAX_ADDITIONAL_SIGNERS_COUNT + 1L)))
            .hasStackTraceContaining("additional_signers_count must not exceed");
        assertThatThrownBy(() -> codec.fromYaml(yaml, Map.of("count", 3_000_000_000L)))
            .hasStackTraceContaining("additional_signers_count must be an integer");
        assertThatThrownBy(() -> codec.fromYaml(yaml, Map.of("count", 2.5d)))
            .hasStackTraceContaining("additional_signers_count must be an integer");
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.5", "2.9", "2.0", "'2'", "3000000000"})
    void non_integer_additional_signers_count_is_rejected(String value) {
        String yaml = "context:\n  additional_signers_count: " + value + "\ntransaction: []\n";

        assertThatThrownBy(() -> TxPlan.from(yaml))
            .hasStackTraceContaining("additional_signers_count must be an integer");
        assertThatThrownBy(() -> TxPlanCodec.builder().build().fromYaml(yaml))
            .hasStackTraceContaining("additional_signers_count must be an integer");
    }
}
