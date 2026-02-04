package com.bloxbean.cardano.client.txflow;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for TxFlow.
 */
class TxFlowTest {

    @Test
    void builder_shouldCreateValidFlow() {
        // Given
        Tx depositTx = new Tx()
                .from("addr1_sender")
                .payToAddress("addr1_contract", Amount.ada(100));

        Tx withdrawTx = new Tx()
                .from("addr1_contract")
                .payToAddress("addr1_receiver", Amount.ada(50));

        // When
        TxFlow flow = TxFlow.builder("test-flow")
                .withDescription("Test flow")
                .addVariable("amount", 100_000_000L)
                .addStep(FlowStep.builder("deposit")
                        .withDescription("Deposit ADA")
                        .withTxContext(builder -> builder.compose(depositTx))
                        .build())
                .addStep(FlowStep.builder("withdraw")
                        .withDescription("Withdraw ADA")
                        .dependsOn("deposit")
                        .withTxContext(builder -> builder.compose(withdrawTx))
                        .build())
                .build();

        // Then
        assertThat(flow.getId()).isEqualTo("test-flow");
        assertThat(flow.getDescription()).isEqualTo("Test flow");
        assertThat(flow.getSteps()).hasSize(2);
        assertThat(flow.getVariables()).containsKey("amount");
    }

    @Test
    void builder_shouldFailWithNoSteps() {
        // When/Then
        assertThatThrownBy(() ->
                TxFlow.builder("empty-flow").build()
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least one step");
    }

    @Test
    void validate_shouldPassForValidFlow() {
        // Given
        TxFlow flow = TxFlow.builder("valid-flow")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .addStep(FlowStep.builder("step2")
                        .dependsOn("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();

        // When
        TxFlow.ValidationResult result = flow.validate();

        // Then
        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    void validate_shouldDetectDuplicateStepIds() {
        // Given
        TxFlow flow = TxFlow.builder("duplicate-flow")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .addStep(FlowStep.builder("step1") // Duplicate!
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();

        // When
        TxFlow.ValidationResult result = flow.validate();

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Duplicate step ID"));
    }

    @Test
    void validate_shouldDetectNonExistentDependency() {
        // Given
        TxFlow flow = TxFlow.builder("missing-dep-flow")
                .addStep(FlowStep.builder("step1")
                        .dependsOn("nonexistent")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();

        // When
        TxFlow.ValidationResult result = flow.validate();

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("non-existent step"));
    }

    @Test
    void validate_shouldDetectForwardDependency() {
        // Given - step1 depends on step2 which comes later
        TxFlow flow = TxFlow.builder("forward-dep-flow")
                .addStep(FlowStep.builder("step1")
                        .dependsOn("step2")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .addStep(FlowStep.builder("step2")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();

        // When
        TxFlow.ValidationResult result = flow.validate();

        // Then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).anyMatch(e -> e.contains("later step"));
    }

    @Test
    void getStep_shouldReturnStep() {
        // Given
        TxFlow flow = TxFlow.builder("get-step-flow")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();

        // When/Then
        assertThat(flow.getStep("step1")).isPresent();
        assertThat(flow.getStep("nonexistent")).isEmpty();
    }

    @Test
    void getStepIds_shouldReturnAllIds() {
        // Given
        TxFlow flow = TxFlow.builder("ids-flow")
                .addStep(FlowStep.builder("step1")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .addStep(FlowStep.builder("step2")
                        .withTxContext(builder -> builder.compose(new Tx().from("addr1")))
                        .build())
                .build();

        // When/Then
        assertThat(flow.getStepIds()).containsExactly("step1", "step2");
    }
}
