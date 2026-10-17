package com.bloxbean.cardano.client.txflow.compile;

import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.codec.DiagnosticSeverity;
import com.bloxbean.cardano.client.txflow.codec.FlowDiagnostic;
import com.bloxbean.cardano.client.txflow.codec.FlowParseOptions;
import com.bloxbean.cardano.client.txflow.codec.FlowParseResult;
import com.bloxbean.cardano.client.txflow.codec.TxFlowCodec;
import com.bloxbean.cardano.client.txflow.exec.FlowEngine;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionRequest;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionResult;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;
import com.bloxbean.cardano.client.txflow.model.FlowBindings;
import com.bloxbean.cardano.client.txflow.testsupport.FakeChain;
import com.bloxbean.cardano.client.txflow.testsupport.FakeExtension;
import com.bloxbean.cardano.client.txflow.testsupport.FakeExtension.PayIntent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.bloxbean.cardano.client.txflow.testsupport.FakeChain.RECEIVER;
import static com.bloxbean.cardano.client.txflow.testsupport.FakeChain.SENDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * ADR 0008 M2: portable v1alpha1 templates that use extension intents, compiled through the
 * registered extension with literal decoding (D4a, D6) and the round-trip guard (D7).
 */
class PortableExtensionTemplateTest {
    private static final String INTENT = "$.spec.steps[0].transaction.tx.intents[0]";
    /** Indentation of an intent field in {@link #DOCUMENT}. */
    private static final String FIELD = " ".repeat(14);
    private static final String DOCUMENT = """
            api_version: txflow.cardano-client.dev/v1alpha1
            kind: TxFlow
            metadata: {name: portable-extension}
            spec:
              parameters:
                receiver: {type: address, required: true}
                note: {type: string, required: true}
              extensions:
                fk:
                  extension: fake
                  schema_version: "1"
                  deployment: {network: "0", label: a}
              steps:
                - id: pay
                  transaction:
                    tx:
                      from: SENDER
                      intents:
                        - type: fk:pay
                          receiver: '${{ inputs.receiver }}'
                          lovelace: 3000000
                          note: '${{ inputs.note }}'
                    context:
                      signers:
                        - {ref: 'account://sender', scope: payment}
            """.replace("SENDER", SENDER);

    private final FakeExtension extension = new FakeExtension();
    private final TxFlowCompiler compiler = new TxFlowCompiler(List.of(extension));

    private static TxFlow parse(String yaml) {
        return TxFlowCodec.standard().parse(yaml, FlowParseOptions.serverDefaults()).requireFlow();
    }

    private static FlowBindings bindings(String note) {
        return FlowBindings.builder().put("receiver", RECEIVER).put("note", note).build();
    }

    private FlowCompilationResult compile(String yaml) {
        return compiler.compile(FlowCompilationRequest.of(parse(yaml), bindings("hello")));
    }

    /** I11 (portable): a bound value that looks like a placeholder stays literal. */
    @Test
    void boundValuesAreDecodedLiterally() {
        CompiledTxFlow compiled = compiler.compile(FlowCompilationRequest.of(parse(DOCUMENT),
                bindings("literal ${other} stays"))).requireCompiledFlow();

        PayIntent intent = (PayIntent) compiled.getExecutionPlan().getSteps().get(0)
                .getTxPlan().getTxs().get(0).getIntentions().get(0);
        assertThat(intent.getReceiver()).isEqualTo(RECEIVER);
        assertThat(intent.getLovelace()).isEqualTo(3_000_000);
        assertThat(intent.getNote()).isEqualTo("literal ${other} stays");
    }

    /** I8 (portable): bindings and intent fields are in the fingerprint; key order is not. */
    @Test
    void theFingerprintCoversPortableBindingsAndIntents() {
        String base = compile(DOCUMENT).requireCompiledFlow().getFingerprint();

        assertThat(compile(DOCUMENT.replace("label: a", "label: b")).requireCompiledFlow().getFingerprint())
                .isNotEqualTo(base);
        assertThat(compile(DOCUMENT.replace("lovelace: 3000000", "lovelace: 4000000"))
                .requireCompiledFlow().getFingerprint()).isNotEqualTo(base);
        assertThat(compile(DOCUMENT.replace("{network: \"0\", label: a}", "{label: a, network: \"0\"}"))
                .requireCompiledFlow().getFingerprint()).isEqualTo(base);
        assertThat(compiler.compile(FlowCompilationRequest.of(parse(DOCUMENT), bindings("other")))
                .requireCompiledFlow().getFingerprint()).isNotEqualTo(base);
    }

    /** I7: an unknown field and a value that does not round-trip are rejected at their intent. */
    @Test
    void lossyOrUnknownIntentFieldsAreRejected() {
        assertThat(errors(compile(DOCUMENT.replace("lovelace: 3000000", "lovelace: 3000000\n" + FIELD + "surprise: 1"))))
                .containsExactly(tuple("TXFLOW_EXTENSION_INTENT_INVALID", "$.spec.steps[0].transaction.tx.intents"));
        assertThat(errors(compile(DOCUMENT.replace("lovelace: 3000000", "lovelace: '3000000'"))))
                .containsExactly(tuple("TXFLOW_EXTENSION_INTENT_LOSSY", INTENT));
    }

    @Test
    void extensionIntentRulesAreCheckedBeforeBinding() {
        assertThat(errors(new TxFlowCompiler().compile(FlowCompilationRequest.of(parse(DOCUMENT), bindings("x")))))
                .containsExactly(tuple("TXFLOW_EXTENSION_UNREGISTERED", "$.spec.extensions.fk"));
        assertThat(errors(compile(DOCUMENT.replace("type: fk:pay", "type: fk:burn"))))
                .containsExactly(tuple("TXFLOW_EXTENSION_INTENT_INVALID", INTENT));
        assertThat(errors(compile(DOCUMENT.replace("lovelace: 3000000",
                "lovelace: 3000000\n" + FIELD + "lookup: {flow_output: {step: pay, output: x}}"))))
                .contains(tuple("TXFLOW_EXTENSION_FLOW_OUTPUT_UNSUPPORTED", INTENT));
    }

    @Test
    void namespacesMustBeDeclaredUsedAndOnlyInIntents() {
        String undeclared = DOCUMENT.replace("type: fk:pay", "type: zz:pay");
        FlowParseResult parsed = TxFlowCodec.standard().parse(undeclared, FlowParseOptions.serverDefaults());
        assertThat(errors(parsed.getDiagnostics())).containsExactly(
                tuple("TXFLOW_EXTENSION_BINDING_UNUSED", "$.spec.extensions.fk"));

        String inInputs = DOCUMENT.replace("          intents:\n", "          inputs:\n");
        assertThat(errors(TxFlowCodec.standard().parse(inInputs, FlowParseOptions.serverDefaults())
                .getDiagnostics())).containsExactly(tuple("TXFLOW_EXTENSION_BINDING_UNUSED", "$.spec.extensions.fk"));

        String twoNamespaces = DOCUMENT.replace("  steps:\n",
                "    fk2: {extension: fake, schema_version: \"1\"}\n  steps:\n");
        assertThat(errors(TxFlowCodec.standard().parse(twoNamespaces, FlowParseOptions.serverDefaults())
                .getDiagnostics())).containsExactly(
                tuple("TXFLOW_EXTENSION_BINDING_CONFLICT", "$.spec.extensions.fk2"));
    }

    /** A step that only uses an undeclared namespace is reported at the intent when compiled. */
    @Test
    void anUndeclaredNamespaceIsReportedAtTheIntent() {
        String undeclared = DOCUMENT.replace("note: '${{ inputs.note }}'\n",
                "note: '${{ inputs.note }}'\n"
                        + "            - type: zz:pay\n"
                        + FIELD + "receiver: '${{ inputs.receiver }}'\n"
                        + FIELD + "lovelace: 1000000\n");
        assertThat(errors(compile(undeclared))).containsExactly(
                tuple("TXFLOW_EXTENSION_INTENT_INVALID", "$.spec.steps[0].transaction.tx.intents[1]"));
    }

    /** A portable extension flow runs end to end on FlowEngine. */
    @Test
    void aPortableExtensionFlowRunsOnTheEngine() {
        FakeChain chain = new FakeChain();
        FlowEngine engine = FlowEngine.builder(chain.utxoSupplier, chain.protocolParamsSupplier,
                        chain.transactionProcessor, chain.chainDataSupplier)
                .executor(Runnable::run)
                .signerRegistry(chain.signers)
                .extension(extension)
                .build();

        FlowExecutionResult result = engine.start(FlowExecutionRequest.builder(parse(DOCUMENT))
                .bindings(bindings("from yaml")).executionId("portable-1").build()).await();

        assertThat(result.state()).as(String.valueOf(result)).isEqualTo(FlowExecutionState.COMPLETED);
        assertThat(extension.notes).containsExactly("from yaml");
        assertThat(chain.submitted).hasSize(1);
    }

    private static List<org.assertj.core.groups.Tuple> errors(FlowCompilationResult result) {
        return errors(result.getDiagnostics());
    }

    private static List<org.assertj.core.groups.Tuple> errors(List<FlowDiagnostic> diagnostics) {
        return diagnostics.stream().filter(d -> d.severity() == DiagnosticSeverity.ERROR)
                .map(d -> tuple(d.code(), d.documentPath())).toList();
    }
}
