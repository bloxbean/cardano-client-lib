package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionIntent;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionMetadata;
import com.bloxbean.cardano.client.quicktx.extension.QuickTxExtension;
import com.bloxbean.cardano.client.quicktx.extension.TxBuildExtension;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionOutput;
import com.bloxbean.cardano.client.txflow.ChainingMode;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.config.ConfirmationConfig;
import com.bloxbean.cardano.client.txflow.internal.FlowExtensions;
import com.bloxbean.cardano.client.txflow.result.FlowResult;
import com.bloxbean.cardano.client.txflow.result.FlowStatus;
import com.bloxbean.cardano.client.txflow.testsupport.FakeChain;
import com.bloxbean.cardano.client.txflow.testsupport.FakeExtension;
import com.bloxbean.cardano.client.txflow.testsupport.FakeExtension.PayIntent;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.bloxbean.cardano.client.txflow.testsupport.FakeChain.PROTOCOL;
import static com.bloxbean.cardano.client.txflow.testsupport.FakeChain.RECEIVER;
import static com.bloxbean.cardano.client.txflow.testsupport.FakeChain.SENDER;
import static com.bloxbean.cardano.client.txflow.testsupport.FakeExtension.pay;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADR 0008 M1 on the FlowExecutor path, with a real QuickTx build and a test-only extension that
 * TxFlow knows nothing about.
 */
class ExtensionFlowExecutorTest {
    private final FakeChain chain = new FakeChain();
    private final FakeExtension extension = new FakeExtension();
    private final List<Transaction> built = new CopyOnWriteArrayList<>();

    private FlowExecutor executor(ChainingMode mode) {
        return FlowExecutor.create(chain.utxoSupplier, chain.protocolParamsSupplier,
                        chain.transactionProcessor, chain.chainDataSupplier)
                .withExecutor(Runnable::run)
                .withChainingMode(mode)
                .withConfirmationConfig(ConfirmationConfig.builder().minConfirmations(1)
                        .checkInterval(Duration.ofMillis(10)).timeout(Duration.ofSeconds(5)).build())
                .withSignerRegistry(chain.signers)
                .withTxInspector(built::add);
    }

    private static TxFlow oneStep(TxPlan plan) {
        return TxFlow.builder("extension-flow")
                .addStep(FlowStep.builder("pay").withTxPlan(plan).build())
                .build();
    }

    /** I5: the builder for an extension plan carries the registered extension. */
    @Test
    void aRegisteredExtensionStepBuildsAndSubmits() {
        FlowResult result = executor(ChainingMode.PIPELINED).withExtension(extension)
                .executeSync(oneStep(extension.plan(SENDER, pay(RECEIVER, 3_000_000))));

        assertThat(result.getStatus()).as(String.valueOf(result.getError())).isEqualTo(FlowStatus.COMPLETED);
        assertThat(chain.submitted).hasSize(1);
        assertThat(paymentsTo(built.get(0), RECEIVER)).containsExactly(BigInteger.valueOf(3_000_000));
    }

    /** I1: nothing is built or submitted when a step needs an unregistered extension. */
    @Test
    void anUnregisteredExtensionFailsBeforeAnyStepIsSubmitted() {
        TxFlow flow = TxFlow.builder("unregistered")
                .addStep(FlowStep.builder("core").withTxPlan(TxPlan.from(new Tx().from(SENDER)
                        .payToAddress(RECEIVER, Amount.ada(2))).withSigner(FakeChain.SIGNER)).build())
                .addStep(FlowStep.builder("extension")
                        .withTxPlan(extension.plan(SENDER, pay(RECEIVER, 3_000_000))).build())
                .build();

        assertThatThrownBy(() -> executor(ChainingMode.PIPELINED).executeSync(flow))
                .isInstanceOf(FlowExecutionException.class)
                .hasMessageContaining("TXFLOW_EXTENSION_UNREGISTERED");
        assertThatThrownBy(() -> executor(ChainingMode.SEQUENTIAL).withExecutor(Runnable::run).execute(flow))
                .isInstanceOf(FlowExecutionException.class)
                .hasMessageContaining("TXFLOW_EXTENSION_UNREGISTERED");
        assertThat(chain.submitted).isEmpty();
        assertThat(built).isEmpty();
    }

    /** I6: a binding that does not match the operator-configured extension is rejected. */
    @Test
    void aBindingTheRegisteredExtensionRejectsFailsBeforeSubmission() {
        TxFlow flow = oneStep(extension.plan(SENDER, pay(RECEIVER, 3_000_000)));

        assertThatThrownBy(() -> executor(ChainingMode.PIPELINED)
                .withExtension(new FakeExtension("1")).executeSync(flow))
                .hasMessageContaining("TXFLOW_EXTENSION_METADATA_INVALID")
                .hasMessageContaining("does not match configured network 1");
        assertThat(chain.submitted).isEmpty();
    }

    /**
     * I6: the schema version is checked in preflight even when the extension's validateMetadata
     * override checks only its deployment, as the codec checks it at copy time.
     */
    @Test
    void anUnsupportedSchemaVersionFailsBeforeAnyStepIsBuilt() {
        QuickTxExtension deploymentOnly = new QuickTxExtension() {
            @Override public String id() { return extension.id(); }
            @Override public String schemaVersion() { return extension.schemaVersion(); }
            @Override public Set<String> operations() { return extension.operations(); }
            @Override public Map<String, Class<? extends ExtensionIntent>> intentTypes() { return extension.intentTypes(); }
            @Override public TxBuildExtension newBuildExtension() { return extension.newBuildExtension(); }
            @Override public void validateMetadata(ExtensionMetadata metadata) { }
        };
        TxPlan schemaTwo = TxPlan.from(new Tx().from(SENDER)).withSigner(FakeChain.SIGNER)
                .withExtension(FakeExtension.NAMESPACE, extension.metadata().toBuilder().schemaVersion("2").build());
        schemaTwo.getTxs().get(0).addIntention(pay(RECEIVER, 3_000_000));
        TxFlow flow = TxFlow.builder("schema-two")
                .addStep(FlowStep.builder("core").withTxPlan(TxPlan.from(new Tx().from(SENDER)
                        .payToAddress(RECEIVER, Amount.ada(2))).withSigner(FakeChain.SIGNER)).build())
                .addStep(FlowStep.builder("extension").withTxPlan(schemaTwo).build())
                .build();

        assertThatThrownBy(() -> executor(ChainingMode.PIPELINED).withExtension(deploymentOnly).executeSync(flow))
                .hasMessageContaining("TXFLOW_EXTENSION_METADATA_INVALID")
                .hasMessageContaining("unsupported schema_version '2'");
        assertThat(built).isEmpty();
        assertThat(chain.submitted).isEmpty();
    }

    @Test
    void anInvalidExtensionIntentFailsBeforeSubmission() {
        TxFlow flow = oneStep(extension.plan(SENDER, pay(RECEIVER, 0)));

        assertThatThrownBy(() -> executor(ChainingMode.PIPELINED).withExtension(extension).executeSync(flow))
                .hasMessageContaining("TXFLOW_EXTENSION_INTENT_INVALID")
                .hasMessageContaining("lovelace must be positive");
        assertThat(chain.submitted).isEmpty();
    }

    @Test
    void twoInstancesWithOneIdCannotBothBeRegistered() {
        FlowExecutor executor = executor(ChainingMode.PIPELINED).withExtension(extension).withExtension(extension);

        assertThatThrownBy(() -> executor.withExtension(new FakeExtension()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Extension already registered: fake");
    }

    /**
     * I10: under PIPELINED, an address scan through the build supplier finds an earlier step's
     * pending output only along a funding edge; {@code needs} alone orders but does not expose it.
     */
    @Test
    void pendingProtocolOutputsAreVisibleToAnAddressScanOnlyWithAFundingEdge() {
        FlowResult withoutEdge = executor(ChainingMode.PIPELINED).withExtension(extension)
                .executeSync(protocolFlow(false));
        assertThat(withoutEdge.getStatus()).isEqualTo(FlowStatus.FAILED);
        assertThat(String.valueOf(withoutEdge.getError())).contains("fake protocol state not found");
        assertThat(extension.scans).containsExactly(List.of());

        extension.scans.clear();
        chain.submitted.clear();
        FlowResult withEdge = executor(ChainingMode.PIPELINED).withExtension(extension)
                .executeSync(protocolFlow(true));

        assertThat(withEdge.getStatus()).as(String.valueOf(withEdge.getError())).isEqualTo(FlowStatus.COMPLETED);
        assertThat(extension.scans).singleElement().satisfies(seen -> assertThat(seen)
                .extracting(Utxo::getTxHash).containsExactly(chain.submitted.get(0)));
    }

    /** I12: the funding edge exposes pending outputs to address scans, never to exact lookups. */
    @Test
    void exactLookupsOfPendingOutputsAreNotWidenedByAFundingEdge() {
        PayIntent intent = pay(RECEIVER, 3_000_000).scan(PROTOCOL, true);
        intent.setLookup(FakeExtension.SCANNED);
        TxFlow flow = TxFlow.builder("exact-lookup")
                .addStep(FlowStep.builder("register").withTxPlan(TxPlan.from(new Tx().from(SENDER)
                        .payToAddress(PROTOCOL, Amount.ada(2))).withSigner(FakeChain.SIGNER)).build())
                .addStep(FlowStep.builder("use").needs("register").fundsFrom("register")
                        .withTxPlan(extension.plan(SENDER, intent)).build())
                .build();

        FlowResult result = executor(ChainingMode.PIPELINED).withExtension(extension).executeSync(flow);

        assertThat(result.getStatus()).as(String.valueOf(result.getError())).isEqualTo(FlowStatus.COMPLETED);
        assertThat(extension.scans.get(0)).hasSize(1);
        assertThat(extension.lookups).containsExactly(Optional.empty());
    }

    /**
     * The evaluator factory gets a view that resolves, by exact lookup, the funding steps' pending
     * outputs the built transaction references; the build itself still cannot (I12 above).
     */
    @Test
    void theEvaluatorCanResolveFundingStepOutputsExactly() {
        List<com.bloxbean.cardano.client.api.UtxoSupplier> evaluatorViews = new CopyOnWriteArrayList<>();
        FlowResult result = executor(ChainingMode.PIPELINED).withExtension(extension)
                .withTxEvaluator((utxos, params) -> {
                    evaluatorViews.add(utxos);
                    return null; // the fake intents need no script evaluation
                })
                .executeSync(protocolFlow(true));

        assertThat(result.getStatus()).as(String.valueOf(result.getError())).isEqualTo(FlowStatus.COMPLETED);
        assertThat(evaluatorViews).hasSize(2);
        String registerHash = chain.submitted.get(0);
        assertThat(evaluatorViews.get(1).getTxOutput(registerHash, 0)).isPresent();
        assertThat(extension.scans.get(0)).extracting(Utxo::getTxHash).containsExactly(registerHash);
    }

    /** I11 (FlowExecutor path): a literal placeholder is never expanded, with or without variables. */
    @Test
    void literalPlaceholdersReachTheBuildUnchanged() {
        TxPlan withVariable = extension.plan(SENDER, pay(RECEIVER, 3_000_000).note("literal ${other} stays"))
                .setVariables(Map.of("other", "REINTERPRETED"));
        TxFlow flow = TxFlow.builder("literal")
                .addVariable("other", "FROM-FLOW")
                .addStep(FlowStep.builder("with-variable").withTxPlan(withVariable).build())
                .addStep(FlowStep.builder("without-variable").fundsFrom("with-variable")
                        .withTxPlan(extension.plan(SENDER,
                        pay(RECEIVER, 2_000_000).note("literal ${other} stays"))).build())
                .build();

        FlowResult result = executor(ChainingMode.PIPELINED).withExtension(extension).executeSync(flow);

        assertThat(result.getStatus()).as(String.valueOf(result.getError())).isEqualTo(FlowStatus.COMPLETED);
        assertThat(extension.notes).containsExactly("literal ${other} stays", "literal ${other} stays");
    }

    /** I4 and I11: every attempt gets an isolated, literal copy with the source's variables. */
    @Test
    void eachAttemptGetsAnIsolatedLiteralCopy() {
        FlowExtensions registered = FlowExtensions.of(List.of(extension));
        TxPlan source = extension.plan(SENDER, pay(RECEIVER, 3_000_000).note("literal ${other} stays"))
                .setVariables(Map.of("other", "REINTERPRETED"));
        FlowExecutionContext context = new FlowExecutionContext("flow");
        TxPlanExecutionMaterializer materializer = new TxPlanExecutionMaterializer();

        TxPlan first = materializer.materialize(source, Map.of("other", "FROM-FLOW"), context, registered);
        TxPlan retry = materializer.materialize(source, Map.of("other", "FROM-FLOW"), context, registered);

        for (TxPlan copy : List.of(first, retry)) {
            assertThat(copy.getExtensions()).isEqualTo(source.getExtensions());
            assertThat(copy.getVariables()).isEqualTo(source.getVariables());
            assertThat(registered.write(copy)).isEqualTo(registered.write(source));
            assertThat(intent(copy).getNote()).isEqualTo("literal ${other} stays");
            assertThat(intent(copy)).isNotSameAs(intent(source));
        }
        assertThat(intent(first)).isNotSameAs(intent(retry));
    }

    /** Builders created from a backend resolve reference scripts through the backend. */
    @Test
    void backendExecutorsUseTheBackendScriptLookup() {
        com.bloxbean.cardano.client.backend.api.BackendService backend =
                org.mockito.Mockito.mock(com.bloxbean.cardano.client.backend.api.BackendService.class);
        assertThat(FlowExecutor.backendScriptSupplier(backend)).isNull();

        org.mockito.Mockito.when(backend.getScriptService()).thenReturn(
                org.mockito.Mockito.mock(com.bloxbean.cardano.client.backend.api.ScriptService.class));
        assertThat(FlowExecutor.backendScriptSupplier(backend)).isNotNull();

        org.mockito.Mockito.when(backend.getScriptService()).thenThrow(new UnsupportedOperationException());
        assertThat(FlowExecutor.backendScriptSupplier(backend)).isNull();
    }

    private TxFlow protocolFlow(boolean fundingEdge) {
        FlowStep.Builder use = FlowStep.builder("use").needs("register")
                .withTxPlan(extension.plan(SENDER, pay(RECEIVER, 3_000_000).scan(PROTOCOL, true)));
        if (fundingEdge) use.fundsFrom("register");
        return TxFlow.builder("protocol-" + fundingEdge)
                .addStep(FlowStep.builder("register").withTxPlan(TxPlan.from(new Tx().from(SENDER)
                        .payToAddress(PROTOCOL, Amount.ada(2))).withSigner(FakeChain.SIGNER)).build())
                .addStep(use.build())
                .build();
    }

    private static PayIntent intent(TxPlan plan) {
        return (PayIntent) plan.getTxs().get(0).getIntentions().get(0);
    }

    private static List<BigInteger> paymentsTo(Transaction transaction, String address) {
        return transaction.getBody().getOutputs().stream()
                .filter(output -> address.equals(output.getAddress()))
                .map(TransactionOutput::getValue)
                .map(value -> value.getCoin())
                .toList();
    }
}
