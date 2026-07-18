package com.bloxbean.cardano.client.txflow.codec;

import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.RetryPolicy;
import com.bloxbean.cardano.client.txflow.StepDependency;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.config.ConfirmationConfig;
import com.bloxbean.cardano.client.txflow.config.FlowExecutionSettings;
import com.bloxbean.cardano.client.txflow.config.RollbackAction;
import com.bloxbean.cardano.client.txflow.config.RollbackPolicy;
import com.bloxbean.cardano.client.txflow.config.RollbackStrategy;
import com.bloxbean.cardano.client.txflow.model.TransactionTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TxFlowCodecTest {
    private final TxFlowCodec codec = TxFlowCodec.standard();

    private static final String PORTABLE = """
            api_version: txflow.cardano-client.dev/v1alpha1
            kind: TxFlow
            metadata:
              name: pay
              version: 1.2.0
              annotations:
                owner: treasury
            spec:
              network: preview
              parameters:
                beneficiary:
                  type: address
                  required: true
                amount:
                  type: integer
                  default: 5000000
                  minimum: 1000000
              execution:
                mode: PIPELINED
                confirmation:
                  min_confirmations: 3
                  check_interval: 1s
                  timeout: 2m
                retry:
                  max_attempts: 3
                  backoff: exponential
                  initial_delay: 1s
                  max_delay: 20s
              steps:
                - id: payment
                  transaction:
                    tx:
                      from_ref: account://treasury
                      intents:
                        - type: payment
                          address: '${{ inputs.beneficiary }}'
                          amounts:
                            - unit: lovelace
                              quantity: '${{ inputs.amount }}'
                    context:
                      fee_payer_ref: account://treasury
            """;

    @Test
    void parsesAndCanonicallyRoundTripsPortableYamlAndJson() {
        FlowParseResult parsed = codec.parse(PORTABLE, FlowParseOptions.serverDefaults());
        assertFalse(parsed.hasErrors(), parsed.getDiagnostics().toString());
        TxFlow flow = parsed.requireFlow();
        assertEquals("pay", flow.getId());
        assertEquals("preview", flow.getNetwork());
        assertEquals(2, flow.getParameters().size());
        assertEquals(com.bloxbean.cardano.client.txflow.ChainingMode.PIPELINED,
                flow.getExecutionSettings().getChainingMode());
        assertEquals(3, flow.getExecutionSettings().getConfirmationConfig().getMinConfirmations());

        String yaml = codec.write(flow, FlowWriteOptions.of(FlowFormat.YAML, FlowSchemaVersion.V1ALPHA1));
        String json = codec.write(flow, FlowWriteOptions.of(FlowFormat.JSON, FlowSchemaVersion.V1ALPHA1));

        assertFalse(codec.parse(yaml, FlowParseOptions.serverDefaults()).hasErrors());
        assertFalse(codec.parse(json, FlowParseOptions.serverDefaults()).hasErrors());
        assertEquals(json, codec.write(codec.parse(json, FlowParseOptions.serverDefaults())
                        .requireFlow(),
                FlowWriteOptions.of(FlowFormat.JSON, FlowSchemaVersion.V1ALPHA1)));
    }

    @Test
    void portableWriterRejectsEveryLegacyStepDependencyStrategy() {
        TransactionTemplate transaction = portableTransaction();
        FlowStep source = FlowStep.builder("source")
                .withTransactionTemplate(transaction)
                .build();
        List<StepDependency> dependencies = List.of(
                StepDependency.all("source"),
                StepDependency.atIndex("source", 0),
                StepDependency.filter("source", utxo -> true));

        for (StepDependency dependency : dependencies) {
            FlowStep consumer = FlowStep.builder("consumer")
                    .withTransactionTemplate(transaction)
                    .dependsOn(dependency)
                    .build();
            TxFlow flow = TxFlow.builder("legacy-dependency")
                    .addStep(source)
                    .addStep(consumer)
                    .build();

            FlowEncodingException failure = assertThrows(FlowEncodingException.class,
                    () -> codec.write(flow, portableYaml()));

            assertTrue(failure.getMessage().contains(dependency.getStrategy().name()));
            assertTrue(failure.getMessage().contains("needs(...)"));
            assertTrue(failure.getMessage().contains("bindOutput(...)"));
            assertTrue(failure.getMessage().contains("flow_output"));
        }
    }

    @Test
    void portableWriterRejectsStepLevelRetryPolicy() {
        TxFlow flow = singleStepFlow(FlowStep.builder("one")
                .withTransactionTemplate(portableTransaction())
                .withRetryPolicy(RetryPolicy.defaults())
                .build());

        FlowEncodingException failure = assertThrows(FlowEncodingException.class,
                () -> codec.write(flow, portableYaml()));

        assertTrue(failure.getMessage().contains("Step-level retry policies"));
        assertTrue(failure.getMessage().contains("spec.execution.retry"));
    }

    @Test
    void portableWriterRejectsLegacyFlowVariables() {
        TxFlow flow = TxFlow.builder("legacy-variables")
                .addVariable("amount", 1_000_000L)
                .addStep(FlowStep.builder("one")
                        .withTransactionTemplate(portableTransaction())
                        .build())
                .build();

        FlowEncodingException failure = assertThrows(FlowEncodingException.class,
                () -> codec.write(flow, portableYaml()));

        assertTrue(failure.getMessage().contains("Legacy flow variables"));
        assertTrue(failure.getMessage().contains("parameters"));
        assertTrue(failure.getMessage().contains("FlowBindings"));
    }

    @Test
    void rejectPolicyAppliesInsideRawExecutionSubtree() {
        String unknownExecution = PORTABLE.replace("mode: PIPELINED",
                "mode: PIPELINED\n    bogus_setting: 42");
        String unknownConfirmation = PORTABLE.replace("min_confirmations: 3",
                "min_confirmations: 3\n      min_confirmatoins: 4");

        for (String source : List.of(unknownExecution, unknownConfirmation)) {
            FlowParseResult result = codec.parse(source, FlowParseOptions.serverDefaults());
            assertTrue(result.hasErrors(), result.getDiagnostics().toString());
            assertEquals("TXFLOW_UNKNOWN_FIELD", result.getDiagnostics().get(0).code());
            assertTrue(result.getDiagnostics().get(0).documentPath().contains("execution"));
        }
    }

    @Test
    void executionNumbersRejectStringsNullsFractionsAndOverflow() {
        List<String> invalid = List.of(
                PORTABLE.replace("min_confirmations: 3", "min_confirmations: notanumber"),
                PORTABLE.replace("min_confirmations: 3", "min_confirmations: null"),
                PORTABLE.replace("max_attempts: 3", "max_attempts: 1.5"),
                PORTABLE.replace("max_attempts: 3", "max_attempts: 999999999999999999999"),
                PORTABLE.replace("max_delay: 20s", "max_delay: 20s\n      jitter: nope"));

        for (String source : invalid) {
            FlowParseResult result = codec.parse(source, FlowParseOptions.serverDefaults());
            assertTrue(result.hasErrors(), result.getDiagnostics().toString());
            assertTrue(result.getDiagnostics().stream().anyMatch(diagnostic ->
                    diagnostic.code().equals("TXFLOW_FIELD_TYPE")),
                    result.getDiagnostics().toString());
        }
    }

    @Test
    void portableEnvelopeScalarsNeverUseJacksonCoercion() throws Exception {
        assertTypeErrorInYamlAndJson(PORTABLE.replace("name: pay", "name: 123"),
                "$.metadata.name");
        assertTypeErrorInYamlAndJson(PORTABLE.replace("owner: treasury", "owner: null"),
                "$.metadata.annotations.owner");
        assertTypeErrorInYamlAndJson(PORTABLE.replace("network: preview", "network: 42"),
                "$.spec.network");
        assertTypeErrorInYamlAndJson(PORTABLE.replace("type: address", "type: 17"),
                "$.spec.parameters.beneficiary.type");
        assertTypeErrorInYamlAndJson(PORTABLE.replace("required: true", "required: null"),
                "$.spec.parameters.beneficiary.required");
        assertTypeErrorInYamlAndJson(PORTABLE.replace("required: true",
                        "required: true\n      sensitive: null"),
                "$.spec.parameters.beneficiary.sensitive");
        assertTypeErrorInYamlAndJson(PORTABLE.replace("minimum: 1000000",
                        "minimum: '1000000'"),
                "$.spec.parameters.amount.minimum");
        assertTypeErrorInYamlAndJson(PORTABLE.replace("- id: payment", "- id: 99"),
                "$.spec.steps[0].id");
        assertTypeErrorInYamlAndJson(PORTABLE.replace("- id: payment",
                        "- id: payment\n      needs: [7]"),
                "$.spec.steps[0].needs[0]");
        assertTypeErrorInYamlAndJson(PORTABLE.replace("      transaction:",
                        "      outputs:\n"
                                + "        chosen:\n"
                                + "          select: {output_index: '0'}\n"
                                + "          expect: exactly_one\n"
                                + "      transaction:"),
                "$.spec.steps[0].outputs.chosen.select.output_index");
    }

    @Test
    void portableWriterRejectsNullAnnotationsInsteadOfViolatingSchema() {
        TxFlow flow = TxFlow.builder("annotation")
                .addAnnotation("owner", null)
                .addStep(FlowStep.builder("one")
                        .withTransactionTemplate(portableTransaction())
                        .build())
                .build();

        FlowEncodingException failure = assertThrows(FlowEncodingException.class,
                () -> codec.write(flow, portableYaml()));

        assertTrue(failure.getMessage().contains("TXFLOW_NON_PORTABLE_ANNOTATION"));
        assertTrue(failure.getMessage().contains("metadata.annotations"));
    }

    @Test
    void allDocumentedConfirmationPresetsAreHonoredAndUnknownPresetIsRejected()
            throws Exception {
        Set<String> schemaPresets = confirmationPresetsFromSchema();
        assertEquals(Set.of("defaults", "devnet", "testnet", "quick"), schemaPresets);
        assertPreset("defaults", ConfirmationConfig.defaults());
        assertPreset("devnet", ConfirmationConfig.devnet());
        assertPreset("testnet", ConfirmationConfig.testnet());
        assertPreset("quick", ConfirmationConfig.quick());
        for (String preset : schemaPresets) {
            TxFlow flow = codec.parse(withConfirmationPreset(preset),
                    FlowParseOptions.serverDefaults()).requireFlow();
            FlowWriteOptions jsonOptions = FlowWriteOptions.of(
                    FlowFormat.JSON, FlowSchemaVersion.V1ALPHA1);
            String first = codec.write(flow, jsonOptions);
            JsonNode written = new ObjectMapper().readTree(first);
            JsonNode emittedPreset = written.at("/spec/execution/confirmation/preset");
            assertTrue(emittedPreset.isMissingNode()
                    || schemaPresets.contains(emittedPreset.asText()));
            assertEquals(first, codec.write(codec.parse(first,
                    FlowParseOptions.serverDefaults()).requireFlow(), jsonOptions));
        }

        FlowParseResult unknown = codec.parse(withConfirmationPreset("production"),
                FlowParseOptions.serverDefaults());
        assertTrue(unknown.hasErrors());
        assertEquals("TXFLOW_CONFIRMATION_PRESET_UNSUPPORTED",
                unknown.getDiagnostics().get(0).code());
        assertEquals("$.spec.execution.confirmation.preset",
                unknown.getDiagnostics().get(0).documentPath());
    }

    @Test
    void pauseForRecoveryNeverMapsToAutomaticRebuild() {
        String source = PORTABLE.replace("    retry:\n", """
                    rollback:
                      action: PAUSE_FOR_RECOVERY
                    retry:
                """);

        TxFlow flow = codec.parse(source, FlowParseOptions.serverDefaults()).requireFlow();

        assertEquals(RollbackAction.PAUSE_FOR_RECOVERY,
                flow.getExecutionSettings().getRollbackPolicy().action());
        assertNull(flow.getExecutionSettings().getRollbackStrategy());
    }

    @Test
    void legacyRollbackStrategiesFailFastInsteadOfBeingApproximated() {
        for (RollbackStrategy strategy : RollbackStrategy.values()) {
            TxFlow flow = singleStepFlowWithSettings(FlowExecutionSettings.builder()
                    .rollbackStrategy(strategy).build());
            FlowEncodingException failure = assertThrows(FlowEncodingException.class,
                    () -> codec.write(flow, portableYaml()));
            assertTrue(failure.getMessage().contains("TXFLOW_NON_PORTABLE_ROLLBACK_STRATEGY"));
        }
    }

    @Test
    void everyPortableRollbackActionIsInSchemaAndRoundTripsCanonically()
            throws Exception {
        Set<String> schemaActions = rollbackActionsFromSchema();
        RollbackPolicy defaults = RollbackPolicy.defaults();
        for (RollbackAction action : RollbackAction.values()) {
            RollbackPolicy policy = new RollbackPolicy(action, defaults.monitoringHorizon(),
                    defaults.rebuildScope(), defaults.maxRecoveryCycles(),
                    defaults.reinclusionWindow(),
                    defaults.minimumConsistentAbsenceObservations());
            TxFlow flow = singleStepFlowWithSettings(FlowExecutionSettings.builder()
                    .rollbackPolicy(policy).build());

            String first = codec.write(flow,
                    FlowWriteOptions.of(FlowFormat.JSON, FlowSchemaVersion.V1ALPHA1));
            JsonNode root = new ObjectMapper().readTree(first);
            String encodedAction = root.at("/spec/execution/rollback/action").asText();
            assertTrue(schemaActions.contains(encodedAction), encodedAction);

            String second = codec.write(codec.parse(first, FlowParseOptions.serverDefaults())
                            .requireFlow(),
                    FlowWriteOptions.of(FlowFormat.JSON, FlowSchemaVersion.V1ALPHA1));
            assertEquals(first, second, encodedAction);
        }
    }

    @Test
    void portableWriterRejectsUnsupportedRetryFiltersAndPreservesFlowDescription() {
        RetryPolicy filteredRetry = RetryPolicy.builder()
                .retryOnNetworkError(false)
                .build();
        TxFlow retryFlow = singleStepFlowWithSettings(FlowExecutionSettings.builder()
                .retryPolicy(filteredRetry)
                .build());
        TxFlow described = TxFlow.builder("described")
                .withDescription("not in portable metadata")
                .addStep(FlowStep.builder("one")
                        .withTransactionTemplate(portableTransaction())
                        .build())
                .build();

        assertTrue(assertThrows(FlowEncodingException.class,
                () -> codec.write(retryFlow, portableYaml())).getMessage()
                .contains("TXFLOW_NON_PORTABLE_RETRY_FILTERS"));
        String encodedDescription = codec.write(described, portableYaml());
        assertEquals("not in portable metadata", codec.parse(encodedDescription,
                FlowParseOptions.serverDefaults()).requireFlow().getDescription());
    }

    @Test
    void portablePresetsDoNotSmuggleLegacyBackendWaitSettings() {
        ConfirmationConfig parsedDevnet = codec.parse(withConfirmationPreset("devnet"),
                        FlowParseOptions.serverDefaults()).requireFlow()
                .getExecutionSettings().getConfirmationConfig();
        assertEquals(3, parsedDevnet.getMinConfirmations());
        assertFalse(parsedDevnet.isWaitForBackendAfterRollback());
        assertEquals(5, parsedDevnet.getPostRollbackWaitAttempts());
        assertEquals(Duration.ZERO, parsedDevnet.getPostRollbackUtxoSyncDelay());

        TxFlow javaDevnet = singleStepFlowWithSettings(FlowExecutionSettings.builder()
                .confirmationConfig(ConfirmationConfig.devnet()).build());
        assertTrue(assertThrows(FlowEncodingException.class,
                () -> codec.write(javaDevnet, portableYaml())).getMessage()
                .contains("TXFLOW_NON_PORTABLE_CONFIRMATION_COMPATIBILITY"));
    }

    @Test
    void malformedInputReachesParseDiagnosticsInsteadOfKindDetection() {
        FlowParseResult malformed = codec.parse("kind: [TxFlow\n",
                FlowParseOptions.serverDefaults());
        assertTrue(malformed.hasErrors());
        assertEquals("TXFLOW_PARSE_ERROR", malformed.getDiagnostics().get(0).code());
        assertTrue(malformed.getDiagnostics().get(0).line() != null);

        FlowParseResult multiple = codec.parse(PORTABLE + "\n---\n" + PORTABLE,
                FlowParseOptions.serverDefaults());
        assertTrue(multiple.hasErrors());
        assertEquals("TXFLOW_PARSE_ERROR", multiple.getDiagnostics().get(0).code());
        assertTrue(multiple.getDiagnostics().get(0).message().contains("Multiple documents"));

        String duplicateJson = """
                {"api_version":"txflow.cardano-client.dev/v1alpha1",
                 "kind":"TxFlow", "kind":"TxFlow",
                 "metadata":{"name":"duplicate"},
                 "spec":{"steps":[{"id":"one","transaction":{"tx":{"intents":[]}}}]}}
                """;
        FlowParseResult duplicate = codec.parse(duplicateJson, FlowParseOptions.serverDefaults());
        assertTrue(duplicate.hasErrors());
        assertEquals("TXFLOW_PARSE_ERROR", duplicate.getDiagnostics().get(0).code());
        assertTrue(duplicate.getDiagnostics().get(0).line() != null);
    }

    @Test
    void maxDocumentBytesIsEnforcedBeforeDecoding() {
        int bytes = PORTABLE.getBytes(StandardCharsets.UTF_8).length;
        FlowParseResult result = codec.parse(PORTABLE, FlowParseOptions.builder()
                .maxDocumentBytes(bytes - 1)
                .build());

        assertTrue(result.hasErrors());
        assertEquals("TXFLOW_DOCUMENT_TOO_LARGE", result.getDiagnostics().get(0).code());
    }

    @Test
    void maxStepsIsEnforcedBeforeGraphCompilation() {
        String twoSteps = PORTABLE + "\n" + """
                    - id: second
                      transaction:
                        tx: {intents: []}
                """;
        assertFalse(codec.parse(twoSteps, FlowParseOptions.serverDefaults()).hasErrors());

        FlowParseResult result = codec.parse(twoSteps, FlowParseOptions.builder()
                .maxSteps(1)
                .build());

        assertTrue(result.hasErrors());
        assertEquals("TXFLOW_TOO_MANY_STEPS", result.getDiagnostics().get(0).code());
    }

    @Test
    void yamlCollectionAliasLimitIsEnforced() {
        int marker = PORTABLE.indexOf("intents:");
        int lineStart = PORTABLE.lastIndexOf('\n', marker) + 1;
        String indentation = PORTABLE.substring(lineStart, marker);
        String aliased = PORTABLE.substring(0, marker)
                + "shared: &shared []\n" + indentation
                + "repeated: *shared\n" + indentation
                + PORTABLE.substring(marker);
        assertTrue(aliased.contains("&shared"));
        assertFalse(codec.parse(aliased, FlowParseOptions.serverDefaults()).hasErrors());

        FlowParseResult result = codec.parse(aliased, FlowParseOptions.builder()
                .maxAliases(0)
                .build());

        assertTrue(result.hasErrors());
        assertEquals("TXFLOW_PARSE_ERROR", result.getDiagnostics().get(0).code());
    }

    @Test
    void yamlNestingDepthLimitIsEnforced() {
        StringBuilder deeplyNested = new StringBuilder("""
                api_version: txflow.cardano-client.dev/v1alpha1
                kind: TxFlow
                metadata: {name: deeply-nested}
                spec:
                  steps:
                    - id: one
                      transaction:
                        tx:
                """);
        for (int level = 0; level < 30; level++) {
            deeplyNested.append("            ").append("  ".repeat(level))
                    .append("level_").append(level).append(":\n");
        }
        deeplyNested.append("            ").append("  ".repeat(30)).append("value: true\n");
        assertFalse(codec.parse(deeplyNested.toString(),
                FlowParseOptions.serverDefaults()).hasErrors());

        FlowParseResult result = codec.parse(deeplyNested.toString(), FlowParseOptions.builder()
                .maxNestingDepth(10)
                .build());

        assertTrue(result.hasErrors());
        assertEquals("TXFLOW_PARSE_ERROR", result.getDiagnostics().get(0).code());
    }

    @Test
    void multilineLegacyExpressionsAreRejectedButCommentTextIsIgnored() {
        String multiline = PORTABLE.replace("'${{ inputs.beneficiary }}'",
                "\"line one\n${legacy}\"");
        FlowParseResult portable = codec.parse(multiline, FlowParseOptions.serverDefaults());
        assertTrue(portable.hasErrors());
        assertEquals("TXFLOW_EXPRESSION_SYNTAX", portable.getDiagnostics().get(0).code());

        String legacy = """
                # ${{ this is only a comment }}
                version: '1.0'
                flow:
                  id: comment
                  steps:
                    - step: {id: one, tx: {intents: []}}
                """;
        assertFalse(codec.parse(legacy, FlowParseOptions.serverDefaults()).hasErrors());
    }

    @Test
    void portableWriterRejectsTxPlanVariables() {
        TxPlan plan = TxPlan.from(new Tx())
                .addVariable("amount", 1_000_000L);
        TxFlow flow = singleStepFlow(FlowStep.builder("one")
                .withTxPlan(plan)
                .build());

        FlowEncodingException failure = assertThrows(FlowEncodingException.class,
                () -> codec.write(flow, portableYaml()));

        assertTrue(failure.getMessage().contains("TxPlan variables"));
        assertTrue(failure.getMessage().contains("declare parameters"));
        assertTrue(failure.getMessage().contains("FlowBindings"));
    }

    @Test
    void txFlowConvenienceParserAcceptsPortableAndLegacyDocuments() {
        assertEquals("pay", TxFlow.fromYaml(PORTABLE).getId());
        String legacy = """
                version: "1.0"
                flow:
                  id: legacy
                  steps:
                    - step:
                        id: step
                        tx: {intents: []}
                """;
        assertEquals("legacy", TxFlow.fromYaml(legacy).getId());
    }

    @Test
    void detectsDocumentKinds() {
        assertEquals(FlowDocumentType.TX_FLOW, codec.detect(PORTABLE));
        assertEquals(FlowDocumentType.TX_PLAN, codec.detect("version: 1.0\ntransaction: []"));
        assertEquals(FlowDocumentType.UNKNOWN, codec.detect("name: something"));
    }

    @Test
    void rejectsLegacyExpressionSyntaxInPortableDocument() {
        FlowParseResult result = codec.parse(PORTABLE.replace(
                "${{ inputs.beneficiary }}", "${beneficiary}"), FlowParseOptions.serverDefaults());
        assertTrue(result.hasErrors());
        assertEquals("TXFLOW_EXPRESSION_SYNTAX", result.getDiagnostics().get(0).code());
    }

    @Test
    void rejectsUnknownPortableFieldsWithLocation() {
        FlowParseResult result = codec.parse(PORTABLE.replace(
                "network: preview", "network: preview\n  unexpected: true"),
                FlowParseOptions.serverDefaults());
        assertTrue(result.hasErrors());
        assertEquals("TXFLOW_UNKNOWN_FIELD", result.getDiagnostics().get(0).code());
        assertEquals("$.spec.unexpected", result.getDiagnostics().get(0).documentPath());
    }

    @Test
    void legacyDecoderProducesMigrationWarning() {
        String legacy = """
                version: '1.0'
                flow:
                  id: legacy
                  steps:
                    - step:
                        id: one
                        tx:
                          intents: []
                """;
        FlowParseResult result = codec.parse(legacy, FlowParseOptions.serverDefaults());
        assertFalse(result.hasErrors(), result.getDiagnostics().toString());
        assertEquals(DiagnosticSeverity.WARNING, result.getDiagnostics().get(0).severity());
    }

    @Test
    void versionlessLegacyFilterIsAcceptedWithSpecificDiagnostics() {
        String legacy = """
                flow:
                  id: legacy-filter
                  steps:
                    - step: {id: source, tx: {intents: []}}
                    - step:
                        id: consumer
                        depends_on:
                          - from_step: source
                            strategy: filter
                            filter: legacy-expression
                        tx: {intents: []}
                """;

        FlowParseResult result = codec.parse(legacy, FlowParseOptions.serverDefaults());

        assertFalse(result.hasErrors(), result.getDiagnostics().toString());
        assertTrue(result.getDiagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().equals("TXFLOW_LEGACY_VERSION_DEFAULTED")));
        assertTrue(result.getDiagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().equals("TXFLOW_LEGACY_UNUSED_FILTER")));
    }

    @Test
    void rejectsMultiplePortableDocumentsAndDuplicateJsonFields() {
        FlowParseResult multiple = codec.parse(PORTABLE + "\n---\n" + PORTABLE,
                FlowParseOptions.serverDefaults());
        assertTrue(multiple.hasErrors());

        String duplicateJson = """
                {"api_version":"txflow.cardano-client.dev/v1alpha1",
                 "kind":"TxFlow", "kind":"TxFlow",
                 "metadata":{"name":"duplicate"},
                 "spec":{"steps":[{"id":"one","transaction":{"tx":{"intents":[]}}}]}}
                """;
        assertTrue(codec.parse(duplicateJson, FlowParseOptions.serverDefaults()).hasErrors());
    }

    @Test
    void unknownFieldsCanWarnOrBeIgnoredWithoutChangingParsedMeaning() {
        String source = PORTABLE.replace("network: preview",
                "network: preview\n  future_extension: true");
        FlowParseResult warned = codec.parse(source, FlowParseOptions.builder()
                .unknownFieldPolicy(UnknownFieldPolicy.WARN).build());
        assertFalse(warned.hasErrors(), warned.getDiagnostics().toString());
        assertTrue(warned.getDiagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("TXFLOW_UNKNOWN_FIELD")));

        FlowParseResult ignored = codec.parse(source, FlowParseOptions.builder()
                .unknownFieldPolicy(UnknownFieldPolicy.IGNORE).build());
        assertFalse(ignored.hasErrors(), ignored.getDiagnostics().toString());
        assertTrue(ignored.getDiagnostics().isEmpty());
        assertEquals(warned.requireFlow().getId(), ignored.requireFlow().getId());
    }

    @Test
    void portableRollbackRetryAndValiditySettingsRoundTripWithoutLoss() {
        String source = PORTABLE.replace("    retry:\n", """
                    rollback:
                      action: RECONCILE_AND_REBUILD
                      monitoring_horizon: UNTIL_FLOW_TERMINAL
                      rebuild_scope: INVALIDATED_CLOSURE
                      max_recovery_cycles: 4
                      reinclusion_window: 90s
                      minimum_consistent_absence_observations: 3
                    validity:
                      window: 2h
                      resubmit_safety_margin: 60
                    retry:
                """).replace("      max_delay: 20s",
                "      max_delay: 20s\n      jitter: 0.15");
        FlowParseResult parsed = codec.parse(source, FlowParseOptions.serverDefaults());
        assertFalse(parsed.hasErrors(), parsed.getDiagnostics().toString());
        TxFlow flow = parsed.requireFlow();
        assertEquals(4, flow.getExecutionSettings().getRollbackPolicy().maxRecoveryCycles());
        assertEquals(3, flow.getExecutionSettings().getRollbackPolicy()
                .minimumConsistentAbsenceObservations());
        assertEquals(60, flow.getExecutionSettings().getValidityPolicy().resubmitSafetyMargin());
        assertEquals(0.15, flow.getExecutionSettings().getRetryPolicy().getJitterFactor());

        String encoded = codec.write(flow,
                FlowWriteOptions.of(FlowFormat.YAML, FlowSchemaVersion.V1ALPHA1));
        TxFlow reparsed = codec.parse(encoded, FlowParseOptions.serverDefaults()).requireFlow();
        assertEquals(flow.getExecutionSettings().getRollbackPolicy(),
                reparsed.getExecutionSettings().getRollbackPolicy());
        assertEquals(flow.getExecutionSettings().getValidityPolicy(),
                reparsed.getExecutionSettings().getValidityPolicy());
    }

    @Test
    void portableDocumentRejectsNegativeDurationsAtOwningPolicyBoundaries() {
        String rollback = PORTABLE.replace("    retry:\n", """
                    rollback:
                      action: WAIT_FOR_REINCLUSION
                      reinclusion_window: -1s
                    retry:
                """);
        String validity = PORTABLE.replace("    retry:\n", """
                    validity:
                      window: -1s
                    retry:
                """);
        String[] invalidDocuments = {
                PORTABLE.replace("check_interval: 1s", "check_interval: -1s"),
                PORTABLE.replace("timeout: 2m", "timeout: -2m"),
                PORTABLE.replace("initial_delay: 1s", "initial_delay: -1s"),
                PORTABLE.replace("max_delay: 20s", "max_delay: -20s"),
                rollback,
                validity
        };

        for (String source : invalidDocuments) {
            FlowParseResult result = codec.parse(source, FlowParseOptions.serverDefaults());
            assertTrue(result.hasErrors(), result.getDiagnostics().toString());
        }
    }

    private TransactionTemplate portableTransaction() {
        return codec.parse(PORTABLE, FlowParseOptions.serverDefaults())
                .requireFlow()
                .getSteps()
                .get(0)
                .getTransactionTemplate();
    }

    private TxFlow singleStepFlow(FlowStep step) {
        return TxFlow.builder("portable-write-test")
                .addStep(step)
                .build();
    }

    private TxFlow singleStepFlowWithSettings(FlowExecutionSettings settings) {
        return TxFlow.builder("portable-settings-test")
                .withExecutionSettings(settings)
                .addStep(FlowStep.builder("one")
                        .withTransactionTemplate(portableTransaction())
                        .build())
                .build();
    }

    private void assertPreset(String name, ConfirmationConfig expected) {
        FlowParseResult result = codec.parse(withConfirmationPreset(name),
                FlowParseOptions.serverDefaults());
        assertFalse(result.hasErrors(), result.getDiagnostics().toString());
        ConfirmationConfig actual = result.requireFlow().getExecutionSettings()
                .getConfirmationConfig();
        assertEquals(expected.getMinConfirmations(), actual.getMinConfirmations(), name);
        assertEquals(expected.getCheckInterval(), actual.getCheckInterval(), name);
        assertEquals(expected.getTimeout(), actual.getTimeout(), name);
        assertFalse(actual.isWaitForBackendAfterRollback(), name);
        assertEquals(5, actual.getPostRollbackWaitAttempts(), name);
        assertEquals(Duration.ZERO, actual.getPostRollbackUtxoSyncDelay(), name);
    }

    private String withConfirmationPreset(String preset) {
        int start = PORTABLE.indexOf("    confirmation:");
        int end = PORTABLE.indexOf("    retry:", start);
        return PORTABLE.substring(0, start)
                + "    confirmation:\n      preset: " + preset + "\n"
                + PORTABLE.substring(end);
    }

    private Set<String> rollbackActionsFromSchema() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
                "/schema/txflow-v1alpha1.schema.json")) {
            JsonNode values = new ObjectMapper().readTree(stream)
                    .at("/$defs/rollback/properties/action/enum");
            Set<String> actions = new HashSet<>();
            values.forEach(value -> actions.add(value.asText()));
            assertEquals(Set.of(RollbackAction.values()).stream()
                            .map(RollbackAction::name).collect(java.util.stream.Collectors.toSet()),
                    actions);
            return actions;
        }
    }

    private Set<String> confirmationPresetsFromSchema() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
                "/schema/txflow-v1alpha1.schema.json")) {
            JsonNode values = new ObjectMapper().readTree(stream)
                    .at("/$defs/confirmation/properties/preset/enum");
            Set<String> presets = new HashSet<>();
            values.forEach(value -> presets.add(value.asText()));
            return presets;
        }
    }

    private void assertTypeErrorInYamlAndJson(String yaml, String expectedPath) throws Exception {
        String json = new ObjectMapper().writeValueAsString(
                new ObjectMapper(new YAMLFactory()).readTree(yaml));
        for (UnknownFieldPolicy policy : UnknownFieldPolicy.values()) {
            FlowParseOptions options = FlowParseOptions.builder()
                    .unknownFieldPolicy(policy)
                    .build();
            for (String source : List.of(yaml, json)) {
                FlowParseResult result = codec.parse(source, options);
                assertTrue(result.hasErrors(), policy + ": " + result.getDiagnostics());
                assertTrue(result.getDiagnostics().stream().anyMatch(diagnostic ->
                                diagnostic.code().equals("TXFLOW_FIELD_TYPE")
                                        && diagnostic.documentPath().equals(expectedPath)),
                        policy + ": " + result.getDiagnostics());
            }
        }
    }

    private FlowWriteOptions portableYaml() {
        return FlowWriteOptions.of(FlowFormat.YAML, FlowSchemaVersion.V1ALPHA1);
    }
}
