package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ADR 0003 M4: auxiliary data in all its forms, its leniency, and the tables a policy walks. */
class RawAuxDataTest {
    private static final String BODY = "a3" + "0080" + "0180" + "0200";
    private static final String METADATA = "a1" + "01" + "6161";
    private static final String NATIVE = "8200" + "581c" + "11".repeat(28);
    private static final String ALONZO = "d90103" + "a5" + "00" + METADATA + "01" + "81" + NATIVE + "02" + "8141aa"
            + "03" + "8141bb" + "04" + "8141cc";

    private static CborSpan span(String hex) {
        return CborSpan.of(HexUtil.decodeHexString(hex));
    }

    private static RawTx tx(String aux) {
        return RawTx.of(HexUtil.decodeHexString("84" + BODY + "a0" + "f5" + aux));
    }

    private static RawAuxData lenient(String aux) {
        return RawAuxData.lenient(span(aux), Era.Conway);
    }

    private static String hex(CborSpan span) {
        return HexUtil.encodeHexString(span.bytes());
    }

    private static List<String> scriptsOf(List<RawScript> scripts) {
        return scripts.stream().map(script -> script.type() + ":" + hex(script.span())).toList();
    }

    /**
     * ADR 0003 D4b: a Dijkstra field is an unknown key before Dijkstra, so absent from every accessor, {@code has}
     * included, as {@code RawTxBody} reads Dijkstra's fields. Key 5, Plutus V4, is Dijkstra's.
     */
    @Test
    void aKeyOfALaterEraIsAbsent() {
        String withV4 = "d90103" + "a2" + "00" + METADATA + "05" + "8141dd";
        for (RawAuxData conway : List.of(lenient(withV4), tx(withV4).auxData(Era.Conway).orElseThrow())) {
            assertThat(conway.has(AuxDataField.PLUTUS_V4_SCRIPTS)).isFalse();
            assertThat(conway.plutusV4Scripts()).isEmpty();
            assertThat(conway.scripts()).isEmpty();
            assertThat(conway.has(AuxDataField.METADATA)).isTrue();
        }
        RawAuxData dijkstra = RawAuxData.lenient(span(withV4), Era.Dijkstra);
        assertThat(dijkstra.has(AuxDataField.PLUTUS_V4_SCRIPTS)).isTrue();
        assertThat(scriptsOf(dijkstra.plutusV4Scripts())).containsExactly("4:41dd");
        assertThat(RawAuxData.lenient(span("d90103a10580"), Era.Dijkstra).has(AuxDataField.PLUTUS_V4_SCRIPTS))
                .isTrue();
    }

    /**
     * ADR 0003 I14, D5: the fields of earlier eras keep their tolerant reading in an era before their own, as before
     * D4b, though Haskell rejects these inputs: Plutus V2 scripts in Alonzo, Plutus V3 scripts in Babbage, and the
     * keyed form's metadata in Shelley.
     */
    @Test
    void anEarlierEraKeyIsStillReadBeforeItsEra() {
        RawAuxData alonzo = RawAuxData.lenient(span("d90103a1038141aa"), Era.Alonzo);
        assertThat(alonzo.has(AuxDataField.PLUTUS_V2_SCRIPTS)).isTrue();
        assertThat(scriptsOf(alonzo.plutusV2Scripts())).containsExactly("2:41aa");

        RawAuxData babbage = RawAuxData.lenient(span("d90103a1048141bb"), Era.Babbage);
        assertThat(babbage.has(AuxDataField.PLUTUS_V3_SCRIPTS)).isTrue();
        assertThat(scriptsOf(babbage.plutusV3Scripts())).containsExactly("3:41bb");

        RawAuxData shelley = RawAuxData.lenient(span("d90103a100a10102"), Era.Shelley);
        assertThat(shelley.has(AuxDataField.METADATA)).isTrue();
        assertThat(hex(shelley.metadata().orElseThrow())).isEqualTo("a10102");
    }

    @Test
    void everyFormOnBothReadings() {
        for (RawAuxData map : List.of(lenient(METADATA), tx(METADATA).auxData(Era.Conway).orElseThrow())) {
            assertThat(hex(map.metadata().orElseThrow())).isEqualTo(METADATA);
            assertThat(map.scripts()).isEmpty();
            assertThat(map.nativeScripts()).isEmpty();
        }
        String array = "82" + METADATA + "81" + NATIVE;
        for (RawAuxData form : List.of(lenient(array), tx(array).auxData(Era.Conway).orElseThrow())) {
            assertThat(hex(form.metadata().orElseThrow())).isEqualTo(METADATA);
            assertThat(scriptsOf(form.nativeScripts())).containsExactly("0:" + NATIVE);
            assertThat(form.plutusV1Scripts()).isEmpty();
            assertThat(form.span().bytes()).isEqualTo(HexUtil.decodeHexString(array));
        }
        for (RawAuxData form : List.of(lenient(ALONZO), tx(ALONZO).auxData(Era.Conway).orElseThrow())) {
            assertThat(hex(form.metadata().orElseThrow())).isEqualTo(METADATA);
            assertThat(scriptsOf(form.scripts())).containsExactly("0:" + NATIVE, "1:41aa", "2:41bb", "3:41cc");
            assertThat(scriptsOf(form.plutusV2Scripts())).containsExactly("2:41bb");
        }
        assertThat(lenient("d90103a0").metadata()).isEmpty();
        assertThat(lenient("d90103a0").scripts()).isEmpty();
        assertThat(tx("f6").auxData(Era.Conway)).isEmpty();
        assertThat(lenient("a0").era()).isEqualTo(Era.Conway);
    }

    @Test
    void theRawTxAccessorStays() {
        assertThat(tx(METADATA).auxScripts()).isEmpty();
        assertThat(tx("f6").auxScripts()).isEmpty();
        assertThat(scriptsOf(tx("82" + METADATA + "81" + NATIVE).auxScripts())).containsExactly("0:" + NATIVE);
        assertThat(scriptsOf(tx(ALONZO).auxScripts())).hasSize(4);
        for (String bad : List.of("00", "c1a0", "8100", "83a08000", "82a000", "d90103a10100"))
            assertThatThrownBy(() -> tx(bad).auxScripts()).as(bad).isInstanceOf(CborRuntimeException.class);
        // a repeated key of the keyed form is rejected with the view
        assertThatThrownBy(() -> tx("d90103a2" + "0080" + "0080")).isInstanceOf(CborRuntimeException.class);
    }

    /** ADR 0003 D7: what the models have always accepted and rejected. */
    @Test
    void readAsTheModelsReadIt() {
        // the tag closest to a map says what it is
        assertThat(scriptsOf(lenient("c1" + "d90103" + "a1" + "0281" + "41aa").scripts())).containsExactly("1:41aa");
        assertThat(lenient("d90103" + "c1" + METADATA).scripts()).isEmpty();
        assertThat(hex(lenient("d90103" + "c1" + METADATA).metadata().orElseThrow())).isEqualTo("d90103c1" + METADATA);
        // a plain map may repeat a label; a tagged one may not repeat any key, however it is encoded
        assertThat(lenient("a2" + "0100" + "0100").metadata()).isPresent();
        for (String bad : List.of("c1" + "a2" + "0100" + "0100", "d90103" + "a2" + "0080" + "1800" + "80",
                "d90103" + "a2" + "6161" + "00" + "6161" + "00"))
            assertThatThrownBy(() -> lenient(bad)).as(bad).isInstanceOf(CborRuntimeException.class);
        // the array form is definite, unless a tree makes it so, and holds a map and an array, tagged or not
        assertThatThrownBy(() -> lenient("9f" + "a0" + "80" + "ff")).isInstanceOf(CborRuntimeException.class);
        assertThat(lenient("c1" + "82" + "c1" + "a0" + "c1" + "80").scripts()).isEmpty();
        assertThat(lenient("82" + "bfff" + "9fff").scripts()).isEmpty();
        for (String bad : List.of("81a0", "83a08000", "8280" + "80", "82a0" + "a0", "82a0" + "00"))
            assertThatThrownBy(() -> {
                RawAuxData aux = lenient(bad);
                aux.metadata();
            }).as(bad).isInstanceOf(CborRuntimeException.class);
        // no other form
        for (String bad : List.of("00", "4100", "6161", "f6", "c100"))
            assertThatThrownBy(() -> lenient(bad)).as(bad).isInstanceOf(CborRuntimeException.class);
        // a field of the keyed form is read when it is asked for, and looks through tags
        assertThatThrownBy(() -> lenient("d90103a1" + "0000").metadata()).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> lenient("d90103a1" + "0100").nativeScripts()).isInstanceOf(CborRuntimeException.class);
        assertThat(lenient("d90103a1" + "01" + "c1" + "80").nativeScripts()).isEmpty();
        assertThat(lenient("d90103a1" + "00" + "c1" + "a0").metadata()).isPresent();
        // and an unknown key is passed over
        assertThat(lenient("d90103a1" + "0580").scripts()).isEmpty();
    }

    /** A chain follower's reading: what a decoded tree accepts, with a repeated key meaning its last value. */
    @Test
    void aTolerantReadingKeepsWhatADecodedTreeAccepts() {
        RawAuxData repeated = RawAuxData.tolerant(span("d90103" + "a2" + "0180" + "1801" + "81" + NATIVE), Era.Conway);
        assertThat(scriptsOf(repeated.nativeScripts())).containsExactly("0:" + NATIVE);
        // the array form may be indefinite and longer
        for (String array : List.of("9f" + METADATA + "81" + NATIVE + "ff", "83" + METADATA + "81" + NATIVE + "00")) {
            RawAuxData aux = RawAuxData.tolerant(span(array), Era.Conway);
            assertThat(hex(aux.metadata().orElseThrow())).isEqualTo(METADATA);
            assertThat(scriptsOf(aux.nativeScripts())).containsExactly("0:" + NATIVE);
        }
        // an item of no form has nothing in it
        for (String none : List.of("00", "6161", "f6", "c100")) {
            RawAuxData aux = RawAuxData.tolerant(span(none), Era.Conway);
            assertThat(aux.metadata()).as(none).isEmpty();
            assertThat(aux.scripts()).as(none).isEmpty();
        }
        for (String bad : List.of("80", "81" + METADATA, "82" + METADATA + "00", "d81e00"))
            assertThatThrownBy(() -> RawAuxData.tolerant(span(bad), Era.Conway)).as(bad)
                    .isInstanceOf(CborRuntimeException.class);
    }

    /** Whether the keyed form has a field, which an empty list of scripts does not say. */
    @Test
    void hasAField() {
        RawAuxData keyed = lenient("d90103" + "a2" + "00" + METADATA + "0180");
        assertThat(keyed.has(AuxDataField.METADATA)).isTrue();
        assertThat(keyed.has(AuxDataField.NATIVE_SCRIPTS)).isTrue();
        assertThat(keyed.nativeScripts()).isEmpty();
        assertThat(keyed.has(AuxDataField.PLUTUS_V1_SCRIPTS)).isFalse();
        for (String other : List.of(METADATA, "82" + METADATA + "81" + NATIVE))
            assertThat(lenient(other).has(AuxDataField.NATIVE_SCRIPTS)).as(other).isFalse();
    }

    @Test
    void anEraIsRequired() {
        assertThatThrownBy(() -> tx(METADATA).auxData(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RawAuxData.lenient(span("a0"), null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static List<String> walk(byte[] tx, Era era) {
        RecordingPolicy policy = new RecordingPolicy();
        RawTx.walk(tx, era, policy);
        return policy.events;
    }

    private static List<String> walkAux(String aux, Era era) {
        RecordingPolicy policy = new RecordingPolicy();
        RawAuxData.RECORD.walk(span(aux), era, policy);
        return policy.events;
    }

    @Test
    void aWalkEntersEveryForm() {
        assertThat(walkAux(METADATA, Era.Shelley)).containsExactly(
                "entry auxiliary_data 5:METADATA key=null value=" + METADATA + " occurrence=0",
                "check METADATA " + METADATA);
        String array = "82" + METADATA + "81" + NATIVE;
        assertThat(walkAux(array, Era.Mary)).containsExactly(
                "entry auxiliary_data 4:AUXILIARY_DATA key=null value=" + array + " occurrence=0",
                "enter auxiliary_data_array " + array,
                "entry auxiliary_data_array 0:METADATA key=null value=" + METADATA + " occurrence=0",
                "check METADATA " + METADATA,
                "entry auxiliary_data_array 1:LIST key=null value=81" + NATIVE + " occurrence=0",
                "check LIST 81" + NATIVE,
                "entry native_script 4:NATIVE_SCRIPT key=null value=" + NATIVE + " occurrence=0",
                "check NATIVE_SCRIPT " + NATIVE);
        String map = "d90103" + "a3" + "00" + METADATA + "02" + "8141aa" + "06" + "80";
        assertThat(walkAux(map, Era.Conway)).containsExactly(
                "entry auxiliary_data 6:AUXILIARY_DATA key=null value=" + map + " occurrence=0",
                "enter auxiliary_data_map " + map,
                "entry auxiliary_data_map 0:METADATA key=00 value=" + METADATA + " occurrence=0",
                "check METADATA " + METADATA,
                "entry auxiliary_data_map 2:LIST key=02 value=8141aa occurrence=0",
                "check LIST 8141aa",
                "entry plutus_script 2:PLUTUS_SCRIPT key=null value=41aa occurrence=0",
                "check PLUTUS_SCRIPT 41aa",
                "entry auxiliary_data_map null key=06 value=80 occurrence=0");
        assertThat(AuxDataField.RECORD.tag()).isEqualTo(259);
        // key 5, Plutus V4 scripts, is known from Dijkstra and unknown before (ADR 0003 D4a)
        assertThat(AuxDataField.RECORD.field(5, Era.Conway)).isNull();
        assertThat(AuxDataField.RECORD.field(5, Era.Dijkstra).validIn(Era.Dijkstra)).isTrue();
        assertThat(RawRedeemer.RECORD.tag()).isEqualTo(-1);
    }

    @Test
    void aWalkLeavesAnyOtherTagToThePolicyAndRejectsWhatItCannotEnter() {
        assertThat(walkAux("c1" + METADATA, Era.Conway)).containsExactly(
                "entry auxiliary_data 5:METADATA key=null value=c1" + METADATA + " occurrence=0",
                "check METADATA c1" + METADATA);
        assertThat(walkAux("c1" + "82" + METADATA + "80", Era.Conway)).contains("enter auxiliary_data_array c182"
                + METADATA + "80");
        for (String bad : List.of("00", "4100", "f5", "d9010300"))
            assertThatThrownBy(() -> walkAux(bad, Era.Conway)).as(bad).isInstanceOf(CborRuntimeException.class);
    }

    /** The transaction's own array, for a policy: the flag, the aux data and each form's rows. */
    @Test
    void aWalkReportsTheTransactionsOwnArray() {
        String body = "a3" + "0080" + "0180" + "0200";
        byte[] withFlag = HexUtil.decodeHexString("84" + body + "a0" + "f4" + METADATA);
        assertThat(walk(withFlag, Era.Conway)).containsSubsequence(
                "enter transaction 84" + body + "a0" + "f4" + METADATA,
                "entry transaction 0:TRANSACTION_BODY key=null value=" + body + " occurrence=0",
                "check TRANSACTION_BODY " + body,
                "enter transaction_body " + body,
                "entry transaction 1:TRANSACTION_WITNESS_SET key=null value=a0 occurrence=0",
                "check TRANSACTION_WITNESS_SET a0",
                "enter transaction_witness_set a0",
                "entry transaction 2:BOOL key=null value=f4 occurrence=0",
                "check BOOL f4",
                "entry transaction 3:OPTIONAL_AUXILIARY_DATA key=null value=" + METADATA + " occurrence=0",
                "check OPTIONAL_AUXILIARY_DATA " + METADATA,
                "check METADATA " + METADATA);
        assertThat(walk(HexUtil.decodeHexString("84" + body + "a0" + "f5" + "f6"), Era.Conway))
                .contains("check OPTIONAL_AUXILIARY_DATA f6").doesNotContain("check METADATA f6");

        byte[] withoutFlag = HexUtil.decodeHexString("83" + body + "a0" + "f6");
        assertThat(walk(withoutFlag, Era.Mary)).contains("enter pre_alonzo_transaction 83" + body + "a0" + "f6",
                "entry pre_alonzo_transaction 2:OPTIONAL_AUXILIARY_DATA key=null value=f6 occurrence=0");
    }

    /** Each form's rows say which eras have it, so an era's policy can tell (ADR 0003 D5). */
    @Test
    void aFormOutsideItsErasIsThePolicysToReject() {
        String body = "a3" + "0080" + "0180" + "0200";
        List<Boolean> valid = new java.util.ArrayList<>();
        FormatPolicy policy = new FormatPolicy() {
            @Override
            public void entry(RecordSpec record, FieldSpec field, CborSpan key, CborSpan value, int occurrence,
                              Era era) {
                if (record.name().endsWith("transaction"))
                    valid.add(field.validIn(era));
            }
        };
        RawTx.walk(HexUtil.decodeHexString("83" + body + "a0" + "f6"), Era.Conway, policy);
        assertThat(valid).containsOnly(false);
        valid.clear();
        RawTx.walk(HexUtil.decodeHexString("84" + body + "a0" + "f5" + "f6"), Era.Mary, policy);
        assertThat(valid).containsOnly(false);
        valid.clear();
        RawTx.walk(HexUtil.decodeHexString("84" + body + "a0" + "f5" + "f6"), Era.Alonzo, policy);
        assertThat(valid).containsOnly(true);
        // the walk rejects no flag of another type by itself: that is the policy's check
        RawTx.walk(HexUtil.decodeHexString("84" + body + "a0" + "00" + "f6"), Era.Conway, FormatPolicy.ACCEPT_ALL);
    }
}
