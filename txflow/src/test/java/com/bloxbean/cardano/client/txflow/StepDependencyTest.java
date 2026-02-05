package com.bloxbean.cardano.client.txflow;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionContext;
import com.bloxbean.cardano.client.txflow.result.FlowStepResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for StepDependency.
 */
class StepDependencyTest {

    private FlowExecutionContext context;
    private List<Utxo> testUtxos;

    @BeforeEach
    void setUp() {
        context = new FlowExecutionContext("test-flow");

        // Create test UTXOs
        Utxo utxo1 = createUtxo("tx1", 0, "addr1", 1_000_000L);
        Utxo utxo2 = createUtxo("tx1", 1, "addr2", 2_000_000L);
        Utxo utxo3 = createUtxo("tx1", 2, "addr3", 3_000_000L);
        testUtxos = Arrays.asList(utxo1, utxo2, utxo3);

        // Record a successful step result
        FlowStepResult result = FlowStepResult.success("step1", "tx1", testUtxos);
        context.recordStepResult("step1", result);
    }

    private Utxo createUtxo(String txHash, int outputIndex, String address, long lovelace) {
        Utxo utxo = new Utxo();
        utxo.setTxHash(txHash);
        utxo.setOutputIndex(outputIndex);
        utxo.setAddress(address);
        utxo.setAmount(Collections.singletonList(Amount.lovelace(BigInteger.valueOf(lovelace))));
        return utxo;
    }

    @Test
    void all_shouldSelectAllUtxos() {
        // Given
        StepDependency dep = StepDependency.all("step1");

        // When
        List<Utxo> selected = dep.resolveUtxos(context);

        // Then
        assertThat(selected).hasSize(3);
    }

    @Test
    void atIndex_shouldSelectSpecificUtxo() {
        // Given
        StepDependency dep = StepDependency.atIndex("step1", 1);

        // When
        List<Utxo> selected = dep.resolveUtxos(context);

        // Then
        assertThat(selected).hasSize(1);
        assertThat(selected.get(0).getOutputIndex()).isEqualTo(1);
    }

    @Test
    void change_shouldSelectLastUtxo() {
        // Given
        StepDependency dep = StepDependency.change("step1");

        // When
        List<Utxo> selected = dep.resolveUtxos(context);

        // Then
        assertThat(selected).hasSize(1);
        assertThat(selected.get(0).getOutputIndex()).isEqualTo(2); // Last output
    }

    @Test
    void filter_withPredicate_shouldFilterUtxos() {
        // Given
        StepDependency dep = StepDependency.filter("step1",
                utxo -> utxo.getAddress().equals("addr2"));

        // When
        List<Utxo> selected = dep.resolveUtxos(context);

        // Then
        assertThat(selected).hasSize(1);
        assertThat(selected.get(0).getAddress()).isEqualTo("addr2");
    }

    @Test
    void resolveUtxos_shouldThrowForMissingRequiredStep() {
        // Given
        StepDependency dep = StepDependency.all("nonexistent");

        // When/Then
        assertThatThrownBy(() -> dep.resolveUtxos(context))
                .isInstanceOf(FlowDependencyException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    void resolveUtxos_shouldReturnEmptyForMissingOptionalStep() {
        // Given
        StepDependency dep = StepDependency.builder("nonexistent")
                .withStrategy(SelectionStrategy.ALL)
                .optional()
                .build();

        // When
        List<Utxo> selected = dep.resolveUtxos(context);

        // Then
        assertThat(selected).isEmpty();
    }

    @Test
    void atIndex_shouldThrowForOutOfBoundsOnRequired() {
        // Given
        StepDependency dep = StepDependency.atIndex("step1", 10);

        // When/Then
        assertThatThrownBy(() -> dep.resolveUtxos(context))
                .isInstanceOf(FlowDependencyException.class)
                .hasMessageContaining("out of bounds");
    }

    @Test
    void atIndex_shouldReturnEmptyForOutOfBoundsOnOptional() {
        // Given
        StepDependency dep = StepDependency.builder("step1")
                .withStrategy(SelectionStrategy.INDEX)
                .withUtxoIndex(10)
                .optional()
                .build();

        // When
        List<Utxo> selected = dep.resolveUtxos(context);

        // Then
        assertThat(selected).isEmpty();
    }

    @Test
    void builder_shouldRequireUtxoIndexForIndexStrategy() {
        // When/Then
        assertThatThrownBy(() ->
                StepDependency.builder("step1")
                        .withStrategy(SelectionStrategy.INDEX)
                        .build()
        ).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("utxoIndex");
    }

    @Test
    void builder_shouldRequireStepId() {
        // When/Then
        assertThatThrownBy(() ->
                StepDependency.builder(null)
        ).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() ->
                StepDependency.builder("")
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getters_shouldReturnCorrectValues() {
        // Given
        StepDependency dep = StepDependency.builder("step1")
                .withStrategy(SelectionStrategy.INDEX)
                .withUtxoIndex(5)
                .optional()
                .build();

        // Then
        assertThat(dep.getStepId()).isEqualTo("step1");
        assertThat(dep.getStrategy()).isEqualTo(SelectionStrategy.INDEX);
        assertThat(dep.getUtxoIndex()).isEqualTo(5);
        assertThat(dep.isOptional()).isTrue();
    }
}
