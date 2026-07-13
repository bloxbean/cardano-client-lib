package com.bloxbean.cardano.client.txflow.compile;

import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.codec.FlowParseOptions;
import com.bloxbean.cardano.client.txflow.codec.TxFlowCodec;
import com.bloxbean.cardano.client.txflow.model.FlowBindings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TxFlowCompilerTest {
    private TxFlow definition;
    private final TxFlowCompiler compiler = new TxFlowCompiler();

    @BeforeEach
    void setUp() {
        String yaml = """
                api_version: txflow.cardano-client.dev/v1alpha1
                kind: TxFlow
                metadata: {name: typed-payment}
                spec:
                  parameters:
                    beneficiary: {type: address, required: true}
                    amount: {type: integer, minimum: 1000000, maximum: 10000000}
                  steps:
                    - id: prepare
                      outputs:
                        staging:
                          select: {output_index: 0}
                          expect: exactly_one
                      transaction:
                        tx:
                          from_ref: account://sender
                          intents:
                            - type: payment
                              address: '${{ inputs.beneficiary }}'
                              amounts:
                                - unit: lovelace
                                  quantity: '${{ inputs.amount }}'
                    - id: audit
                      needs: [prepare]
                      transaction:
                        tx:
                          inputs:
                            - type: collect_from
                              refs:
                                - flow_output: {step: prepare, output: staging}
                          intents: []
                """;
        definition = TxFlowCodec.standard().parse(yaml, FlowParseOptions.serverDefaults()).requireFlow();
    }

    @Test
    void bindsNativeValuesWithoutMutatingDefinitionAndProducesStableFingerprint() {
        FlowBindings bindings = FlowBindings.builder()
                .put("beneficiary", "addr_test1qpz")
                .put("amount", 5_000_000L)
                .build();

        CompiledTxFlow first = compiler.compile(FlowCompilationRequest.of(definition, bindings))
                .requireCompiledFlow();
        CompiledTxFlow second = compiler.compile(FlowCompilationRequest.of(definition, bindings))
                .requireCompiledFlow();

        String compiledYaml = first.getExecutionPlan().getSteps().get(0).getTxPlan().toYaml();
        assertTrue(compiledYaml.contains("5000000"));
        assertFalse(compiledYaml.contains("${{"));
        assertTrue(definition.getSteps().get(0).hasTransactionTemplate());
        assertEquals(first.getFingerprint(), second.getFingerprint());
        assertTrue(first.getExecutionPlan().getSteps().get(1).getDependencies().isEmpty());
        assertEquals(1, first.getExecutionPlan().getSteps().get(1).getNeeds().size());
        assertEquals(1, first.getExecutionPlan().getSteps().get(0).getOutputBindings().size());
        assertEquals(java.util.Set.of("audit"), first.getExplicitConsumers().get("prepare"));
    }

    @Test
    void rejectsMissingWrongAndOutOfRangeBindings() {
        FlowCompilationResult missing = compiler.compile(FlowCompilationRequest.of(
                definition, FlowBindings.empty()));
        assertTrue(missing.hasErrors());

        FlowCompilationResult wrong = compiler.compile(FlowCompilationRequest.of(definition,
                FlowBindings.builder().put("beneficiary", "addr").put("amount", "five").build()));
        assertTrue(wrong.hasErrors());

        FlowCompilationResult range = compiler.compile(FlowCompilationRequest.of(definition,
                FlowBindings.builder().put("beneficiary", "addr").put("amount", 99L).build()));
        assertTrue(range.hasErrors());
    }

    @Test
    void fingerprintChangesWhenCanonicalBindingsChange() {
        CompiledTxFlow first = compileAt(2_000_000L);
        CompiledTxFlow second = compileAt(3_000_000L);
        assertNotEquals(first.getFingerprint(), second.getFingerprint());
    }

    @Test
    void namedOutputSelectorEnforcesCardinalityAndMissingReferencesFailCompile() {
        com.bloxbean.cardano.client.txflow.model.FlowOutputSelector selector =
                com.bloxbean.cardano.client.txflow.model.FlowOutputSelector.atIndex(1);
        assertThrows(IllegalStateException.class, () -> selector.select(java.util.List.of(
                com.bloxbean.cardano.client.api.model.Utxo.builder().outputIndex(0).build())));

        String encoded = TxFlowCodec.standard().write(definition,
                com.bloxbean.cardano.client.txflow.codec.FlowWriteOptions.of(
                        com.bloxbean.cardano.client.txflow.codec.FlowFormat.YAML,
                        com.bloxbean.cardano.client.txflow.codec.FlowSchemaVersion.V1ALPHA1));
        TxFlow broken = TxFlowCodec.standard().parse(encoded.replace(
                        "output: staging", "output: missing"), FlowParseOptions.serverDefaults())
                .requireFlow();
        FlowCompilationResult result = compiler.compile(FlowCompilationRequest.of(broken,
                FlowBindings.builder().put("beneficiary", "addr")
                        .put("amount", 2_000_000L).build()));
        assertTrue(result.getDiagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().equals("TXFLOW_FLOW_OUTPUT_BINDING_MISSING")));
    }

    @Test
    void portableYamlAndJavaModelCompileToTheSameFingerprint() {
        FlowBindings bindings = FlowBindings.builder().put("beneficiary", "addr_test1qpz")
                .put("amount", 2_000_000L).build();
        String yaml = TxFlowCodec.standard().write(definition,
                com.bloxbean.cardano.client.txflow.codec.FlowWriteOptions.of(
                        com.bloxbean.cardano.client.txflow.codec.FlowFormat.YAML,
                        com.bloxbean.cardano.client.txflow.codec.FlowSchemaVersion.V1ALPHA1));
        TxFlow decoded = TxFlowCodec.standard().parse(
                yaml, FlowParseOptions.serverDefaults()).requireFlow();
        assertEquals(compiler.compile(FlowCompilationRequest.of(definition, bindings))
                        .requireCompiledFlow().getFingerprint(),
                compiler.compile(FlowCompilationRequest.of(decoded, bindings))
                        .requireCompiledFlow().getFingerprint());
    }

    private CompiledTxFlow compileAt(long amount) {
        return compiler.compile(FlowCompilationRequest.of(definition, FlowBindings.builder()
                .put("beneficiary", "addr_test1qpz")
                .put("amount", amount)
                .build())).requireCompiledFlow();
    }
}
