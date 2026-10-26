package com.bloxbean.cardano.client.quicktx.serialization;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.intent.PaymentIntent;
import com.bloxbean.cardano.client.quicktx.intent.TxIntent;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
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
import static org.assertj.core.api.Assertions.catchThrowable;

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

    static Stream<Arguments> variants() {
        return IntentSamples.variants().entrySet().stream().map(e -> Arguments.of(e.getKey(), e.getValue()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("variants")
    void variantsReadStrictlyAndRoundTrip(String name, String yaml) {
        String first = STRICT.fromYaml(yaml).toYaml();

        assertThat(STRICT.fromYaml(first).toYaml()).as(name).isEqualTo(first);
        assertThat(TxPlan.from(yaml).toYaml()).as(name).isEqualTo(first);
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
                Arguments.of("numeric enum index", tx("scripts", "{type: validator, role: 0, cbor_hex: '49480100002221200101', version: v2}"), "RedeemerTag"),
                Arguments.of("extra UTxO reference key", tx("inputs", "{type: collect_from, utxo_refs: [{tx_hash: " + TX_HASH
                        + ", output_index: 0, index: 1}]}"), "index"),
                Arguments.of("UTxO reference without output_index", tx("inputs", "{type: collect_from, utxo_refs: [{tx_hash: " + TX_HASH + "}]}"), "output_index"),
                Arguments.of("flow_output that is not an object", tx("inputs", "{type: collect_from, utxo_refs: [{flow_output: first}]}"), "flow_output"),
                Arguments.of("flow_output with extra key", tx("inputs", "{type: collect_from, utxo_refs: [{flow_output: {step: a, output: b, extra: c}}]}"), "extra"),
                Arguments.of("UTxO reference that is not an object", tx("inputs", "{type: collect_from, utxo_refs: [" + TX_HASH + "]}"), "object"),
                Arguments.of("empty string for a boolean", tx("intents", "{type: pool_update, update: ''}"), "update"),
                Arguments.of("number for a boolean", tx("intents", "{type: pool_update, is_update: 1}"), "is_update"),
                Arguments.of("output_index beyond int range", tx("inputs", "{type: collect_from, utxo_refs: [{tx_hash: " + TX_HASH
                        + ", output_index: 4294967296}]}"), "output_index"),
                Arguments.of("negative output_index", tx("inputs", "{type: collect_from, utxo_refs: [{tx_hash: " + TX_HASH
                        + ", output_index: -1}]}"), "output_index"),
                Arguments.of("non-numeric output_index", tx("inputs", "{type: collect_from, utxo_refs: [{tx_hash: " + TX_HASH
                        + ", output_index: garbage}]}"), "output_index"),
                Arguments.of("output_index placeholder without a name", tx("inputs", "{type: collect_from, utxo_refs: [{tx_hash: " + TX_HASH
                        + ", output_index: '${}'}]}"), "output_index"),
                Arguments.of("empty output_index", tx("inputs", "{type: collect_from, utxo_refs: [{tx_hash: " + TX_HASH
                        + ", output_index: ''}]}"), "output_index"),
                Arguments.of("the string null for a primitive boolean", tx("intents", "{type: pool_update, is_update: 'null'}"), "is_update"),
                Arguments.of("the string null for a primitive int", tx("intents", "{type: pool_retirement, pool_id: "
                        + IntentSamples.POOL_ID + ", retirement_epoch: 'null'}"), "retirement_epoch"),
                Arguments.of("YAML alias", "transaction:\n  - tx:\n      from: &r " + SENDER + "\n      change_address: *r\n", "alias"),
                Arguments.of("YAML alias in variables", "variables:\n  original: &receiver " + RECEIVER + "\n  copy: *receiver\n"
                        + tx("intents", "{type: payment, address: '${copy}'}"), "alias"),
                Arguments.of("octal variable", "variables: {n: 010}\n" + tx("intents", "{type: payment, address: " + RECEIVER
                        + ", amounts: [{unit: lovelace, quantity: '${n}'}]}"), "010"),
                Arguments.of("hexadecimal variable", "variables: {n: 0x10}\n" + tx("intents", "{type: payment, address: " + RECEIVER
                        + ", amounts: [{unit: lovelace, quantity: '${n}'}]}"), "0x10"),
                Arguments.of("decimal variable with underscores", "variables: {r: 1_0.5}\n" + tx("intents", "{type: payment, address: "
                        + RECEIVER + "}"), "1_0.5"),
                Arguments.of("octal-looking quantity", tx("intents", "{type: payment, address: " + RECEIVER
                        + ", amounts: [{unit: lovelace, quantity: 010}]}"), "010"),
                Arguments.of("hex string field with a leading zero, unquoted", tx("intents", "{type: payment, address: " + RECEIVER
                        + ", datum_hex: 0102}"), "0102"),
                Arguments.of("hexadecimal integer", tx("intents", "{type: payment, address: " + RECEIVER
                        + ", amounts: [{unit: lovelace, quantity: 0x10}]}"), "0x10"),
                Arguments.of("integer with underscores", tx("intents", "{type: payment, address: " + RECEIVER
                        + ", amounts: [{unit: lovelace, quantity: 1_000}]}"), "1_000"),
                Arguments.of("pool type contradicting its update flag", tx("intents", "{type: pool_update, is_update: false}"), "contradicts"),
                Arguments.of("canonical name and alias together", tx("inputs", "{type: collect_from, utxo_refs: [{tx_hash: " + TX_HASH
                        + ", output_index: 0}], refs: [{tx_hash: " + TX_HASH + ", output_index: 1}]}"), "refs"),
                Arguments.of("empty string for an integer", tx("intents", "{type: payment, address: " + RECEIVER
                        + ", amounts: [{unit: lovelace, quantity: ''}]}"), "quantity"));
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
        for (Function<String, TxPlan> reader : List.<Function<String, TxPlan>>of(TxPlan::from, STRICT::fromYaml, LENIENT::fromYaml)) {
            assertThatThrownBy(() -> reader.apply("transaction: [{tx: {intents: [null]}}]"))
                    .hasStackTraceContaining("intents").isNotInstanceOf(NullPointerException.class);
        }
        assertThatThrownBy(() -> TxPlan.from("transaction: [{scriptTx: {}}]"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("scriptTx");
        assertThatThrownBy(() -> TxPlan.from("")).hasMessageContaining("must be a mapping");
        assertThatThrownBy(() -> TxPlan.from("~\n")).hasMessageContaining("must be a mapping");
        assertThatThrownBy(() -> TxPlan.from("transaction: null\n")).hasStackTraceContaining("transaction");
        assertThat(LENIENT.fromYaml("transaction: null\n").getTxs()).isEmpty();
    }

    @Test
    void booleanPlaceholdersStillResolve() {
        String yaml = "variables: {flag: true}\n" + tx("intents", "{type: pool_update, is_update: '${flag}'}");

        // The codec substitutes a typed value; TxPlan.from substitutes text, so it reads the string "true".
        assertThat(STRICT.fromYaml(yaml).getTxs()).hasSize(1);
        assertThat(TxPlan.from(yaml).getTxs()).hasSize(1);
    }

    @Test
    void digitOnlyReferenceTextRoundTrips() {
        String hash = "1234567890".repeat(6) + "1234";
        String yaml = tx("inputs", "{type: collect_from, utxo_refs: [{flow_output: {step: '1', output: '0'}}, "
                + "{flow_output: {step: '2', output: '0.5'}}, {tx_hash: '" + hash + "', output_index: 0}]}");

        String first = TxPlan.from(yaml).toYaml();

        assertThat(first).as("the writer leaves digit-only text unquoted").contains("step: 1");
        assertThat(TxPlan.from(first).toYaml()).isEqualTo(first);
        assertThat(STRICT.fromYaml(first).toYaml()).isEqualTo(first);
    }

    @Test
    void malformedPlutusDataIsRejectedNotCrashed() {
        for (String shape : List.of("{constructor: 0, fields: 5}", "{list: 5}", "{map: 5}", "{map: [null]}",
                "{map: [{k: {int: 1}}]}", "{list: [5]}", "{constructor: 0, fields: [null]}", "{constructor: 0}",
                "{constructor: abc, fields: []}", "{int: 1, bytes: ab}", "{constructor: -1, fields: []}",
                "{constructor: 123456789012345678901234567890, fields: []}")) {
            String yaml = tx("inputs", "{type: script_collect_from, utxo_refs: [{tx_hash: " + TX_HASH + ", output_index: 0}], redeemer: " + shape + "}");
            for (Function<String, TxPlan> reader : List.<Function<String, TxPlan>>of(TxPlan::from, STRICT::fromYaml, LENIENT::fromYaml)) {
                assertThatThrownBy(() -> reader.apply(yaml)).as(shape)
                        .hasStackTraceContaining("Plutus data")
                        .isNotInstanceOfAny(ClassCastException.class, NullPointerException.class);
            }
        }
    }

    @Test
    void stringsThatLookLikeNumbersSurviveTheCanonicalWriter() {
        for (String value : List.of("0xabc", ".inf", "1_000", "1e3", "+5", "0b101", "010")) {
            String yaml = TxPlan.from(new Tx().from(SENDER).payToAddress(value, Amount.ada(1))).toYaml();

            PaymentIntent payment = (PaymentIntent) TxPlan.from(yaml).getTxs().get(0).getIntentions().get(0);
            assertThat(payment.getAddress()).as("%s written as:%n%s", value, yaml).isEqualTo(value);
        }
    }

    @Test
    void variablesThatReferToPlaceholdersAreRejected() {
        String chain = "variables: {a: " + RECEIVER + ", b: '${a}', c: '${b}'}\n" + tx("intents", "{type: payment, address: '${c}'}");

        // TxPlan.from substitutes text, so a chain would resolve one more level on every read.
        assertThatThrownBy(() -> TxPlan.from(chain)).hasMessageContaining("literal");
        String oneLevel = "variables: {a: " + RECEIVER + ", b: '${a}'}\n" + tx("intents", "{type: payment, address: '${b}'}");
        assertThatThrownBy(() -> TxPlan.from(oneLevel)).hasMessageContaining("literal");
        // TxPlanCodec replaces a variable literally and then refuses a field left holding the text ${a}.
        assertThatThrownBy(() -> STRICT.fromYaml(oneLevel)).hasMessageContaining("placeholder");
        // A literal variable used directly reads back the same every time.
        String literal = "variables: {a: " + RECEIVER + "}\n" + tx("intents", "{type: payment, address: '${a}'}");
        assertThat(TxPlan.from(literal).toYaml()).isEqualTo(TxPlan.from(TxPlan.from(literal).toYaml()).toYaml());
    }

    @Test
    void decimalVariablesKeepTheirWrittenScaleAndRereadAfterWriting() {
        String yaml = "variables: {r: 1.50}\n" + tx("intents", "{type: payment, address: " + RECEIVER + "}");
        assertThat(TxPlan.from(yaml).getTxs()).hasSize(1);
        assertThat(STRICT.fromYaml(yaml).getTxs()).hasSize(1);

        // A runtime BigDecimal kept on the plan is written with its scale and must read back unchanged.
        String written = STRICT.toYaml(STRICT.fromYaml(tx("intents", "{type: payment, address: " + RECEIVER + "}"),
                Map.of("r", new BigDecimal("1.50"))));
        assertThat(STRICT.toYaml(STRICT.readLiteral(written))).isEqualTo(written);
    }

    @Test
    void placeholderKeysAreRejectedByEveryStrictReader() {
        String rootKey = "variables: {field: context}\n'${field}': {fee_payer: " + SENDER + "}\ntransaction: []\n";
        String intentKey = "variables: {field: donation_amount}\n"
                + tx("intents", "{type: donation, current_treasury_value: 10, '${field}': 5}");
        String txKey = "variables: {k: change_address}\ntransaction:\n  - tx:\n      from: " + SENDER
                + "\n      '${k}': " + SENDER + "\n";

        // TxPlan.from would rename the key by text substitution; the codec and the schema never substitute keys.
        assertRejected(rootKey, "${field}");
        assertRejected(intentKey, "${field}");
        assertRejected(txKey, "${k}");
    }

    @Test
    void decimalVariablesPreservePrecisionAndScale() {
        String decimal = "1.23456789012345678900";
        String yaml = "variables: {value: " + decimal + "}\ntransaction: []\n";
        for (Function<String, TxPlan> reader : List.<Function<String, TxPlan>>of(TxPlan::from, STRICT::fromYaml, LENIENT::fromYaml)) {
            TxPlan plan = reader.apply(yaml);
            assertThat(plan.getVariables().get("value")).isEqualTo(new BigDecimal(decimal));
        }

        TxPlan runtime = STRICT.fromYaml("transaction: []\n", Map.of("value", new BigDecimal(decimal)));
        String snapshot = STRICT.toYaml(runtime);
        assertThat(STRICT.readLiteral(snapshot).getVariables().get("value")).isEqualTo(new BigDecimal(decimal));
        assertThat(STRICT.toYaml(STRICT.readLiteral(snapshot))).isEqualTo(snapshot);
    }

    @Test
    void constructorIndexGivenAsDecimalTextStillReads() {
        String quoted = "variables: {c: 1}\n" + tx("inputs", "{type: script_collect_from, utxo_refs: [{tx_hash: " + TX_HASH
                + ", output_index: 0}], redeemer: {constructor: '${c}', fields: []}}");
        String literal = tx("inputs", "{type: script_collect_from, utxo_refs: [{tx_hash: " + TX_HASH
                + ", output_index: 0}], redeemer: {constructor: ' 1', fields: []}}");

        assertThat(TxPlan.from(quoted).getTxs()).hasSize(1);
        assertThat(STRICT.fromYaml(literal).getTxs()).hasSize(1);
        assertThat(LENIENT.fromYaml(literal).getTxs()).hasSize(1);
    }

    @Test
    void plutusMapEntriesWithExtraKeysAreRejected() {
        String yaml = tx("inputs", "{type: script_collect_from, utxo_refs: [{tx_hash: " + TX_HASH
                + ", output_index: 0}], redeemer: {map: [{k: {int: 1}, v: {int: 2}, typo: 3}]}}");
        for (Function<String, TxPlan> reader : List.<Function<String, TxPlan>>of(TxPlan::from, STRICT::fromYaml, LENIENT::fromYaml)) {
            assertThatThrownBy(() -> reader.apply(yaml)).hasStackTraceContaining("must be {k, v}");
        }
    }

    @Test
    void runtimeVariablesRoundTripThroughTheCodecSnapshotReader() {
        TxPlan decimal = STRICT.fromYaml("transaction: []\n", Map.of("rate", new BigDecimal("1.50")));
        assertThat(STRICT.fromYaml(decimal.toYaml()).getVariables()).containsKey("rate");

        // The codec replaces a variable literally, so a value that looks like a template is data; snapshots use readLiteral.
        TxPlan template = STRICT.fromYaml("transaction: []\n", Map.of("unused", "${later}"));
        assertThat(STRICT.readLiteral(template.toYaml()).getVariables()).containsEntry("unused", "${later}");
    }

    @Test
    void variableNamesThatBreakTextualSubstitutionAreRejected() {
        String yaml = "variables:\n  'n${': " + RECEIVER + "\n" + tx("intents", "{type: payment, address: " + RECEIVER + "}");

        assertThatThrownBy(() -> TxPlan.from(yaml)).hasMessageContaining("Variable name");
    }

    @Test
    void anIntentMissingItsAddressIsNotACrashWhenVariablesExist() {
        String yaml = "variables: {v: " + RECEIVER + "}\n" + tx("intents", "{type: stake_registration}");

        for (Function<String, TxPlan> reader : List.<Function<String, TxPlan>>of(TxPlan::from, STRICT::fromYaml, LENIENT::fromYaml)) {
            assertThat(reader.apply(yaml).getTxs()).hasSize(1);
        }
    }

    @Test
    void scriptHexOfTheWrongCborShapeIsRejectedNotCastFailed() {
        String yaml = tx("scripts", "{type: native_script, script_hex: '40'}");

        for (Function<String, TxPlan> reader : List.<Function<String, TxPlan>>of(TxPlan::from, STRICT::fromYaml, LENIENT::fromYaml)) {
            Throwable thrown = catchThrowable(() -> reader.apply(yaml));
            assertThat(thrown).hasStackTraceContaining("must be CBOR Array");
            for (Throwable t = thrown; t != null; t = t.getCause()) assertThat(t).isNotInstanceOf(ClassCastException.class);
        }
    }

    @Test
    void aFileHoldsOneYamlDocument() {
        String two = tx("intents", "{type: payment, address: " + RECEIVER + "}") + "---\n" + tx("intents", "{type: payment, address: " + SENDER + "}");

        assertRejected(two, "one YAML document");
    }

    @Test
    void scriptTxNextToTxIsRejectedNotDropped() {
        String yaml = "transaction: [{tx: {from: " + SENDER + ", intents: []}, scriptTx: {}}]";
        for (Function<String, TxPlan> reader : List.<Function<String, TxPlan>>of(TxPlan::from, STRICT::fromYaml, LENIENT::fromYaml)) {
            assertThatThrownBy(() -> reader.apply(yaml)).hasStackTraceContaining("scriptTx");
        }
    }

    @Test
    void stringsThatCannotBePlainKeepTheirQuotingStyle() {
        // The canonical bytes for these did not change: only values YAML would read as a different number are affected.
        for (String value : List.of(" padded ", "@x", "*x", "'q'")) {
            String yaml = TxPlan.from(new Tx().from(SENDER).payToAddress(value, Amount.ada(1))).toYaml();
            assertThat(yaml).as(value).doesNotContain("address: \"");
            assertThat(((PaymentIntent) TxPlan.from(yaml).getTxs().get(0).getIntentions().get(0)).getAddress()).isEqualTo(value);
        }
    }

    @Test
    void lenientCodecStillReadsNonCanonicalYaml() {
        assertThat(LENIENT.fromYaml(tx("intents", "{type: payment, address: " + RECEIVER
                + ", amounts: [{unit: lovelace, quantity: 010}]}")).getTxs()).hasSize(1);
    }

    @Test
    void unresolvedMetadataPlaceholderIsWrittenBack() {
        String yaml = tx("intents", "{type: metadata, metadata: '${note}'}");

        String first = TxPlan.from(yaml).toYaml();

        assertThat(first).contains("${note}");
        assertThat(TxPlan.from(first).toYaml()).isEqualTo(first);
    }

    @Test
    void lenientCodecStillTruncatesFractionalQuantities() {
        String yaml = tx("intents", "{type: payment, address: " + RECEIVER + ", amounts: [{unit: lovelace, quantity: 2.5}]}");

        PaymentIntent payment = (PaymentIntent) LENIENT.fromYaml(yaml).getTxs().get(0).getIntentions().get(0);
        assertThat(payment.getAmounts().get(0).getQuantity()).isEqualTo(BigInteger.TWO);
    }

    @Test
    void validReferenceShapesAndCoercionsStillRead() {
        String yaml = "variables: {index: 3}\n" + tx("inputs", "{type: collect_from, utxo_refs: [{tx_hash: " + TX_HASH + ", output_index: '1'},"
                + " {tx_hash: " + TX_HASH + ", output_index: 2}, {tx_hash: " + TX_HASH + ", output_index: '${index}'}]}")
                + "      intents:\n        - {type: payment, address: " + RECEIVER + ", amounts: [{unit: lovelace, quantity: '5000000'}]}\n"
                + "      scripts:\n        - {type: validator, role: spend, cbor_hex: 49480100002221200101, version: v2}\n";

        assertThat(TxPlan.from(yaml).getTxs().get(0).getIntentions()).hasSize(3);
        assertThat(TxPlan.from(yaml.replace("utxo_refs:", "refs:")).getTxs().get(0).getIntentions()).hasSize(3);
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
            // The message itself names the problem (ADR I7), not only a nested cause.
            assertThatThrownBy(() -> reader.apply(yaml))
                    .hasMessageContaining(mentioned)
                    .isNotInstanceOfAny(NullPointerException.class, ClassCastException.class);
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
