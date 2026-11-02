package com.bloxbean.cardano.client.programmabletoken;

import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.ChainDataSupplier;
import com.bloxbean.cardano.client.api.ProtocolParamsSupplier;
import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113Deployments;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProgrammableTokenService;
import com.bloxbean.cardano.client.programmabletoken.cip113.Cip113ProtocolService;
import com.bloxbean.cardano.client.programmabletoken.intent.ProgrammableUnfrackIntent;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.intent.PlutusDataValue;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.codec.FlowParseOptions;
import com.bloxbean.cardano.client.txflow.codec.TxFlowCodec;
import com.bloxbean.cardano.client.txflow.compile.FlowCompilationRequest;
import com.bloxbean.cardano.client.txflow.compile.TxFlowCompiler;
import com.bloxbean.cardano.client.txflow.exec.FlowEngine;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionRequest;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.model.FlowBindings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UnfrackingUnsupportedTest {
    private static final String OWNER = AddressProvider.getEntAddress(
            Credential.fromKey("22".repeat(28)), Networks.testnet()).toBech32();
    private static final String POLICY = "44".repeat(28);
    private static final String MESSAGE = "Unfracking is temporarily unsupported in this release";

    private final UtxoSupplier utxos = mock(UtxoSupplier.class);
    private final ProtocolParamsSupplier params = mock(ProtocolParamsSupplier.class);
    private final TransactionProcessor processor = mock(TransactionProcessor.class);
    private final ChainDataSupplier chain = mock(ChainDataSupplier.class);

    private Cip113ProgrammableTokenService tokens() {
        Cip113ProtocolService service = mock(Cip113ProtocolService.class);
        // A custom deployment's flag cannot opt back into the temporarily unsupported operation.
        when(service.deployment()).thenReturn(Cip113Deployments.PREVIEW.toBuilder()
                .unfrackingEnabled(true).build());
        return new Cip113ProgrammableTokenService(service);
    }

    private Tx rawUnfrack() {
        Tx tx = new Tx().from(OWNER);
        tx.addIntention(ProgrammableUnfrackIntent.builder().policyId(POLICY)
                .authorization(PlutusDataValue.of(PlutusData.unit())).build());
        return tx;
    }

    @Test
    void facadeThrowsWithoutChangingPreviouslyAuthoredIntents() {
        ProgrammableTokenTx tx = new ProgrammableTokenTx().from(OWNER)
                .transfer(OWNER, Amount.asset(POLICY + "ab", 1), PlutusData.unit());
        var before = List.copyOf(tx.getIntentions());

        assertThatThrownBy(() -> tx.unfrack(POLICY, PlutusData.unit()))
                .isInstanceOf(UnsupportedOperationException.class).hasMessage(MESSAGE);
        assertThat(tx.getIntentions()).containsExactlyElementsOf(before);
    }

    @Test
    void manuallyAddedIntentFailsBeforeQuickTxBackendAccess() {
        var tokens = tokens();
        assertThat(tokens.capabilities()).doesNotContain(ProgrammableTokenCapability.UNFRACK);
        assertThatThrownBy(() -> new QuickTxBuilder(utxos, params, processor)
                .withExtension(tokens.extension()).compose(rawUnfrack()).build())
                .isInstanceOf(UnsupportedOperationException.class).hasMessage(MESSAGE);
        verifyNoInteractions(utxos, params, processor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pt", "tokens"})
    void savedTxPlanYamlFailsWithAnExplicitUnsupportedException(String namespace) throws IOException {
        var tokens = tokens();
        String yaml = resource("/txplan/cip113/07-unfrack.yml")
                .replace("  pt:", "  " + namespace + ":")
                .replace("type: pt:unfrack", "type: " + namespace + ":unfrack");
        assertThatThrownBy(() -> tokens.txPlanCodec(namespace).fromYaml(yaml, Map.of(
                "owner_address", OWNER, "policy_id", POLICY,
                "bootstrap_tx", Cip113Deployments.PREVIEW.getBootstrapTxHash())))
                .isInstanceOf(UnsupportedOperationException.class).hasMessage(MESSAGE);
    }

    @Test
    void unsupportedPlansCannotBeExportedForLaterExecution() {
        var tokens = tokens();
        var plan = tokens.extension().configure(TxPlan.from(rawUnfrack()));
        assertThatThrownBy(() -> tokens.txPlanCodec().toYaml(plan))
                .isInstanceOf(UnsupportedOperationException.class).hasMessage(MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aLaterUnfrackFailsTheWholeFlowBeforeTheFirstStepRuns(boolean portableYaml) {
        var tokens = tokens();
        TxFlow flow = portableYaml
                ? TxFlowCodec.standard().parse(portableFlow(), FlowParseOptions.serverDefaults()).requireFlow()
                : TxFlow.builder("unsupported-unfrack")
                        .addStep(FlowStep.builder("pay").withTxPlan(TxPlan.from(new Tx().from(OWNER)
                                .payToAddress(OWNER, Amount.ada(2)))).build())
                        .addStep(FlowStep.builder("unfrack").needs("pay")
                                .withTxPlan(tokens.extension().configure(TxPlan.from(rawUnfrack()))).build())
                        .build();
        FlowEngine engine = FlowEngine.builder(utxos, params, processor, chain)
                .executor(Runnable::run).extension(tokens.extension()).build();

        var preflight = engine.preflight(FlowCompilationRequest.of(flow, FlowBindings.empty()));
        assertThat(preflight.hasErrors()).isTrue();
        assertThat(preflight.getDiagnostics()).anySatisfy(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("TXFLOW_EXTENSION_INTENT_INVALID");
            assertThat(diagnostic.message()).contains(MESSAGE);
            assertThat(diagnostic.documentPath()).contains("steps[1]");
        });
        var result = engine.start(FlowExecutionRequest.builder(flow)
                .executionId("unsupported-unfrack-" + portableYaml).build()).await();
        assertThat(result.state()).isEqualTo(FlowExecutionState.FAILED);
        verifyNoInteractions(utxos, params, processor, chain);
    }

    @Test
    void supportedPortableLifecycleStillCompiles() throws IOException {
        var tokens = tokens();
        var flow = TxFlowCodec.standard().parse(resource("/txflow/cip113-lifecycle.yaml"),
                FlowParseOptions.serverDefaults()).requireFlow();
        var bindings = FlowBindings.builder().put("owner_address", OWNER)
                .put("logic_script_hash", "33".repeat(28)).put("policy_id", POLICY)
                .put("token_unit", POLICY + "ab").build();
        var compiled = new TxFlowCompiler(List.of(tokens.extension()))
                .compile(FlowCompilationRequest.of(flow, bindings)).requireCompiledFlow();
        assertThat(compiled.getExecutionPlan().getSteps()).hasSize(3);
    }

    private static String portableFlow() {
        return """
                api_version: txflow.cardano-client.dev/v1alpha1
                kind: TxFlow
                metadata: {name: unsupported-unfrack}
                spec:
                  extensions:
                    pt:
                      extension: programmable-token
                      schema_version: '1'
                      protocol: cip-113
                      contract_version: 0.0.1
                  steps:
                    - id: pay
                      transaction:
                        tx:
                          from: OWNER
                          intents:
                            - type: payment
                              address: OWNER
                              amounts: [{unit: lovelace, quantity: 2000000}]
                    - id: unfrack
                      needs: [pay]
                      transaction:
                        tx:
                          from: OWNER
                          intents:
                            - type: pt:unfrack
                              policy_id: POLICY
                              authorization: {constructor: 0, fields: []}
                """.replace("OWNER", OWNER).replace("POLICY", POLICY);
    }

    private static String resource(String path) throws IOException {
        try (var input = UnfrackingUnsupportedTest.class.getResourceAsStream(path)) {
            assertThat(input).as("resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
