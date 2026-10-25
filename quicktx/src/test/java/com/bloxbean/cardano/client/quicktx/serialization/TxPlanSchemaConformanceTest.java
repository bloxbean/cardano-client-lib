package com.bloxbean.cardano.client.quicktx.serialization;

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

/**
 * Validates real TxPlan documents against the published schema (ADR D7, I5, I10): documents the runtime accepts must
 * validate, and the documented mistakes must not.
 */
class TxPlanSchemaConformanceTest {

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
        int handWritten = 0;
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, String> document : corpus.entrySet()) {
            if (!TxPlanSchemaSupport.acceptedByRuntime(document.getValue())) continue;
            if (!document.getKey().startsWith("sample:")) handWritten++;
            List<String> errors = TxPlanSchemaSupport.validate(document.getValue());
            if (!errors.isEmpty()) failures.add(document.getKey() + " " + errors + "\n" + document.getValue());
        }

        assertThat(failures).as("runtime-accepted documents that the schema rejects").isEmpty();
        // Guards against a vacuous pass, e.g. the text-block scan finding nothing. Samples are checked separately.
        assertThat(handWritten).as("runtime-accepted hand-written corpus documents").isGreaterThanOrEqualTo(5);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejected")
    void documentedMistakesAreRejected(String name, String yaml) {
        assertThat(TxPlanSchemaSupport.validate(yaml)).as(name).isNotEmpty();
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
                Arguments.of("negative valid_to_slot", "context: {valid_to_slot: -1}\n" + tx("intents", "{type: payment, address: " + RECEIVER + "}")),
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
                Arguments.of("number in a string field", tx("intents", "{type: donation, current_treasury_value: 1000000000, donation_amount: 2000000}")),
                Arguments.of("variables section", "variables: {amount: 5000000, receiver: " + RECEIVER + "}\n"
                        + tx("intents", "{type: payment, address: '${receiver}', amounts: [{unit: lovelace, quantity: '${amount}'}]}")));
    }

    private static String tx(String array, String intent) {
        return "transaction:\n  - tx:\n      from: " + SENDER + "\n      " + array + ":\n        - " + intent + "\n";
    }

    private static String poolWithRelay(String relay) {
        return tx("intents", "{type: pool_registration, pool_registration: {relays: [" + relay + "]}}");
    }
}
