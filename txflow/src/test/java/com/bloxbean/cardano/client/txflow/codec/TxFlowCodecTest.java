package com.bloxbean.cardano.client.txflow.codec;

import com.bloxbean.cardano.client.txflow.TxFlow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertEquals("TXFLOW_PARSE_ERROR", result.getDiagnostics().get(0).code());
        assertTrue(result.getDiagnostics().get(0).line() != null);
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
}
