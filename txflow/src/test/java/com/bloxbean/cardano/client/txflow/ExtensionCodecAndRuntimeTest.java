package com.bloxbean.cardano.client.txflow;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.api.BlockService;
import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.api.UtxoService;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.extension.ExtensionMetadata;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.codec.FlowDiagnostic;
import com.bloxbean.cardano.client.txflow.codec.FlowEncodingException;
import com.bloxbean.cardano.client.txflow.codec.FlowFormat;
import com.bloxbean.cardano.client.txflow.codec.FlowParseOptions;
import com.bloxbean.cardano.client.txflow.codec.FlowParseResult;
import com.bloxbean.cardano.client.txflow.codec.FlowSchemaVersion;
import com.bloxbean.cardano.client.txflow.codec.PortableFlowValidator;
import com.bloxbean.cardano.client.txflow.codec.FlowWriteOptions;
import com.bloxbean.cardano.client.txflow.codec.TxFlowCodec;
import com.bloxbean.cardano.client.txflow.compile.FlowCompilationRequest;
import com.bloxbean.cardano.client.txflow.compile.TxFlowCompiler;
import com.bloxbean.cardano.client.txflow.exec.FlowExecutor;
import com.bloxbean.cardano.client.txflow.internal.FlowExtensions;
import com.bloxbean.cardano.client.txflow.model.FlowBindings;
import com.bloxbean.cardano.client.txflow.testsupport.FakeChain;
import com.bloxbean.cardano.client.txflow.testsupport.FakeExtension;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.bloxbean.cardano.client.txflow.testsupport.FakeChain.RECEIVER;
import static com.bloxbean.cardano.client.txflow.testsupport.FakeChain.SENDER;
import static com.bloxbean.cardano.client.txflow.testsupport.FakeExtension.pay;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ADR 0008 M1: the canonical writer and parser for {@code spec.extensions} (D6a), the
 * FlowRuntime entry point, and the genericity invariant I3.
 */
class ExtensionCodecAndRuntimeTest {
    private static final FlowWriteOptions JSON = FlowWriteOptions.of(FlowFormat.JSON, FlowSchemaVersion.V1ALPHA1);
    private static final FlowWriteOptions YAML = FlowWriteOptions.of(FlowFormat.YAML, FlowSchemaVersion.V1ALPHA1);
    private final FakeExtension extension = new FakeExtension();

    private TxFlow flow() {
        return TxFlow.builder("codec-extension")
                .addStep(FlowStep.builder("core").withTxPlan(TxPlan.from(new Tx().from(SENDER)
                        .payToAddress(RECEIVER, Amount.ada(2)))).build())
                .addStep(FlowStep.builder("extension").needs("core").fundsFrom("core")
                        .withTxPlan(extension.plan(SENDER, pay(RECEIVER, 3_000_000))).build())
                .build();
    }

    @Test
    void theCanonicalWriterHoistsBindingsAndWritesNamespacedIntents() throws Exception {
        JsonNode written = new ObjectMapper().readTree(
                TxFlowCodec.withExtensions(List.of(extension)).write(flow(), JSON));

        JsonNode binding = written.at("/spec/extensions/fk");
        assertThat(binding.path("extension").asText()).isEqualTo("fake");
        assertThat(binding.at("/deployment/network").isTextual()).isTrue();
        assertThat(written.at("/spec/steps/1/transaction/tx/intents/0/type").asText()).isEqualTo("fk:pay");
        assertThat(written.at("/spec/steps/0/transaction/tx").toString()).doesNotContain("fk:");
    }

    @Test
    void theStandardCodecReportsTheExtensionAsUnregistered() {
        assertThatThrownBy(() -> TxFlowCodec.standard().write(flow(), YAML))
                .isInstanceOf(FlowEncodingException.class)
                .hasMessageContaining("TXFLOW_EXTENSION_UNREGISTERED");
    }

    @Test
    void conflictingPlanBindingsCannotBeWritten() {
        TxPlan other = extension.plan(SENDER, pay(RECEIVER, 1_000_000));
        TxPlan conflicting = TxPlan.from(new Tx().from(SENDER))
                .withExtension(FakeExtension.NAMESPACE, extension.metadata().toBuilder()
                        .contractVersion("2").build());
        conflicting.getTxs().get(0).addIntention(pay(RECEIVER, 1_000_000));
        TxFlow flow = TxFlow.builder("conflict")
                .addStep(FlowStep.builder("a").withTxPlan(other).build())
                .addStep(FlowStep.builder("b").withTxPlan(conflicting).build())
                .build();

        assertThatThrownBy(() -> TxFlowCodec.withExtensions(List.of(extension)).write(flow, YAML))
                .hasMessageContaining("TXFLOW_EXTENSION_BINDING_CONFLICT");
    }

    /** The portable form and the Java model compile to the same fingerprint (D6a, M2). */
    @Test
    void aWrittenExtensionFlowParsesAndCompilesToTheJavaFingerprint() {
        String yaml = TxFlowCodec.withExtensions(List.of(extension)).write(flow(), YAML);
        FlowParseResult parsed = TxFlowCodec.standard().parse(yaml, FlowParseOptions.serverDefaults());

        assertThat(parsed.hasErrors()).as(String.valueOf(parsed.getDiagnostics())).isFalse();
        TxFlow flow = parsed.requireFlow();
        assertThat(flow.getExtensions()).containsOnlyKeys("fk");
        assertThat(flow.getExtensions().get("fk")).isEqualTo(extension.metadata());
        TxFlowCompiler compiler = new TxFlowCompiler(List.of(extension));
        assertThat(compiler.compile(FlowCompilationRequest.of(flow, FlowBindings.empty()))
                .requireCompiledFlow().getFingerprint())
                .isEqualTo(compiler.compile(FlowCompilationRequest.of(flow(), FlowBindings.empty()))
                        .requireCompiledFlow().getFingerprint());
    }

    /**
     * The compiler copies a plan through YAML, which reads a small long back as an int; the same
     * binding at flow and plan level must still merge.
     */
    @Test
    void anIntegerDeploymentValueSurvivesCompilationOfIdenticalBindings() {
        Map<String, Object> deployment = new HashMap<>(extension.metadata().getDeployment());
        deployment.put("revision", 7L);
        ExtensionMetadata metadata = extension.metadata().toBuilder().deployment(deployment).build();
        Tx tx = new Tx().from(SENDER);
        tx.addIntention(pay(RECEIVER, 1_000_000));
        TxFlow flow = TxFlow.builder("long-revision").withExtension(FakeExtension.NAMESPACE, metadata)
                .addStep(FlowStep.builder("pay").withTxPlan(TxPlan.from(tx)
                        .withExtension(FakeExtension.NAMESPACE, metadata).withSigner(FakeChain.SIGNER)).build())
                .build();

        assertThat(new TxFlowCompiler(List.of(extension))
                .compile(FlowCompilationRequest.of(flow, FlowBindings.empty())).getDiagnostics()).isEmpty();
        assertThat(FlowExtensions.mergeable(
                Map.of(FakeExtension.NAMESPACE, metadata),
                Map.of(FakeExtension.NAMESPACE, metadata.toBuilder()
                        .deployment(Map.of("network", deployment.get("network"), "revision", 7)).build())))
                .isTrue();
    }

    /** I13 at parse time, plus binding shape and unknown-field checks. */
    @Test
    void portableBindingsAreValidatedWhenParsed() {
        String unused = """
                api_version: txflow.cardano-client.dev/v1alpha1
                kind: TxFlow
                metadata: {name: core-only}
                spec:
                  extensions:
                    fk: {extension: fake, schema_version: "1"}
                  steps:
                    - id: pay
                      transaction:
                        tx:
                          from_ref: account://sender
                          intents: []
                """;
        assertThat(codes(TxFlowCodec.standard().parse(unused, FlowParseOptions.serverDefaults())))
                .containsExactly("TXFLOW_EXTENSION_BINDING_UNUSED");
        assertThat(codes(TxFlowCodec.standard().parse(
                unused.replace("schema_version: \"1\"", "schema_version: \"1\", surprise: 1"),
                FlowParseOptions.serverDefaults())))
                .contains("TXFLOW_UNKNOWN_FIELD");
        assertThat(codes(TxFlowCodec.standard().parse(
                unused.replace(", schema_version: \"1\"", ""), FlowParseOptions.serverDefaults())))
                .isNotEmpty();
    }

    /**
     * D8 for Java plans: a flow_output pointer inside an extension intent is rejected by the
     * shared check, the compiler, the canonical writer and executor preflight, as it is in a
     * portable template, and nothing is submitted.
     */
    @Test
    void aFlowOutputInsideAJavaExtensionIntentIsRejectedEverywhere() throws Exception {
        FakeExtension.PayIntent intent = pay(RECEIVER, 3_000_000);
        intent.setExtra(new ObjectMapper().readTree("{\"datum\": {\"flow_output\": {\"step\": \"core\", \"output\": \"x\"}}}"));
        TxFlow flow = TxFlow.builder("flow-output")
                .addStep(FlowStep.builder("extension").withTxPlan(extension.plan(SENDER, intent)).build())
                .build();
        String path = "$.spec.steps[0].transaction.tx.intents[0]";

        assertThat(PortableFlowValidator.validate(flow, FlowExtensions.of(List.of(extension))))
                .extracting(FlowDiagnostic::code, FlowDiagnostic::documentPath)
                .containsExactly(tuple("TXFLOW_EXTENSION_FLOW_OUTPUT_UNSUPPORTED", path));
        assertThat(new TxFlowCompiler(List.of(extension)).compile(FlowCompilationRequest.of(flow, FlowBindings.empty()))
                .getDiagnostics()).extracting(FlowDiagnostic::code)
                .containsExactly("TXFLOW_EXTENSION_FLOW_OUTPUT_UNSUPPORTED");
        assertThatThrownBy(() -> TxFlowCodec.withExtensions(List.of(extension)).write(flow, YAML))
                .hasMessageContaining("TXFLOW_EXTENSION_FLOW_OUTPUT_UNSUPPORTED");

        FakeChain chain = new FakeChain();
        assertThatThrownBy(() -> FlowExecutor.create(chain.utxoSupplier, chain.protocolParamsSupplier,
                        chain.transactionProcessor, chain.chainDataSupplier)
                .withExecutor(Runnable::run).withSignerRegistry(chain.signers).withExtension(extension)
                .executeSync(flow))
                .hasMessageContaining("TXFLOW_EXTENSION_FLOW_OUTPUT_UNSUPPORTED");
        assertThat(chain.submitted).isEmpty();
    }

    @Test
    void theLegacyFormatRefusesExtensionFlows() {
        assertThatThrownBy(() -> flow().toYaml())
                .hasMessageContaining("legacy TxFlow YAML format cannot carry QuickTx extensions");
    }

    @Test
    void flowRuntimeRegistersTheExtensionOnItsEngine() {
        try (FlowRuntime runtime = FlowRuntime.builder(backend()).extension(extension).build();
             FlowRuntime plain = FlowRuntime.builder(backend()).build()) {
            assertThat(runtime.engine().preflight(FlowCompilationRequest.of(flow(), FlowBindings.empty()))
                    .getDiagnostics()).isEmpty();
            assertThat(plain.engine().preflight(FlowCompilationRequest.of(flow(), FlowBindings.empty()))
                    .getDiagnostics()).extracting(FlowDiagnostic::code)
                    .containsExactly("TXFLOW_EXTENSION_UNREGISTERED");
        }
    }

    /**
     * I3: TxFlow production code names no concrete extension, extension intent or extension
     * module, so a new extension needs no TxFlow change.
     */
    @Test
    void txflowHasNoExtensionSpecificCode() throws Exception {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertThat(source).as(file.toString())
                        .doesNotContain("programmabletoken")
                        .doesNotContain("implements QuickTxExtension")
                        .doesNotContain("implements ExtensionIntent");
            }
        }
        assertThat(Files.readString(Path.of("build.gradle"))).doesNotContain("programmable-token");
    }

    private static List<String> codes(FlowParseResult result) {
        return result.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.severity()
                        == com.bloxbean.cardano.client.txflow.codec.DiagnosticSeverity.ERROR)
                .map(FlowDiagnostic::code).toList();
    }

    private static BackendService backend() {
        BackendService backend = mock(BackendService.class);
        when(backend.getUtxoService()).thenReturn(mock(UtxoService.class));
        when(backend.getEpochService()).thenReturn(mock(EpochService.class));
        when(backend.getTransactionService()).thenReturn(mock(TransactionService.class));
        when(backend.getBlockService()).thenReturn(mock(BlockService.class));
        return backend;
    }
}
