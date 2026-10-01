package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.spec.Era;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static com.bloxbean.cardano.client.crypto.Blake2bUtil.blake2bHash224;
import static com.bloxbean.cardano.client.crypto.Blake2bUtil.blake2bHash256;
import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;
import static com.bloxbean.cardano.client.util.HexUtil.encodeHexString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The scenario matrix of ADR 0001 section 6 for {@link RawTx}, on synthetic transactions. Every hash is checked against
 * an independent computation: {@code blake2b} of the bytes the test expects to be hashed.
 */
class RawTxScenarioTest {
    private static final String BODY = "a3008001800200"; // {0: [], 1: [], 2: 0}
    private static final String VKEY_WITNESS = "82" + "5820" + "11".repeat(32) + "5840" + "22".repeat(64);
    private static final String ADDRESS = "581d6033333333333333333333333333333333333333333333333333333333";
    private static final String HASH32 = "44".repeat(32);
    private static final String NATIVE = "8200581c" + "55".repeat(28);
    private static final String PLUTUS = "4e4d01000033222220051200120011"; // a byte string holding a script
    private static final String DATUM = "d8799f182aff"; // 121([_ 42])
    private static final String EX_UNITS = "820102";

    private static RawTx tx(String body, String witnesses, String aux) {
        return RawTx.of(decodeHexString("83" + body + witnesses + aux));
    }

    private static String hex(byte[] bytes) {
        return encodeHexString(bytes);
    }

    private static String hash256(String hex) {
        return hex(blake2bHash256(decodeHexString(hex)));
    }

    // ---- shapes

    @Test
    void submissionShapes() {
        RawTx three = tx(BODY, "a0", "f6");
        assertThat(three.isValid()).isTrue();
        assertThat(three.auxData()).isEmpty();
        assertThat(three.auxDataHash()).isEmpty();
        assertThat(hex(three.txId())).isEqualTo(hash256(BODY));

        RawTx invalid = RawTx.of(decodeHexString("84" + BODY + "a0" + "f4" + "f6"));
        assertThat(invalid.isValid()).isFalse();
        assertThat(RawTx.of(decodeHexString("84" + BODY + "a0" + "f5" + "a0")).auxDataHash().map(RawTxScenarioTest::hex))
                .contains(hash256("a0"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "84" + BODY + "a0" + "01" + "f6",        // isValid is not a bool
            "83" + BODY + "a0" + "f6" + "00",        // trailing bytes
            "c1" + "83" + BODY + "a0" + "f6",        // tagged
            "a0", "85" + BODY + "a0f5f6f6", "81" + BODY,
            "83" + "80" + "a0" + "f6",               // the body is not a map
            "83" + BODY + "d9010280" + "f6",         // nor the witness set
    })
    void malformedTransactionsAreRejected(String hex) {
        assertThatThrownBy(() -> RawTx.of(decodeHexString(hex))).isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void byronTransactionsAreNotSupported() {
        assertThatThrownBy(() -> RawTx.of(decodeHexString("82" + "83808080" + "80")))
                .isInstanceOf(CborRuntimeException.class).hasMessageContaining("Byron");
    }

    // ---- records (D6)

    @ParameterizedTest
    @ValueSource(strings = {
            "83" + "a400800180020000" + "80" + "a0" + "f6",            // body {0: [], 1: [], 2: 0, 0: []}
            "83" + BODY + "a2" + "0080" + "1800" + "80" + "f6",        // witness set {0: [], 0 (non-minimal): []}
            "83" + BODY + "a0" + "d90103a2" + "00a0" + "00a0",          // aux #6.259({0: {}, 0: {}})
    })
    void duplicateRecordKeysAreRejected(String hex) {
        assertThatThrownBy(() -> RawTx.of(decodeHexString(hex)))
                .isInstanceOf(CborRuntimeException.class).hasMessageContaining("duplicate key");
    }

    @Test
    void duplicateOutputKeyIsRejectedWhenTheOutputIsRead() {
        RawTx raw = tx("a3" + "0080" + "0181" + "a3" + "00" + ADDRESS + "00" + ADDRESS + "0100" + "0200", "a0", "f6");
        assertThatThrownBy(raw::outputs).isInstanceOf(CborRuntimeException.class).hasMessageContaining("duplicate key");
    }

    @Test
    void unknownRecordKeysArePassedThrough() {
        RawTx raw = tx("a4" + "0080" + "0180" + "0200" + "186300", "a1" + "186380", "d90103a1" + "0581" + "00");
        assertThat(raw.bodyField(99)).isPresent();
        assertThat(raw.witnessField(99)).isPresent();
        assertThat(raw.auxScripts()).isEmpty();
        assertThat(raw.auxDataHash().map(RawTxScenarioTest::hex)).contains(hash256("d90103a1" + "0581" + "00"));
    }

    // ---- sets (tag 258)

    @ParameterizedTest
    @ValueSource(strings = {"", "d90102", "da00000102", "db0000000000000102"})
    void setTagAtAnyWidth(String tag) {
        RawTx raw = tx("a3" + "00" + tag + "80" + "0180" + "0200", "a2" + "00" + tag + "81" + VKEY_WITNESS + "04" + tag + "82" + DATUM + DATUM, "f6");
        assertThat(raw.vkeyWitnesses()).hasSize(1);
        assertThat(raw.vkeyWitnesses().get(0).bytes()).isEqualTo(decodeHexString(VKEY_WITNESS));
        assertThat(raw.witnessDatums()).hasSize(2); // a repeated datum is kept
    }

    @Test
    void otherTagAtASetPositionIsAnError() {
        RawTx raw = tx(BODY, "a1" + "00" + "d90103" + "81" + VKEY_WITNESS, "f6");
        assertThatThrownBy(raw::vkeyWitnesses).isInstanceOf(CborRuntimeException.class);
    }

    // ---- outputs, datums, script refs

    @Test
    void legacyAndMapOutputs() {
        String outputs = "85"
                + "82" + ADDRESS + "1903e8"                                    // [address, 1000]
                + "83" + ADDRESS + "00" + "5820" + HASH32                       // [address, 0, datum hash]
                + "a2" + "01" + "00" + "00" + ADDRESS                           // {1: 0, 0: address}, keys out of order
                + "a3" + "00" + ADDRESS + "01" + "00" + "02" + "8200" + "5820" + HASH32         // datum option [0, hash]
                + "a3" + "00" + ADDRESS + "01" + "00" + "02" + "8201" + "d818" + "45" + "a202000100"; // inline datum {2: 0, 1: 0}
        RawTx raw = tx("a3" + "0080" + "01" + outputs + "0200", "a0", "f6");
        List<RawOutput> parsed = raw.outputs();
        assertThat(parsed).hasSize(5);
        assertThat(parsed.get(0).value().asLong()).isEqualTo(1000);
        assertThat(parsed.get(0).datumHash()).isEmpty();
        assertThat(parsed.get(1).datumHash().map(RawTxScenarioTest::hex)).contains(HASH32);
        assertThat(parsed.get(2).address().bytes()).isEqualTo(decodeHexString(ADDRESS));
        assertThat(parsed.get(3).datumHash().map(RawTxScenarioTest::hex)).contains(HASH32);
        RawDatum inline = parsed.get(4).inlineDatum().orElseThrow();
        assertThat(inline.span().bytes()).isEqualTo(decodeHexString("a202000100")); // unsorted keys, as encoded
        assertThat(hex(inline.hash())).isEqualTo(hash256("a202000100"));
    }

    @Test
    void chunkedEmbeddedDatumIsJoined() {
        // #6.24 on a chunked byte string: 5f 43 d87980 ff → the datum d87980
        String output = "a3" + "00" + ADDRESS + "01" + "00" + "02" + "8201" + "d818" + "5f" + "42d879" + "4180" + "ff";
        RawDatum datum = tx("a3" + "0080" + "0181" + output + "0200", "a0", "f6").outputs().get(0).inlineDatum().orElseThrow();
        assertThat(datum.span().bytes()).isEqualTo(decodeHexString("d87980"));
        assertThat(hex(datum.hash())).isEqualTo(hash256("d87980"));
    }

    @Test
    void referenceScriptsOfEveryType() throws Exception {
        String indefiniteNative = "9f01" + "9f" + NATIVE + "ff" + "ff"; // [_ 1, [_ native]]
        String[][] refs = {{"0", NATIVE}, {"1", PLUTUS}, {"2", PLUTUS}, {"3", PLUTUS}, {"0", indefiniteNative}};
        for (String[] ref : refs) {
            int type = Integer.parseInt(ref[0]);
            String payload = "820" + type + ref[1];
            String output = "a3" + "00" + ADDRESS + "01" + "00" + "03" + "d818" + bytesHead(payload) + payload;
            RawScript script = tx("a3" + "0080" + "0181" + output + "0200", "a0", "f6").outputs().get(0).scriptRef().orElseThrow();
            assertThat(script.type()).isEqualTo(type);
            assertThat(script.span().bytes()).isEqualTo(decodeHexString(ref[1]));
            String preimage = type == 0 ? "00" + ref[1] : "0" + type + PLUTUS.substring(2);
            assertThat(hex(script.hash())).as(payload).isEqualTo(hex(blake2bHash224(decodeHexString(preimage))));
            // the model re-encodes an indefinite script as definite, so only the view hashes it as on chain
            boolean canonical = !ref[1].equals(indefiniteNative);
            assertThat(Arrays.equals(script.toScript().getScriptHash(), script.hash())).isEqualTo(canonical);
        }
    }

    private static String bytesHead(String hex) {
        int length = hex.length() / 2;
        return length < 24 ? String.format("%02x", 0x40 + length) : String.format("58%02x", length);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "a3" + "00" + ADDRESS + "01" + "00" + "02" + "8202" + "00",                 // unknown datum option
            "a3" + "00" + ADDRESS + "01" + "00" + "03" + "d818" + "44" + "8204" + "8100",      // unknown script type
            "a3" + "00" + ADDRESS + "01" + "00" + "03" + "d818" + "4c" + "821b0000000100000001" + "8100", // type 2^32 + 1
            "a1" + "00" + ADDRESS,                                                       // no value
            "81" + ADDRESS,                                                              // a legacy output of one item
    })
    void malformedOutputsAreRejected(String output) {
        RawTx raw = tx("a3" + "0080" + "0181" + output + "0200", "a0", "f6");
        assertThatThrownBy(raw::outputs).isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void collateralReturn() {
        RawTx raw = tx("a4" + "0080" + "0181" + "82" + ADDRESS + "00" + "0200" + "10" + "82" + ADDRESS + "05", "a0", "f6");
        assertThat(raw.collateralReturn().orElseThrow().value().asLong()).isEqualTo(5);
        assertThat(raw.outputs()).hasSize(1);
    }

    // ---- witness scripts and aux data

    @Test
    void witnessScriptsOfEveryType() {
        RawTx raw = tx(BODY, "a4" + "0181" + NATIVE + "0381" + PLUTUS + "0681" + PLUTUS + "07d9010281" + PLUTUS, "f6");
        List<RawScript> scripts = raw.scripts();
        assertThat(scripts).extracting(RawScript::type).containsExactly(0, 1, 2, 3);
        assertThat(hex(scripts.get(0).hash())).isEqualTo(hex(blake2bHash224(decodeHexString("00" + NATIVE))));
        for (int type = 1; type <= 3; type++)
            assertThat(hex(scripts.get(type).hash()))
                    .isEqualTo(hex(blake2bHash224(decodeHexString("0" + type + PLUTUS.substring(2)))));
    }

    @Test
    void unknownNativeScriptTypeIsRejectedWhenDecoded() {
        RawScript script = tx(BODY, "a1" + "0181" + "820600", "f6").scripts().get(0);
        assertThat(hex(script.hash())).isEqualTo(hex(blake2bHash224(decodeHexString("00820600"))));
        assertThatThrownBy(script::toScript).isInstanceOf(CborDeserializationException.class);
    }

    @Test
    void auxDataInEveryShape() {
        String metadata = "a1" + "01" + "6161";
        String allegra = "82" + metadata + "81" + NATIVE;
        String alonzo = "d90103a5" + "00" + metadata + "0181" + NATIVE + "0281" + PLUTUS + "0381" + PLUTUS + "0481" + PLUTUS;
        assertThat(tx(BODY, "a0", metadata).auxScripts()).isEmpty();
        assertThat(tx(BODY, "a0", allegra).auxScripts()).extracting(RawScript::type).containsExactly(0);
        assertThat(tx(BODY, "a0", alonzo).auxScripts()).extracting(RawScript::type).containsExactly(0, 1, 2, 3);
        for (String aux : new String[]{metadata, allegra, alonzo})
            assertThat(tx(BODY, "a0", aux).auxDataHash().map(RawTxScenarioTest::hex)).contains(hash256(aux));
        assertThatThrownBy(() -> tx(BODY, "a0", "c1" + metadata).auxScripts()).isInstanceOf(CborRuntimeException.class);
    }

    // ---- redeemers

    @Test
    void redeemersInBothForms() {
        RawTx array = tx(BODY, "a1" + "05" + "82" + "84" + "00" + "00" + DATUM + EX_UNITS + "84" + "01" + "02" + "00" + EX_UNITS, "f6");
        assertThat(array.redeemers()).extracting(RawRedeemer::tag, RawRedeemer::index).containsExactly(tuple(0, 0L), tuple(1, 2L));
        assertThat(array.redeemers().get(0).data().bytes()).isEqualTo(decodeHexString(DATUM));
        assertThat(array.redeemers().get(0).exUnits().bytes()).isEqualTo(decodeHexString(EX_UNITS));

        // Conway map form, with a repeated key: both entries kept, in encoded order
        RawTx map = tx(BODY, "a1" + "05" + "a2" + "820000" + "82" + DATUM + EX_UNITS + "820000" + "82" + "07" + EX_UNITS, "f6");
        assertThat(map.redeemers()).hasSize(2);
        assertThat(map.redeemers().get(1).data().bytes()).isEqualTo(decodeHexString("07"));
    }

    @Test
    void redeemerTagMustBeASmallInteger() {
        RawTx raw = tx(BODY, "a1" + "05" + "81" + "84" + "1b0000000100000000" + "00" + DATUM + EX_UNITS, "f6");
        assertThatThrownBy(raw::redeemers).isInstanceOf(CborRuntimeException.class);
    }

    // ---- script integrity (D3)

    @Test
    void emptyDatumsContributeNothingWhateverTheirEncoding() {
        String redeemers = "81" + "84" + "00" + "00" + DATUM + EX_UNITS;
        String views = "a0";
        String expected = hash256(redeemers + views);
        for (String datums : new String[]{null, "80", "9fff", "d9010280"}) {
            String witnesses = datums == null ? "a1" + "05" + redeemers : "a2" + "04" + datums + "05" + redeemers;
            for (Era era : new Era[]{Era.Alonzo, Era.Babbage, Era.Conway})
                assertThat(tx(BODY, witnesses, "f6").scriptDataHash(era, decodeHexString(views)).map(RawTxScenarioTest::hex))
                        .as("datums %s in %s", datums, era).contains(expected);
        }
    }

    @Test
    void datumsAreHashedAsEncodedWithTheirSetTag() {
        String datums = "d90102" + "81" + DATUM;
        String redeemers = "81" + "84" + "00" + "00" + DATUM + EX_UNITS;
        String views = "a1" + "00" + "8100"; // stands in for a real language views encoding
        RawTx raw = tx(BODY, "a2" + "04" + datums + "05" + redeemers, "f6");
        assertThat(raw.scriptDataHash(Era.Conway, decodeHexString(views)).map(RawTxScenarioTest::hex))
                .contains(hash256(redeemers + datums + views));
    }

    @Test
    void absentRedeemersFollowTheEra() {
        String datums = "81" + DATUM;
        RawTx raw = tx(BODY, "a1" + "04" + datums, "f6");
        byte[] views = decodeHexString("a0");
        assertThat(raw.scriptDataHash(Era.Alonzo, views).map(RawTxScenarioTest::hex)).contains(hash256("80" + datums + "a0"));
        assertThat(raw.scriptDataHash(Era.Babbage, views).map(RawTxScenarioTest::hex)).contains(hash256("80" + datums + "a0"));
        assertThat(raw.scriptDataHash(Era.Conway, views).map(RawTxScenarioTest::hex)).contains(hash256("a0" + datums + "a0"));
    }

    @Test
    void scriptIntegrityIsEmptyOnlyWhenEverythingIsEmpty() {
        byte[] noViews = decodeHexString("a0");
        assertThat(tx(BODY, "a0", "f6").scriptDataHash(Era.Conway, noViews)).isEmpty();
        assertThat(tx(BODY, "a2" + "0480" + "0580", "f6").scriptDataHash(Era.Babbage, noViews)).isEmpty();
        assertThat(tx(BODY, "a1" + "05a0", "f6").scriptDataHash(Era.Conway, decodeHexString("bfff"))).isEmpty();
        // language views alone give a hash
        String views = "a1" + "00" + "8100";
        assertThat(tx(BODY, "a0", "f6").scriptDataHash(Era.Babbage, decodeHexString(views)).map(RawTxScenarioTest::hex))
                .contains(hash256("80" + views));
        // empty redeemers that are present are hashed as encoded
        assertThat(tx(BODY, "a1" + "05" + "9fff", "f6").scriptDataHash(Era.Babbage, decodeHexString(views)).map(RawTxScenarioTest::hex))
                .contains(hash256("9fff" + views));
    }

    @Test
    void noScriptIntegrityBeforeAlonzo() {
        RawTx raw = tx(BODY, "a0", "f6");
        for (Era era : new Era[]{Era.Shelley, Era.Allegra, Era.Mary})
            assertThatThrownBy(() -> raw.scriptDataHash(era, decodeHexString("a0"))).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- non-canonical CBOR, big integers and constructors

    @Test
    void nonCanonicalBytesAreHashedAsEncoded() {
        String indefiniteBody = "bf" + "0080" + "0180" + "0200" + "ff";
        String nonMinimalBody = "b803" + "1800" + "9800" + "1801" + "80" + "1802" + "1800";
        for (String body : new String[]{indefiniteBody, nonMinimalBody}) {
            RawTx raw = RawTx.of(decodeHexString("9f" + body + "bf" + "ff" + "f5" + "f6" + "ff"));
            assertThat(hex(raw.txId())).isEqualTo(hash256(body));
            assertThat(raw.bodyField(2).orElseThrow().asLong()).isZero();
        }
        String aux = "b90001" + "1801" + "7f61616161ff"; // {1: "aa" in two chunks}, non-minimal count and key
        assertThat(tx(BODY, "a0", aux).auxDataHash().map(RawTxScenarioTest::hex)).contains(hash256(aux));
    }

    @Test
    void bigIntegersAndConstructorsInData() throws Exception {
        RawTx raw = tx(BODY, "a1" + "04" + "84" + "c25f41014100ff" + "d87980" + "d9050080" + "d866820080", "f6");
        List<RawDatum> datums = raw.witnessDatums();
        assertThat(((BigIntPlutusData) datums.get(0).toPlutusData()).getValue()).isEqualTo(BigInteger.valueOf(256));
        assertThat(((ConstrPlutusData) datums.get(1).toPlutusData()).getAlternative()).isZero();
        assertThat(((ConstrPlutusData) datums.get(2).toPlutusData()).getAlternative()).isEqualTo(7);
        assertThat(((ConstrPlutusData) datums.get(3).toPlutusData()).getAlternative()).isZero();
        assertThat(hex(datums.get(0).hash())).isEqualTo(hash256("c25f41014100ff"));
        assertThat(datums.get(0).span().asBigInteger()).isEqualTo(BigInteger.valueOf(256));
    }
}
