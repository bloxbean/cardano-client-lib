package com.bloxbean.cardano.client.quicktx.serialization;

import com.bloxbean.cardano.client.quicktx.intent.PaymentIntent;
import com.bloxbean.cardano.client.quicktx.intent.TxIntent;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.bloxbean.cardano.client.quicktx.serialization.IntentSamples.RECEIVER;
import static com.bloxbean.cardano.client.quicktx.serialization.IntentSamples.SENDER;
import static com.bloxbean.cardano.client.quicktx.serialization.IntentSamples.TX_HASH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Strict reading of core intents by {@link TxPlan#from(String)} and {@link TxPlanCodec}, and the
 * {@code strictIntentFields(false)} opt-out (ADR D8, I7-I9).
 */
class TxPlanStrictReadingTest {

    private static final Map<String, String> SAMPLES = IntentSamples.documents();
    private static final TxPlanCodec STRICT = TxPlanCodec.builder().build();
    private static final TxPlanCodec LENIENT = TxPlanCodec.builder().strictIntentFields(false).build();

    @Test
    void samplesCoverEveryRegisteredIntentType() {
        assertThat(SAMPLES.keySet()).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(TxIntent.class.getAnnotation(JsonSubTypes.class).value())
                        .map(JsonSubTypes.Type::name).toList());
    }

    static Stream<Arguments> samples() {
        return SAMPLES.entrySet().stream().map(e -> Arguments.of(e.getKey(), e.getValue()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("samples")
    void unknownFieldInAnyIntentIsRejectedAndNamed(String type, String yaml) {
        String typo = withField(yaml, type, "surprise_field: 1");

        assertRejected(typo, "surprise_field");
        assertThat(LENIENT.fromYaml(typo).toYaml()).as("lenient codec ignores it").isEqualTo(LENIENT.fromYaml(yaml).toYaml());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("samples")
    void everyIntentRoundTripsWhenStrict(String type, String yaml) {
        String first = TxPlan.from(yaml).toYaml();

        assertThat(TxPlan.from(first).toYaml()).as(type).isEqualTo(first);
        assertThat(STRICT.fromYaml(yaml).toYaml()).as(type).isEqualTo(LENIENT.fromYaml(yaml).toYaml());
    }

    @Test
    void misspelledFieldNoLongerDisappearsSilently() {
        String yaml = tx("intents", "{type: donation, current_treasury_value: 100, donation_amout: 5}");

        assertRejected(yaml, "donation_amout");
        assertThat(LENIENT.fromYaml(yaml).getTxs().get(0).getIntentions().get(0))
                .extracting("donationAmount").isNull();
    }

    static Stream<Arguments> coercions() {
        return Stream.of(
                Arguments.of("Java-only @JsonIgnore field", tx("intents", "{type: payment, address: " + RECEIVER + ", script: x}"), "script"),
                Arguments.of("fractional integer", tx("intents", "{type: payment, address: " + RECEIVER
                        + ", amounts: [{unit: lovelace, quantity: 2.5}]}"), "2.5"),
                Arguments.of("numeric enum index", tx("scripts", "{type: validator, role: 0, cbor_hex: '49480100002221200101', version: v2}"), "0"),
                Arguments.of("extra UTxO reference key", tx("inputs", "{type: collect_from, utxo_refs: [{tx_hash: " + TX_HASH
                        + ", output_index: 0, index: 1}]}"), "index"),
                Arguments.of("UTxO reference without output_index", tx("inputs", "{type: collect_from, utxo_refs: [{tx_hash: " + TX_HASH + "}]}"), "output_index"),
                Arguments.of("flow_output that is not an object", tx("inputs", "{type: collect_from, utxo_refs: [{flow_output: first}]}"), "flow_output"),
                Arguments.of("flow_output with extra key", tx("inputs", "{type: collect_from, utxo_refs: [{flow_output: {step: a, output: b, extra: c}}]}"), "extra"),
                Arguments.of("UTxO reference that is not an object", tx("inputs", "{type: collect_from, utxo_refs: [" + TX_HASH + "]}"), "object"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("coercions")
    void strictReadersRefuseSilentCoercionAndLenientCodecKeepsIt(String name, String yaml, String mentioned) {
        assertRejected(yaml, mentioned);
        assertThat(LENIENT.fromYaml(yaml).getTxs()).as("lenient codec still reads: %s", name).hasSize(1);
    }

    @Test
    void explicitNullsAndEmptyValuesAreRejected() {
        assertRejected(tx("inputs", "{type: reference_input, refs: null}"), "refs");
        assertRejected(tx("intents", "{type: payment, address: " + RECEIVER + ", amounts: [null]}"), "null");
        String emptyValue = "transaction:\n  - tx:\n      from: " + SENDER + "\n      intents:\n        - type: payment\n          address:\n";
        assertRejected(emptyValue, "address");
        assertRejected("context:\n  fee_payer:\n" + tx("intents", "{type: payment, address: " + RECEIVER + "}"), "fee_payer");
        assertThat(LENIENT.fromYaml(tx("inputs", "{type: reference_input, refs: null}")).getTxs()).hasSize(1);
    }

    @Test
    void emptyTransactionEntriesAndDocumentsAreRejectedNotCrashed() {
        for (TxPlanCodec codec : List.of(STRICT, LENIENT)) {
            assertThatThrownBy(() -> codec.fromYaml("transaction:\n  -\n")).isInstanceOf(RuntimeException.class)
                    .satisfies(e -> assertThat(e).hasStackTraceContaining("transaction"));
        }
        assertThatThrownBy(() -> TxPlan.from("transaction:\n  -\n")).hasStackTraceContaining("transaction");
        assertThatThrownBy(() -> TxPlan.from("")).hasStackTraceContaining("No content");
        assertThatThrownBy(() -> TxPlan.from("~\n")).hasMessageContaining("must be a mapping");
        assertThatThrownBy(() -> TxPlan.from("transaction: null\n")).hasStackTraceContaining("transaction");
        assertThat(LENIENT.fromYaml("transaction: null\n").getTxs()).isEmpty();
    }

    @Test
    void lenientCodecStillTruncatesFractionalQuantities() {
        String yaml = tx("intents", "{type: payment, address: " + RECEIVER + ", amounts: [{unit: lovelace, quantity: 2.5}]}");

        PaymentIntent payment = (PaymentIntent) LENIENT.fromYaml(yaml).getTxs().get(0).getIntentions().get(0);
        assertThat(payment.getAmounts().get(0).getQuantity()).isEqualTo(BigInteger.TWO);
    }

    @Test
    void validReferenceShapesAndCoercionsStillRead() {
        String yaml = tx("inputs", "{type: collect_from, utxo_refs: [{tx_hash: " + TX_HASH + ", output_index: '1'},"
                + " {tx_hash: " + TX_HASH + ", output_index: 2}]}")
                + "      intents:\n        - {type: payment, address: " + RECEIVER + ", amounts: [{unit: lovelace, quantity: '5000000'}]}\n"
                + "      scripts:\n        - {type: validator, role: spend, cbor_hex: 49480100002221200101, version: v2}\n";

        assertThat(TxPlan.from(yaml).getTxs().get(0).getIntentions()).hasSize(3);
        assertThat(STRICT.fromYaml(yaml).getTxs().get(0).getIntentions()).hasSize(3);
    }

    @Test
    void unknownKeysOutsideIntentsAreRejectedEvenWhenLenient() {
        String yaml = "context: {fee_payr: " + SENDER + "}\n" + tx("intents", "{type: payment, address: " + RECEIVER + "}");

        assertThatThrownBy(() -> LENIENT.fromYaml(yaml)).hasStackTraceContaining("fee_payr");
        assertThatThrownBy(() -> TxPlan.from(yaml)).hasStackTraceContaining("fee_payr");
    }

    private static void assertRejected(String yaml, String mentioned) {
        for (Function<String, TxPlan> reader : List.<Function<String, TxPlan>>of(TxPlan::from, STRICT::fromYaml)) {
            assertThatThrownBy(() -> reader.apply(yaml)).hasStackTraceContaining(mentioned);
        }
    }

    /** Adds {@code field} next to the {@code type} key of the first intent of {@code type} in a canonical sample. */
    private static String withField(String yaml, String type, String field) {
        int at = yaml.indexOf("type: " + type + "\n");
        assertThat(at).as("sample contains type %s", type).isNotNegative();
        int lineStart = yaml.lastIndexOf('\n', at) + 1;
        int lineEnd = yaml.indexOf('\n', at) + 1;
        return yaml.substring(0, lineEnd) + " ".repeat(at - lineStart) + field + "\n" + yaml.substring(lineEnd);
    }

    private static String tx(String array, String intent) {
        return "transaction:\n  - tx:\n      from: " + SENDER + "\n      " + array + ":\n        - " + intent + "\n";
    }
}
