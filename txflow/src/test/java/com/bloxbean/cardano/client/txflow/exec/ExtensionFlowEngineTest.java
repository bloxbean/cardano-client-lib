package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionMetadata;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.ChainingMode;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.codec.FlowDiagnostic;
import com.bloxbean.cardano.client.txflow.compile.CompiledTxFlow;
import com.bloxbean.cardano.client.txflow.compile.FlowCompilationRequest;
import com.bloxbean.cardano.client.txflow.compile.FlowCompilationResult;
import com.bloxbean.cardano.client.txflow.config.ConfirmationConfig;
import com.bloxbean.cardano.client.txflow.config.FlowExecutionSettings;
import com.bloxbean.cardano.client.txflow.model.FlowBindings;
import com.bloxbean.cardano.client.txflow.testsupport.FakeChain;
import com.bloxbean.cardano.client.txflow.testsupport.FakeExtension;
import com.bloxbean.cardano.client.txflow.testsupport.FakeExtension.PayIntent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.bloxbean.cardano.client.txflow.testsupport.FakeChain.RECEIVER;
import static com.bloxbean.cardano.client.txflow.testsupport.FakeChain.SENDER;
import static com.bloxbean.cardano.client.txflow.testsupport.FakeExtension.pay;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR 0008 M1 on the portable path: FlowEngine compiles, fingerprints and runs Java extension
 * steps (review finding F1), with the I1, I8, I11 and I13 rules.
 */
class ExtensionFlowEngineTest {
    private final FakeChain chain = new FakeChain();
    private final FakeExtension extension = new FakeExtension();

    private FlowEngine engine(boolean registered) {
        FlowEngine.Builder builder = FlowEngine.builder(chain.utxoSupplier, chain.protocolParamsSupplier,
                        chain.transactionProcessor, chain.chainDataSupplier)
                .executor(Runnable::run)
                .signerRegistry(chain.signers);
        if (registered) builder.extension(extension);
        return builder.build();
    }

    private TxFlow flow(TxPlan extensionPlan) {
        return TxFlow.builder("engine-extension")
                .withExecutionSettings(FlowExecutionSettings.builder()
                        .chainingMode(ChainingMode.PIPELINED)
                        .confirmationConfig(ConfirmationConfig.builder().minConfirmations(1)
                                .checkInterval(Duration.ofMillis(10)).timeout(Duration.ofSeconds(5)).build())
                        .build())
                .addStep(FlowStep.builder("core").withTxPlan(TxPlan.from(new Tx().from(SENDER)
                        .payToAddress(RECEIVER, Amount.ada(2))).withSigner(FakeChain.SIGNER)).build())
                .addStep(FlowStep.builder("extension").needs("core").fundsFrom("core")
                        .withTxPlan(extensionPlan).build())
                .build();
    }

    private FlowCompilationResult preflight(FlowEngine engine, TxFlow flow) {
        return engine.preflight(FlowCompilationRequest.of(flow, FlowBindings.empty()));
    }

    @Test
    void theEngineCompilesFingerprintsAndRunsAJavaExtensionFlow() {
        TxFlow flow = flow(extension.plan(SENDER, pay(RECEIVER, 3_000_000)));
        FlowEngine engine = engine(true);

        CompiledTxFlow compiled = preflight(engine, flow).requireCompiledFlow();
        FlowExecutionResult result = engine.start(FlowExecutionRequest.builder(flow)
                .executionId("run-1").build()).await();

        assertThat(result.state()).as(String.valueOf(result)).isEqualTo(FlowExecutionState.COMPLETED);
        assertThat(result.definitionFingerprint()).isEqualTo(compiled.getFingerprint());
        assertThat(chain.submitted).hasSize(2);
    }

    /** I1 on the engine: no compilation, no submission without the registration. */
    @Test
    void anEngineWithoutTheExtensionRejectsTheFlowBeforeSubmission() {
        TxFlow flow = flow(extension.plan(SENDER, pay(RECEIVER, 3_000_000)));
        FlowEngine engine = engine(false);

        assertThat(codes(preflight(engine, flow))).containsExactly("TXFLOW_EXTENSION_UNREGISTERED");
        FlowExecutionResult result = engine.start(FlowExecutionRequest.builder(flow)
                .executionId("run-2").build()).await();
        assertThat(result.state()).isNotEqualTo(FlowExecutionState.COMPLETED);
        assertThat(chain.submitted).isEmpty();
    }

    /** I8: the fingerprint covers the binding and the intent; deployment key order does not matter. */
    @Test
    void theFingerprintCoversBindingsAndIntentFields() {
        FlowEngine engine = engine(true);
        String base = fingerprint(engine, metadata(Map.of("network", "0", "label", "a")), 3_000_000);

        assertThat(fingerprint(engine, metadata(Map.of("network", "0", "label", "b")), 3_000_000))
                .isNotEqualTo(base);
        assertThat(fingerprint(engine, metadata(Map.of("network", "0", "label", "a")), 4_000_000))
                .isNotEqualTo(base);
        Map<String, Object> reordered = new LinkedHashMap<>();
        reordered.put("label", "a");
        reordered.put("network", "0");
        assertThat(fingerprint(engine, metadata(reordered), 3_000_000)).isEqualTo(base);
    }

    /**
     * I11 (portable path): an already-bound plan that still carries variables is not portable and
     * is reported as such, not as an extension problem; once cleared it compiles and its literal
     * value survives into the build.
     */
    @Test
    void retainedVariablesAreRejectedOnThePortablePathAndTheLiteralSurvivesOnceCleared() {
        TxPlan bound = extension.plan(SENDER, pay(RECEIVER, 3_000_000).note("literal ${other} stays"))
                .setVariables(Map.of("other", "REINTERPRETED"));
        FlowEngine engine = engine(true);

        List<String> rejected = codes(preflight(engine, flow(bound)));
        assertThat(rejected).containsExactly("TXFLOW_NON_PORTABLE_TXPLAN_VARIABLES");

        bound.setVariables(Map.of());
        CompiledTxFlow compiled = preflight(engine, flow(bound)).requireCompiledFlow();
        PayIntent compiledIntent = (PayIntent) compiled.getExecutionPlan().getSteps().get(1)
                .getTxPlan().getTxs().get(0).getIntentions().get(0);
        assertThat(compiledIntent.getNote()).isEqualTo("literal ${other} stays");

        FlowExecutionResult result = engine.start(FlowExecutionRequest.builder(flow(bound))
                .executionId("run-3").build()).await();
        assertThat(result.state()).as(String.valueOf(result)).isEqualTo(FlowExecutionState.COMPLETED);
        assertThat(extension.notes).containsExactly("literal ${other} stays");
    }

    /** I13: an unused binding is rejected whether or not its extension is registered. */
    @Test
    void unusedBindingsAreRejectedRegisteredOrNot() {
        ExtensionMetadata unregistered = ExtensionMetadata.builder().extension("other").schemaVersion("1").build();
        TxPlan usedPlusUnused = extension.plan(SENDER, pay(RECEIVER, 3_000_000)).withExtension("ot", unregistered);
        TxPlan registeredButUnused = TxPlan.from(new Tx().from(SENDER).payToAddress(RECEIVER, Amount.ada(2)))
                .withExtension(FakeExtension.NAMESPACE, extension.metadata()).withSigner(FakeChain.SIGNER);

        assertThat(preflight(engine(true), flow(usedPlusUnused)).getDiagnostics())
                .extracting(FlowDiagnostic::code, FlowDiagnostic::documentPath)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        "TXFLOW_EXTENSION_BINDING_UNUSED", "$.spec.steps[1].transaction"));
        assertThat(codes(preflight(engine(true), flow(registeredButUnused))))
                .containsExactly("TXFLOW_EXTENSION_BINDING_UNUSED");
        assertThat(chain.submitted).isEmpty();
    }

    private String fingerprint(FlowEngine engine, ExtensionMetadata metadata, long lovelace) {
        TxPlan plan = TxPlan.from(new Tx().from(SENDER))
                .withExtension(FakeExtension.NAMESPACE, metadata).withSigner(FakeChain.SIGNER);
        plan.getTxs().get(0).addIntention(pay(RECEIVER, lovelace));
        return preflight(engine, flow(plan)).requireCompiledFlow().getFingerprint();
    }

    private ExtensionMetadata metadata(Map<String, Object> deployment) {
        return extension.metadata().toBuilder().deployment(deployment).build();
    }

    private static List<String> codes(FlowCompilationResult result) {
        return result.getDiagnostics().stream().map(FlowDiagnostic::code).toList();
    }
}
