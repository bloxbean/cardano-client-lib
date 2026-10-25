package com.bloxbean.cardano.client.quicktx.serialization;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.bloxbean.cardano.client.quicktx.serialization.IntentSamples.RECEIVER;
import static com.bloxbean.cardano.client.quicktx.serialization.IntentSamples.SENDER;
import static com.bloxbean.cardano.client.quicktx.serialization.IntentSamples.TX_HASH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validates real TxPlan documents against the published schema (ADR D7, I5, I10): documents the runtime accepts must
 * validate, and the documented mistakes must not.
 */
class TxPlanSchemaConformanceTest {

    private static final int FLOOR_TEXT_BLOCKS = 9;
    private static final int FLOOR_DOCS = 4;

    @Test
    void samplesCoverEveryIntentTypeAndValidate() {
        Map<String, String> samples = IntentSamples.documents();

        assertThat(new ArrayList<>(samples.keySet())).containsExactlyInAnyOrderElementsOf(
                TxPlanSchemaGenerator.intentDefinitionNames().stream()
                        .map(name -> name.substring(TxPlanSchemaGenerator.INTENT_PREFIX.length())).toList());
        samples.forEach((type, yaml) -> {
            assertThat(yaml).as(type).contains("type: " + type);
            assertThat(TxPlanSchemaSupport.acceptedByRuntime(yaml)).as("runtime accepts the %s sample:%n%s", type, yaml).isTrue();
            assertThat(TxPlanSchemaSupport.validate(yaml)).as("%s sample:%n%s", type, yaml).isEmpty();
        });
    }

    @Test
    void everyCorpusDocumentTheRuntimeAcceptsValidates() {
        Map<String, String> corpus = TxPlanSchemaSupport.corpus();
        int textBlocks = 0;
        int docs = 0;
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, String> document : corpus.entrySet()) {
            if (!TxPlanSchemaSupport.acceptedByRuntime(document.getValue())) continue;
            if (document.getKey().contains(".mdx#")) docs++;
            else if (document.getKey().contains(".java#")) textBlocks++;
            List<String> errors = TxPlanSchemaSupport.validate(document.getValue());
            if (!errors.isEmpty()) failures.add(document.getKey() + " " + errors + "\n" + document.getValue());
        }

        assertThat(failures).as("runtime-accepted documents that the schema rejects").isEmpty();
        // Guards against a vacuous pass, e.g. a scan finding nothing; floors are the counts when they were set.
        assertThat(textBlocks).as("runtime-accepted test text blocks").isGreaterThanOrEqualTo(FLOOR_TEXT_BLOCKS);
        assertThat(docs).as("runtime-accepted docs examples").isGreaterThanOrEqualTo(FLOOR_DOCS);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejected")
    void documentedMistakesAreRejected(String name, String yaml) {
        assertThat(TxPlanSchemaSupport.validate(yaml)).as(name).isNotEmpty();
        // A mistake the schema rejects must be one the runtime rejects too (ADR I10).
        assertThat(TxPlanSchemaSupport.acceptedByRuntime(yaml)).as("the runtime rejects: %s", name).isFalse();
    }

    static Stream<Arguments> rejected() {
        return Stream.of(
                Arguments.of("misspelled intent field", tx("intents", "{type: donation, current_treasury_value: 1, donation_amout: 5}")),
                Arguments.of("unknown core intent type", tx("intents", "{type: paymant, address: " + RECEIVER + "}")),
                Arguments.of("payment in inputs", tx("inputs", "{type: payment, address: " + RECEIVER + "}")),
                Arguments.of("collect_from in scripts", tx("scripts", "{type: collect_from, utxo_refs: [{tx_hash: " + TX_HASH + ", output_index: 0}]}")),
                Arguments.of("extension intent in inputs", tx("inputs", "{type: 'fk:pay', receiver: " + RECEIVER + "}")),
                Arguments.of("extension intent in scripts", tx("scripts", "{type: 'fk:pay', receiver: " + RECEIVER + "}")),
                Arguments.of("wrong enum case", tx("scripts", "{type: validator, role: Spend, cbor_hex: '4e4d01000033222220051200120011', version: v2}")),
                Arguments.of("wrong enum value", tx("scripts", "{type: validator, role: spend, cbor_hex: '4e4d01000033222220051200120011', version: V2}")),
                Arguments.of("fractional quantity", tx("intents", "{type: payment, address: " + RECEIVER + ", amounts: [{unit: lovelace, quantity: 2.5}]}")),
                Arguments.of("unknown utxo ref field", tx("inputs", "{type: collect_from, utxo_refs: [{tx_hash: " + TX_HASH + ", output_index: 0, index: 1}]}")),
                Arguments.of("unknown relay type", poolWithRelay("{relay_type: anycast, dnsName: relay.example.com}")),
                Arguments.of("additional_signers_count above bound", "context: {additional_signers_count: 1001}\n" + tx("intents", "{type: payment, address: " + RECEIVER + "}")),
                Arguments.of("unknown context key", "context: {fee_payr: " + SENDER + "}\n" + tx("intents", "{type: payment, address: " + RECEIVER + "}")),
                Arguments.of("unknown root key", "versio: '1.0'\n" + tx("intents", "{type: payment, address: " + RECEIVER + "}")),
                Arguments.of("scriptTx entry", "transaction:\n  - scriptTx: {}\n"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("accepted")
    void placeholdersAndRuntimeAcceptedPlacementsValidate(String name, String yaml) {
        assertThat(TxPlanSchemaSupport.validate(yaml)).as(name).isEmpty();
    }

    static Stream<Arguments> accepted() {
        return Stream.of(
                Arguments.of("integer placeholder", tx("intents", "{type: payment, address: " + RECEIVER + ", amounts: [{unit: lovelace, quantity: '${amount}'}]}")),
                Arguments.of("integer as string", tx("intents", "{type: payment, address: " + RECEIVER + ", amounts: [{unit: lovelace, quantity: '5000000'}]}")),
                Arguments.of("TxFlow binding", tx("intents", "{type: payment, address: '${{ inputs.receiver }}', amounts: [{unit: lovelace, quantity: '${{ inputs.amount }}'}]}")),
                Arguments.of("whole-array placeholder", tx("intents", "{type: payment, address: " + RECEIVER + ", amounts: '${amounts}'}")),
                Arguments.of("enum placeholder", tx("scripts", "{type: validator, role: '${role}', cbor_hex: '4e4d01000033222220051200120011', version: v2}")),
                Arguments.of("boolean placeholder", tx("intents", "{type: pool_registration, is_update: '${update}'}")),
                Arguments.of("placeholder discriminator", tx("intents", "{type: '${kind}', anything: 1}")),
                Arguments.of("extension intent in intents", tx("intents", "{type: 'fk:pay', receiver: " + RECEIVER + ", lovelace: 3000000}")),
                Arguments.of("input intent under intents", tx("intents", "{type: collect_from, refs: [{tx_hash: " + TX_HASH + ", output_index: '0'}]}")),
                Arguments.of("flow output reference", tx("inputs", "{type: collect_from, utxo_refs: [{flow_output: {step: first, output: change}}]}")),
                Arguments.of("context placeholders", "context: {additional_signers_count: '${count}', valid_to_slot: '${slot}', valid_from_slot: '100'}\n"
                        + tx("intents", "{type: payment, address: " + RECEIVER + "}")),
                Arguments.of("numeric version", "version: 1.0\n" + tx("intents", "{type: payment, address: " + RECEIVER + "}")),
                Arguments.of("empty transaction entry (skipped by the runtime)", "transaction:\n  - {}\n"),
                Arguments.of("extensions from a variable", "variables: {e: {}}\nextensions: '${e}'\ntransaction: []\n"),
                Arguments.of("number in a string field", tx("intents", "{type: donation, current_treasury_value: 1000000000, donation_amount: 2000000}")),
                Arguments.of("variables section", "variables: {amount: 5000000, receiver: " + RECEIVER + "}\n"
                        + tx("intents", "{type: payment, address: '${receiver}', amounts: [{unit: lovelace, quantity: '${amount}'}]}")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("runtimeAcceptedTemplates")
    void templatesTheRuntimeAcceptsValidate(String name, String yaml) {
        assertThat(TxPlanSchemaSupport.acceptedByRuntime(yaml)).as("a strict reader accepts: %s", name).isTrue();
        assertThat(TxPlanSchemaSupport.validate(yaml)).as(name).isEmpty();
    }

    static Stream<Arguments> runtimeAcceptedTemplates() {
        String donation = "{type: donation, current_treasury_value: 100, donation_amount: 5}";
        return Stream.of(
                Arguments.of("whole intents array from a variable", "variables:\n  xs: [" + donation + "]\n"
                        + "transaction:\n  - tx:\n      from: " + SENDER + "\n      intents: '${xs}'\n"),
                Arguments.of("one intent from a variable", "variables:\n  d: " + donation + "\n"
                        + "transaction:\n  - tx:\n      from: " + SENDER + "\n      intents:\n        - '${d}'\n"),
                Arguments.of("whole context from a variable", "variables:\n  ctx: {fee_payer: " + SENDER + "}\ncontext: '${ctx}'\n"
                        + tx("intents", donation)),
                Arguments.of("relay discriminator from a variable", "variables:\n  kind: single_host_name\n"
                        + tx("intents", "{type: pool_registration, pool_registration: {relays: [{relay_type: '${kind}', dnsName: relay.example.com}]}}")),
                Arguments.of("signer entry from a variable", "variables:\n  s: {ref: 'account://alice'}\ncontext: {signers: ['${s}']}\n"
                        + tx("intents", donation)),
                Arguments.of("digit-only hex written unquoted", tx("intents", "{type: pool_registration, pool_registration: {operator: 1234}}")),
                Arguments.of("control characters Jackson trims", tx("intents", "{type: payment, address: " + RECEIVER
                        + ", amounts: [{unit: lovelace, quantity: \"\\x015\"}]}")),
                Arguments.of("enum names with surrounding spaces", tx("scripts",
                        "{type: validator, role: ' spend ', cbor_hex: '49480100002221200101', version: ' v2 '}")));
    }

    @Test
    void unwrappedPropertiesFailGeneration() {
        assertThatThrownBy(() -> TxPlanSchemaGenerator.describe(WithUnwrapped.class))
                .hasMessageContaining("JsonUnwrapped");
    }

    static Stream<Arguments> unsupportedConstructs() {
        return Stream.of(
                Arguments.of(WithEnumAlias.class, "aliases"),
                Arguments.of(WithSingleValueArray.class, "ACCEPT_SINGLE_VALUE_AS_ARRAY"),
                Arguments.of(WithStringCreator.class, "scalar"),
                Arguments.of(WithContentDeserializer.class, "content or key"),
                Arguments.of(WithDefaultImpl.class, "defaultImpl"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unsupportedConstructs")
    void unsupportedConstructsFailGenerationInsteadOfDrifting(Class<?> type, String mentioned) {
        assertThatThrownBy(() -> TxPlanSchemaGenerator.describe(type)).hasStackTraceContaining(mentioned);
    }

    @Test
    void subtypesNamedByJsonTypeNameAreGenerated() throws Exception {
        String schema = TxPlanSchemaGenerator.describe(WithShape.class).toString();

        assertThat(schema).contains("model.Circle");
    }

    enum Mode {
        @JsonAlias("quick") FAST, SLOW
    }

    static class WithEnumAlias {
        public Mode mode;
    }

    static class WithSingleValueArray {
        @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        public List<String> values;
    }

    static class Wrapped {
        private final String value;

        Wrapped(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    static class WithStringCreator {
        public Wrapped wrapped;
    }

    static class WithContentDeserializer {
        @JsonDeserialize(contentUsing = StringDeserializer.class)
        public List<String> values;
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind", defaultImpl = Circle.class)
    @JsonSubTypes(@JsonSubTypes.Type(Circle.class))
    interface ShapeWithDefault {
    }

    static class WithDefaultImpl {
        public ShapeWithDefault shape;
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes(@JsonSubTypes.Type(Circle.class))
    interface Shape {
    }

    @JsonTypeName("circle")
    static class Circle implements Shape, ShapeWithDefault {
        public int radius;
    }

    static class WithShape {
        public Shape shape;
    }

    static class WithUnwrapped {
        public String unit;
        @JsonUnwrapped
        public Extra extra;
    }

    static class Extra {
        public String memo;
    }

    private static String tx(String array, String intent) {
        return "transaction:\n  - tx:\n      from: " + SENDER + "\n      " + array + ":\n        - " + intent + "\n";
    }

    private static String poolWithRelay(String relay) {
        return tx("intents", "{type: pool_registration, pool_registration: {relays: [" + relay + "]}}");
    }
}
