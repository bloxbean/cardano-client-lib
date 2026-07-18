package com.bloxbean.cardano.client.txflow.compile;

import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.txflow.FlowStep;
import com.bloxbean.cardano.client.txflow.RetryPolicy;
import com.bloxbean.cardano.client.txflow.StepDependency;
import com.bloxbean.cardano.client.txflow.TxFlow;
import com.bloxbean.cardano.client.txflow.codec.FlowParseOptions;
import com.bloxbean.cardano.client.txflow.codec.TxFlowCodec;
import com.bloxbean.cardano.client.txflow.config.FlowExecutionSettings;
import com.bloxbean.cardano.client.txflow.config.RollbackStrategy;
import com.bloxbean.cardano.client.txflow.model.FlowBindings;
import com.bloxbean.cardano.client.txflow.model.TransactionTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TxFlowCompilerTest {
    private static final ObjectMapper JSON = new ObjectMapper();
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

    @Test
    void compilerReportsPathSpecificDiagnosticsForEveryNonPortableLegacyConstruct() {
        TransactionTemplate transaction =
                definition.getSteps().get(0).getTransactionTemplate();
        TxFlow dependency = TxFlow.builder("dependency")
                .addStep(FlowStep.builder("source").withTransactionTemplate(transaction).build())
                .addStep(FlowStep.builder("consumer").withTransactionTemplate(transaction)
                        .dependsOn(StepDependency.all("source")).build())
                .build();
        TxFlow stepRetry = oneStep("step-retry", FlowStep.builder("one")
                .withTransactionTemplate(transaction)
                .withRetryPolicy(RetryPolicy.defaults()).build());
        TxFlow flowVariables = TxFlow.builder("flow-variables")
                .addVariable("amount", 1L)
                .addStep(FlowStep.builder("one").withTransactionTemplate(transaction).build())
                .build();
        TxFlow planVariables = oneStep("plan-variables", FlowStep.builder("one")
                .withTxPlan(TxPlan.from(new Tx()).addVariable("amount", 1L)).build());
        TxFlow multipleTransactions = oneStep("multiple-transactions", FlowStep.builder("one")
                .withTxPlan(TxPlan.from(new Tx()).addTransaction(new Tx())).build());
        TxFlow legacyRollback = TxFlow.builder("legacy-rollback")
                .withExecutionSettings(FlowExecutionSettings.builder()
                        .rollbackStrategy(RollbackStrategy.NOTIFY_ONLY).build())
                .addStep(FlowStep.builder("one").withTransactionTemplate(transaction).build())
                .build();

        assertPortableDiagnostic(dependency, "TXFLOW_NON_PORTABLE_DEPENDENCY",
                "$.spec.steps[1].depends_on[0]");
        assertPortableDiagnostic(stepRetry, "TXFLOW_NON_PORTABLE_STEP_RETRY",
                "$.spec.steps[0].retry");
        assertPortableDiagnostic(flowVariables, "TXFLOW_NON_PORTABLE_FLOW_VARIABLES",
                "$.variables");
        assertPortableDiagnostic(planVariables, "TXFLOW_NON_PORTABLE_TXPLAN_VARIABLES",
                "$.spec.steps[0].transaction.variables");
        assertPortableDiagnostic(multipleTransactions,
                "TXFLOW_NON_PORTABLE_TXPLAN_CARDINALITY",
                "$.spec.steps[0].transaction");
        assertPortableDiagnostic(legacyRollback, "TXFLOW_NON_PORTABLE_ROLLBACK_STRATEGY",
                "$.spec.execution.rollback");
    }

    @Test
    void compilerDoesNotCollapseNonPortableSettingsIntoTheSameFingerprint() {
        TxFlow filteredRetry = TxFlow.builder("filtered-retry")
                .withExecutionSettings(FlowExecutionSettings.builder()
                        .retryPolicy(RetryPolicy.builder()
                                .retryOnTimeout(false).build()).build())
                .addStep(FlowStep.builder("one")
                        .withTransactionTemplate(definition.getSteps().get(0)
                                .getTransactionTemplate()).build())
                .build();

        assertPortableDiagnostic(filteredRetry, "TXFLOW_NON_PORTABLE_RETRY_FILTERS",
                "$.spec.execution.retry");
    }

    @Test
    void compilerRejectsFromWalletBeforeConversionOrFingerprinting() {
        String source = """
                api_version: txflow.cardano-client.dev/v1alpha1
                kind: TxFlow
                metadata: {name: wallet-source}
                spec:
                  steps:
                    - id: one
                      transaction:
                        tx:
                          from_wallet: WALLET
                          intents: []
                """;

        FlowCompilationResult alice = compilePortable(source.replace("WALLET", "alice"));
        FlowCompilationResult bob = compilePortable(source.replace("WALLET", "bob"));

        assertFromWalletDiagnostic(alice);
        assertFromWalletDiagnostic(bob);
        assertThrows(IllegalStateException.class, alice::requireCompiledFlow);
        assertThrows(IllegalStateException.class, bob::requireCompiledFlow);
    }

    @Test
    void compilerRejectsUnknownIntentFieldsBeforeFingerprinting() {
        String source = """
                api_version: txflow.cardano-client.dev/v1alpha1
                kind: TxFlow
                metadata: {name: intent-typo}
                spec:
                  steps:
                    - id: one
                      transaction:
                        tx:
                          from: addr_test1qpx
                          intents:
                            - type: payment
                              address: addr_test1qpy
                              amounts: [{unit: lovelace, quantity: 2000000}]
                              datum_hexx: VALUE
                """;

        FlowCompilationResult first = compilePortable(source.replace("VALUE", "aa"));
        FlowCompilationResult second = compilePortable(source.replace("VALUE", "bb"));

        assertTransactionDiagnostic(first, "TXFLOW_TRANSACTION_FIELD_UNKNOWN",
                "$.spec.steps[0].transaction.tx.intents[0].datum_hexx");
        assertTransactionDiagnostic(second, "TXFLOW_TRANSACTION_FIELD_UNKNOWN",
                "$.spec.steps[0].transaction.tx.intents[0].datum_hexx");
        assertThrows(IllegalStateException.class, first::requireCompiledFlow);
        assertThrows(IllegalStateException.class, second::requireCompiledFlow);
    }

    @Test
    void compilerRejectsUnknownEnvelopeAndNestedValueFields() {
        String envelope = """
                api_version: txflow.cardano-client.dev/v1alpha1
                kind: TxFlow
                metadata: {name: envelope-typo}
                spec:
                  steps:
                    - id: one
                      transaction:
                        trace_id: discarded
                        tx: {intents: []}
                """;
        String amount = """
                api_version: txflow.cardano-client.dev/v1alpha1
                kind: TxFlow
                metadata: {name: amount-typo}
                spec:
                  steps:
                    - id: one
                      transaction:
                        tx:
                          intents:
                            - type: payment
                              address: addr_test1qpy
                              amounts:
                                - unit: lovelace
                                  quantitty: 2000000
                """;

        assertTransactionDiagnostic(compilePortable(envelope),
                "TXFLOW_TRANSACTION_FIELD_UNKNOWN",
                "$.spec.steps[0].transaction.trace_id");
        assertTransactionDiagnostic(compilePortable(amount),
                "TXFLOW_TRANSACTION_FIELD_UNKNOWN",
                "$.spec.steps[0].transaction.tx.intents[0].amounts[0].quantitty");
    }

    @Test
    void compilerRejectsCustomUtxoDeserializerBlindSpots() {
        String hash = "a".repeat(64);
        String concreteTypo = """
                api_version: txflow.cardano-client.dev/v1alpha1
                kind: TxFlow
                metadata: {name: concrete-ref-typo}
                spec:
                  steps:
                    - id: one
                      transaction:
                        tx:
                          inputs:
                            - type: collect_from
                              refs:
                                - {tx_hash: HASH, output_indx: 0}
                """.replace("HASH", hash);
        String mixedShape = """
                api_version: txflow.cardano-client.dev/v1alpha1
                kind: TxFlow
                metadata: {name: mixed-ref}
                spec:
                  steps:
                    - id: source
                      outputs:
                        selected:
                          select: {output_index: 0}
                          expect: exactly_one
                      transaction: {tx: {intents: []}}
                    - id: spend
                      needs: [source]
                      transaction:
                        tx:
                          inputs:
                            - type: collect_from
                              refs:
                                - flow_output: {step: source, output: selected}
                                  tx_hash: HASH
                                  output_index: 0
                """.replace("HASH", hash);

        assertTransactionDiagnostic(compilePortable(concreteTypo),
                "TXFLOW_TRANSACTION_FIELD_UNKNOWN",
                "$.spec.steps[0].transaction.tx.inputs[0].refs[0].output_indx");
        assertTransactionDiagnostic(compilePortable(mixedShape),
                "TXFLOW_TRANSACTION_FIELD_UNKNOWN",
                "$.spec.steps[1].transaction.tx.inputs[0].refs[0].output_index");
    }

    @Test
    void compilerRejectsLossyQuickTxProjectionCombinations() {
        String conflictingSource = """
                api_version: txflow.cardano-client.dev/v1alpha1
                kind: TxFlow
                metadata: {name: conflicting-source}
                spec:
                  steps:
                    - id: one
                      transaction:
                        tx:
                          from: addr_test1qpx
                          from_ref: account://sender
                          intents: []
                """;
        String orphanChangeDatum = """
                api_version: txflow.cardano-client.dev/v1alpha1
                kind: TxFlow
                metadata: {name: orphan-change-datum}
                spec:
                  steps:
                    - id: one
                      transaction:
                        tx:
                          change_datum_hash: deadbeef
                          intents: []
                """;
        String duplicateRefAliases = """
                api_version: txflow.cardano-client.dev/v1alpha1
                kind: TxFlow
                metadata: {name: duplicate-ref-aliases}
                spec:
                  steps:
                    - id: one
                      transaction:
                        tx:
                          inputs:
                            - type: collect_from
                              refs: [{tx_hash: HASH, output_index: 0}]
                              utxo_refs: [{tx_hash: HASH, output_index: 1}]
                """.replace("HASH", "a".repeat(64));

        assertTransactionDiagnostic(compilePortable(conflictingSource),
                "TXFLOW_TRANSACTION_INVALID", "$.spec.steps[0].transaction.tx");
        assertTransactionDiagnostic(compilePortable(orphanChangeDatum),
                "TXFLOW_TRANSACTION_INVALID",
                "$.spec.steps[0].transaction.tx.change_address");
        assertTransactionDiagnostic(compilePortable(duplicateRefAliases),
                "TXFLOW_TRANSACTION_INVALID",
                "$.spec.steps[0].transaction.tx.inputs[0]");
    }

    @Test
    void compilerKeepsSubtypeMappingFailuresAtTheOwningIntentPath() {
        String source = """
                api_version: txflow.cardano-client.dev/v1alpha1
                kind: TxFlow
                metadata: {name: unknown-intent-type}
                spec:
                  steps:
                    - id: one
                      transaction:
                        tx:
                          intents:
                            - type: paymant
                              address: addr_test1qpy
                              amounts: [{unit: lovelace, quantity: 2000000}]
                """;

        assertTransactionDiagnostic(compilePortable(source),
                "TXFLOW_TRANSACTION_INVALID",
                "$.spec.steps[0].transaction.tx.intents[0].type");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ambiguousIntentChoices")
    void compilerRejectsEveryKnownAmbiguousIntentChoice(
            String name, String section, String intentJson, String rejectedField) {
        FlowCompilationResult result = compileIntent(section, intentJson);

        assertTransactionDiagnostic(result, "TXFLOW_TRANSACTION_INVALID",
                "$.spec.steps[0].transaction.tx." + section + "[0]." + rejectedField);
    }

    private static Stream<Arguments> ambiguousIntentChoices() {
        return Stream.of(
                choice("payment datum hex/hash", "intents",
                        "{\"type\":\"payment\",\"datum_hex\":\"01\",\"datum_hash\":\"aa\"}",
                        "datum_hash"),
                choice("payment datum hex/structured", "intents",
                        "{\"type\":\"payment\",\"datum_hex\":\"01\",\"datum\":{\"int\":1}}",
                        "datum"),
                choice("payment datum hash/structured", "intents",
                        "{\"type\":\"payment\",\"datum_hash\":\"aa\",\"datum\":{\"int\":1}}",
                        "datum"),
                choice("script collect redeemer hex/structured", "inputs",
                        "{\"type\":\"script_collect_from\",\"redeemer_hex\":\"01\",\"redeemer\":{\"int\":1}}",
                        "redeemer"),
                choice("script collect datum hex/structured", "inputs",
                        "{\"type\":\"script_collect_from\",\"datum_hex\":\"01\",\"datum\":{\"int\":1}}",
                        "datum"),
                choice("script collect fixed/filter", "inputs",
                        "{\"type\":\"script_collect_from\",\"utxo_refs\":[],\"utxo_filter\":{}}",
                        "utxo_filter"),
                choice("script collect fixed/address", "inputs",
                        "{\"type\":\"script_collect_from\",\"utxo_refs\":[],\"address\":\"addr_test1q\"}",
                        "address"),
                choice("script mint redeemer hex/structured", "intents",
                        "{\"type\":\"script_minting\",\"redeemer_hex\":\"01\",\"redeemer\":{\"int\":1}}",
                        "redeemer"),
                choice("script mint output datum hex/structured", "intents",
                        "{\"type\":\"script_minting\",\"receiver\":\"addr_test1q\",\"output_datum_hex\":\"01\",\"output_datum\":{\"int\":1}}",
                        "output_datum"),
                choice("script mint output datum without receiver", "intents",
                        "{\"type\":\"script_minting\",\"output_datum_hex\":\"01\"}",
                        "receiver"),
                choice("mint policy ref/script hex", "intents",
                        "{\"type\":\"minting\",\"policy_ref\":\"policy://nft\",\"script_hex\":\"00\"}",
                        "script_hex"),
                choice("mint policy ref/script type", "intents",
                        "{\"type\":\"minting\",\"policy_ref\":\"policy://nft\",\"script_type\":0}",
                        "script_type"),
                choice("native script ref/hash", "scripts",
                        "{\"type\":\"native_script\",\"script_ref\":\"script://one\",\"script_hash\":\"aa\"}",
                        "script_hash"),
                choice("native script ref/hex", "scripts",
                        "{\"type\":\"native_script\",\"script_ref\":\"script://one\",\"script_hex\":\"00\"}",
                        "script_hex"),
                choice("native script hash/hex", "scripts",
                        "{\"type\":\"native_script\",\"script_hash\":\"aa\",\"script_hex\":\"00\"}",
                        "script_hex"),
                choice("validator ref/hash", "scripts",
                        "{\"type\":\"validator\",\"script_ref\":\"script://one\",\"script_hash\":\"aa\"}",
                        "script_hash"),
                choice("validator ref/cbor", "scripts",
                        "{\"type\":\"validator\",\"script_ref\":\"script://one\",\"cbor_hex\":\"00\"}",
                        "cbor_hex"),
                choice("validator ref/version", "scripts",
                        "{\"type\":\"validator\",\"script_ref\":\"script://one\",\"version\":\"v2\"}",
                        "version"),
                choice("validator hash/cbor", "scripts",
                        "{\"type\":\"validator\",\"script_hash\":\"aa\",\"cbor_hex\":\"00\"}",
                        "cbor_hex"),
                choice("validator hash/version", "scripts",
                        "{\"type\":\"validator\",\"script_hash\":\"aa\",\"version\":\"v2\"}",
                        "version"),
                choice("voting delegation CBOR/type", "intents",
                        "{\"type\":\"voting_delegation\",\"drep_hex\":\"80\",\"drep_type\":\"abstain\"}",
                        "drep_type"),
                choice("voting delegation CBOR/hash", "intents",
                        "{\"type\":\"voting_delegation\",\"drep_hex\":\"80\",\"drep_hash\":\"aa\"}",
                        "drep_hash"),
                choice("hashless DRep type/hash", "intents",
                        "{\"type\":\"voting_delegation\",\"drep_type\":\"abstain\",\"drep_hash\":\"aa\"}",
                        "drep_hash"),
                choice("DRep registration anchor hash without URL", "intents",
                        "{\"type\":\"drep_registration\",\"anchor_hash\":\"aa\"}",
                        "anchor_url"),
                choice("DRep update anchor hash without URL", "intents",
                        "{\"type\":\"drep_update\",\"anchor_hash\":\"aa\"}",
                        "anchor_url"),
                choice("governance proposal anchor hash without URL", "intents",
                        "{\"type\":\"governance_proposal\",\"anchor_hash\":\"aa\"}",
                        "anchor_url"),
                choice("vote anchor hash without URL", "intents",
                        "{\"type\":\"voting\",\"anchor_hash\":\"aa\"}",
                        "anchor_url"),
                choice("pool registration inconsistent update flag", "intents",
                        "{\"type\":\"pool_registration\",\"is_update\":true}",
                        "is_update"),
                choice("pool update inconsistent update flag", "intents",
                        "{\"type\":\"pool_update\",\"is_update\":false}",
                        "is_update")
        );
    }

    private static Arguments choice(String name, String section, String json,
                                    String rejectedField) {
        return Arguments.of(name, section, json, rejectedField);
    }

    @ParameterizedTest(name = "valid single form: {0}")
    @MethodSource("validSingleIntentForms")
    void compilerAcceptsRepresentativeSingleIntentForms(
            String name, String section, String intentJson) {
        FlowCompilationResult result = compileIntent(section, intentJson);

        assertFalse(result.hasErrors(), result.getDiagnostics().toString());
        assertEquals(1, result.requireCompiledFlow().getExecutionPlan().getSteps().size());
    }

    private static Stream<Arguments> validSingleIntentForms() {
        String ref = "{\"tx_hash\":\"" + "a".repeat(64) + "\",\"output_index\":0}";
        String amounts = "\"address\":\"addr_test1q\",\"amounts\":[{\"unit\":\"lovelace\",\"quantity\":2000000}]";
        String assets = "\"assets\":[{\"name\":\"token\",\"value\":1}]";
        return Stream.of(
                Arguments.of("payment datum hex", "intents",
                        "{\"type\":\"payment\"," + amounts + ",\"datum_hex\":\"01\"}"),
                Arguments.of("payment structured datum", "intents",
                        "{\"type\":\"payment\"," + amounts + ",\"datum\":{\"int\":1}}"),
                Arguments.of("script collect hex redeemer", "inputs",
                        "{\"type\":\"script_collect_from\",\"utxo_refs\":[" + ref
                                + "],\"redeemer_hex\":\"01\"}"),
                Arguments.of("script collect structured redeemer", "inputs",
                        "{\"type\":\"script_collect_from\",\"utxo_refs\":[" + ref
                                + "],\"redeemer\":{\"int\":1}}"),
                Arguments.of("script mint hex redeemer", "intents",
                        "{\"type\":\"script_minting\",\"policyId\":\"aa\"," + assets
                                + ",\"redeemer_hex\":\"01\"}"),
                Arguments.of("script mint structured redeemer", "intents",
                        "{\"type\":\"script_minting\",\"policyId\":\"aa\"," + assets
                                + ",\"redeemer\":{\"int\":1}}"),
                Arguments.of("mint policy reference", "intents",
                        "{\"type\":\"minting\",\"policy_ref\":\"policy://nft\"," + assets + "}"),
                Arguments.of("native script hash", "scripts",
                        "{\"type\":\"native_script\",\"script_hash\":\"aa\"}"),
                Arguments.of("validator script hash", "scripts",
                        "{\"type\":\"validator\",\"script_hash\":\"aa\"}"),
                Arguments.of("voting delegation type", "intents",
                        "{\"type\":\"voting_delegation\",\"address\":\"addr_test1q\",\"drep_type\":\"abstain\"}"),
                Arguments.of("voting delegation typed hash", "intents",
                        "{\"type\":\"voting_delegation\",\"address\":\"addr_test1q\",\"drep_type\":\"key_hash\",\"drep_hash\":\"aa\"}"),
                Arguments.of("script mint output datum with receiver", "intents",
                        "{\"type\":\"script_minting\",\"policyId\":\"aa\"," + assets
                                + ",\"receiver\":\"addr_test1q\",\"output_datum_hex\":\"01\"}")
        );
    }

    @ParameterizedTest(name = "context source conflict: {0}")
    @MethodSource("ambiguousContextChoices")
    void compilerRejectsAddressAndReferenceContextSources(
            String name, String contextJson, String rejectedField) {
        FlowCompilationResult result = compileTransaction(
                "{\"tx\":{\"intents\":[]},\"context\":" + contextJson + "}");

        assertTransactionDiagnostic(result, "TXFLOW_TRANSACTION_INVALID",
                "$.spec.steps[0].transaction.context." + rejectedField);
    }

    private static Stream<Arguments> ambiguousContextChoices() {
        return Stream.of(
                Arguments.of("fee payer", "{\"fee_payer\":\"addr_test1q\",\"fee_payer_ref\":\"account://fee\"}",
                        "fee_payer_ref"),
                Arguments.of("collateral payer", "{\"collateral_payer\":\"addr_test1q\",\"collateral_payer_ref\":\"account://collateral\"}",
                        "collateral_payer_ref")
        );
    }

    @Test
    void strictMaterializationAcceptsRegularInputAndScriptIntentFamilies() {
        String hash = "a".repeat(64);
        String source = """
                api_version: txflow.cardano-client.dev/v1alpha1
                kind: TxFlow
                metadata: {name: intent-families}
                spec:
                  steps:
                    - id: one
                      transaction:
                        tx:
                          from: addr_test1qpx
                          inputs:
                            - type: reference_input
                              refs:
                                - {tx_hash: HASH, output_index: 0}
                          intents:
                            - type: payment
                              address: addr_test1qpy
                              amounts: [{unit: lovelace, quantity: 2000000}]
                          scripts:
                            - type: native_script
                              script_hash: deadbeef
                """.replace("HASH", hash);

        FlowCompilationResult result = compilePortable(source);

        assertFalse(result.hasErrors(), result.getDiagnostics().toString());
        assertEquals(1, result.requireCompiledFlow().getExecutionPlan().getSteps().size());
    }

    @Test
    void compilerRunsExistingQuickTxIntentValidation() {
        String source = """
                api_version: txflow.cardano-client.dev/v1alpha1
                kind: TxFlow
                metadata: {name: invalid-intent}
                spec:
                  steps:
                    - id: one
                      transaction:
                        tx:
                          intents:
                            - type: payment
                              amounts: [{unit: lovelace, quantity: 2000000}]
                """;

        FlowCompilationResult result = compilePortable(source);

        assertTransactionDiagnostic(result, "TXFLOW_TRANSACTION_INVALID",
                "$.spec.steps[0].transaction.tx.intents[0]");
    }

    private CompiledTxFlow compileAt(long amount) {
        return compiler.compile(FlowCompilationRequest.of(definition, FlowBindings.builder()
                .put("beneficiary", "addr_test1qpz")
                .put("amount", amount)
                .build())).requireCompiledFlow();
    }

    private TxFlow oneStep(String id, FlowStep step) {
        return TxFlow.builder(id).addStep(step).build();
    }

    private FlowCompilationResult compilePortable(String source) {
        TxFlow flow = TxFlowCodec.standard()
                .parse(source, FlowParseOptions.serverDefaults())
                .requireFlow();
        return compiler.compile(FlowCompilationRequest.of(flow, FlowBindings.empty()));
    }

    private FlowCompilationResult compileIntent(String section, String intentJson) {
        return compileTransaction("{\"tx\":{\"" + section + "\":[" + intentJson + "]}}");
    }

    private FlowCompilationResult compileTransaction(String transactionJson) {
        try {
            TransactionTemplate transaction = new TransactionTemplate(JSON.readTree(transactionJson));
            TxFlow flow = TxFlow.builder("strict-intent-choice")
                    .addStep(FlowStep.builder("one")
                            .withTransactionTemplate(transaction)
                            .build())
                    .build();
            return compiler.compile(FlowCompilationRequest.of(flow, FlowBindings.empty()));
        } catch (Exception failure) {
            throw new AssertionError("Invalid test fixture", failure);
        }
    }

    private void assertFromWalletDiagnostic(FlowCompilationResult result) {
        assertTrue(result.hasErrors(), result.getDiagnostics().toString());
        assertTrue(result.getDiagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.code().equals("TXFLOW_TRANSACTION_FROM_WALLET_UNSUPPORTED")
                                && diagnostic.documentPath().equals(
                                "$.spec.steps[0].transaction.tx.from_wallet")),
                result.getDiagnostics().toString());
        assertFalse(result.getDiagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().equals("TXFLOW_COMPILATION_FAILED")));
    }

    private void assertTransactionDiagnostic(FlowCompilationResult result,
                                             String code, String path) {
        assertTrue(result.hasErrors(), result.getDiagnostics().toString());
        assertTrue(result.getDiagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.code().equals(code)
                                && diagnostic.documentPath().equals(path)),
                result.getDiagnostics().toString());
        assertFalse(result.getDiagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().equals("TXFLOW_COMPILATION_FAILED")));
    }

    private void assertPortableDiagnostic(TxFlow flow, String code, String path) {
        FlowCompilationResult result = compiler.compile(
                FlowCompilationRequest.of(flow, FlowBindings.empty()));
        assertTrue(result.hasErrors(), result.getDiagnostics().toString());
        assertTrue(result.getDiagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.code().equals(code)
                                && diagnostic.documentPath().equals(path)),
                result.getDiagnostics().toString());
        assertFalse(result.getDiagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().equals("TXFLOW_COMPILATION_FAILED")));
    }
}
