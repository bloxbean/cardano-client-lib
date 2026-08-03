package com.bloxbean.cardano.client.txflow.yaml;

import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.SelectionStrategy;
import com.bloxbean.cardano.client.txflow.StepDependency;
import com.bloxbean.cardano.client.txflow.TxFlow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlowYamlSafetyTest {

    @Test
    void rejectsJavaFactoryInsteadOfWritingAnEmptyStep() {
        TxFlow flow = TxFlow.builder("factory")
                .addStep(FlowStep.builder("one").withTxContext(builder -> null).build())
                .build();

        assertThrows(IllegalStateException.class, flow::toYaml);
    }

    @Test
    void rejectsMultiTransactionPlanInsteadOfDiscardingEntries() {
        TxPlan plan = new TxPlan().addTransaction(new Tx()).addTransaction(new Tx());
        TxFlow flow = TxFlow.builder("multi")
                .addStep(FlowStep.builder("one").withTxPlan(plan).build())
                .build();

        assertThrows(IllegalStateException.class, flow::toYaml);
    }

    @Test
    void rejectsPredicateFilterInsteadOfDegradingToMatchAll() {
        TxPlan plan = new TxPlan().addTransaction(new Tx());
        TxFlow flow = TxFlow.builder("filter")
                .addStep(FlowStep.builder("source").withTxPlan(plan).build())
                .addStep(FlowStep.builder("consumer")
                        .withTxPlan(new TxPlan().addTransaction(new Tx()))
                        .dependsOn(StepDependency.filter("source", utxo -> true))
                        .build())
                .build();

        assertThrows(IllegalStateException.class, flow::toYaml);
    }

    @Test
    void rejectsDuplicateKeysAndMultipleDocuments() {
        String duplicate = """
                version: '1.0'
                flow:
                  id: first
                  id: second
                  steps: []
                """;
        String multiple = """
                version: '1.0'
                flow: {id: first, steps: []}
                ---
                version: '1.0'
                flow: {id: second, steps: []}
                """;

        assertThrows(RuntimeException.class, () -> TxFlow.fromYaml(duplicate));
        assertThrows(RuntimeException.class, () -> TxFlow.fromYaml(multiple));
    }

    @Test
    void standardDecodeValidatesLegacyVersion() {
        String yaml = """
                version: '2.0'
                flow: {id: unsupported, steps: []}
                """;

        assertThrows(RuntimeException.class, () -> TxFlow.fromYaml(yaml));
    }

    @Test
    void versionlessLegacyFilterFieldRemainsReadable() {
        String yaml = """
                flow:
                  id: unsafe-filter
                  steps:
                    - step:
                        id: source
                        tx: {intents: []}
                    - step:
                        id: consumer
                        depends_on:
                          - from_step: source
                            strategy: filter
                            filter: anything
                        tx: {intents: []}
                """;
        TxFlow parsed = TxFlow.fromYaml(yaml);

        assertEquals("unsafe-filter", parsed.getId());
        assertEquals(SelectionStrategy.FILTER,
                parsed.getSteps().get(1).getDependencies().get(0).getStrategy());
    }
}
