package com.bloxbean.cardano.client.txflow;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for FlowStep.
 */
class FlowStepTest {

    @Test
    void builder_shouldCreateStepWithTx() {
        // Given
        Tx tx = new Tx()
                .from("addr1_sender")
                .payToAddress("addr1_receiver", Amount.ada(10));

        // When
        FlowStep step = FlowStep.builder("step1")
                .withDescription("Test step")
                .withTx(tx)
                .build();

        // Then
        assertThat(step.getId()).isEqualTo("step1");
        assertThat(step.getDescription()).isEqualTo("Test step");
        assertThat(step.hasTx()).isTrue();
        assertThat(step.hasTxPlan()).isFalse();
        assertThat(step.hasDependencies()).isFalse();
    }

    @Test
    void builder_shouldCreateStepWithTxPlan() {
        // Given
        TxPlan plan = new TxPlan()
                .addVariable("amount", 100)
                .addTransaction(new Tx().from("addr1"));

        // When
        FlowStep step = FlowStep.builder("step1")
                .withTxPlan(plan)
                .build();

        // Then
        assertThat(step.hasTxPlan()).isTrue();
        assertThat(step.hasTx()).isFalse();
    }

    @Test
    void builder_shouldNotAllowBothTxAndTxPlan() {
        // Given
        Tx tx = new Tx().from("addr1");
        TxPlan plan = new TxPlan().addTransaction(new Tx().from("addr2"));

        // When/Then
        assertThatThrownBy(() ->
                FlowStep.builder("step1")
                        .withTx(tx)
                        .withTxPlan(plan)
                        .build()
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void builder_shouldRequireEitherTxOrTxPlan() {
        // When/Then
        assertThatThrownBy(() ->
                FlowStep.builder("step1").build()
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TxPlan or AbstractTx");
    }

    @Test
    void builder_shouldNotAllowNullStepId() {
        // When/Then
        assertThatThrownBy(() ->
                FlowStep.builder(null)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void builder_shouldNotAllowEmptyStepId() {
        // When/Then
        assertThatThrownBy(() ->
                FlowStep.builder("")
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void builder_shouldAddDependencies() {
        // When
        FlowStep step = FlowStep.builder("step2")
                .withTx(new Tx().from("addr1"))
                .dependsOn("step1")
                .dependsOnIndex("step1", 0)
                .dependsOnChange("step1")
                .build();

        // Then
        assertThat(step.hasDependencies()).isTrue();
        assertThat(step.getDependencies()).hasSize(3);
        assertThat(step.getDependencyStepIds()).containsExactly("step1", "step1", "step1");
    }

    @Test
    void builder_shouldAddDependencyWithStrategy() {
        // When
        FlowStep step = FlowStep.builder("step2")
                .withTx(new Tx().from("addr1"))
                .dependsOn("step1", SelectionStrategy.CHANGE)
                .build();

        // Then
        assertThat(step.getDependencies()).hasSize(1);
        assertThat(step.getDependencies().get(0).getStrategy()).isEqualTo(SelectionStrategy.CHANGE);
    }

    @Test
    void builder_shouldAddCustomDependency() {
        // Given
        StepDependency customDep = StepDependency.builder("step1")
                .withStrategy(SelectionStrategy.INDEX)
                .withUtxoIndex(2)
                .optional()
                .build();

        // When
        FlowStep step = FlowStep.builder("step2")
                .withTx(new Tx().from("addr1"))
                .dependsOn(customDep)
                .build();

        // Then
        assertThat(step.getDependencies()).hasSize(1);
        StepDependency dep = step.getDependencies().get(0);
        assertThat(dep.getStepId()).isEqualTo("step1");
        assertThat(dep.getStrategy()).isEqualTo(SelectionStrategy.INDEX);
        assertThat(dep.getUtxoIndex()).isEqualTo(2);
        assertThat(dep.isOptional()).isTrue();
    }
}
