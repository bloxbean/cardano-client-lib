package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.Era;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ADR 0003 M4: the typed views of a witness set, their leniency, and the tables a policy walks. */
class RawWitnessSetTest {
    private static final String BODY = "a3" + "0080" + "0180" + "0200";
    private static final String VKEY = "4101";
    private static final String SIG = "4102";
    private static final String VKEY_WITNESS = "82" + VKEY + SIG;
    private static final String BOOTSTRAP = "84" + VKEY + SIG + "4103" + "4104";
    private static final String NATIVE = "8200" + "581c" + "11".repeat(28);
    private static final String UNITS = "820506";

    private static CborSpan span(String hex) {
        return CborSpan.of(HexUtil.decodeHexString(hex));
    }

    private static RawTx tx(String witnesses) {
        return RawTx.of(HexUtil.decodeHexString("84" + BODY + witnesses + "f5" + "f6"));
    }

    private static RawWitnessSet lenient(String witnesses) {
        return RawWitnessSet.lenient(span(witnesses), Era.Conway);
    }

    private static String hex(CborSpan span) {
        return HexUtil.encodeHexString(span.bytes());
    }

    private static List<String> walk(String witnesses, Era era) {
        RecordingPolicy policy = new RecordingPolicy();
        WitnessSetField.RECORD.walk(span(witnesses), era, policy);
        return policy.events;
    }

    @Test
    void readsEveryField() {
        String witnesses = "a8" + "0081" + VKEY_WITNESS + "0181" + NATIVE + "0281" + BOOTSTRAP + "038141aa" + "048100"
                + "058184" + "00" + "01" + "02" + UNITS + "068141bb" + "078141cc";
        RawWitnessSet set = tx(witnesses).witnessSet(Era.Conway);

        RawVkeyWitness vkey = set.vkeyWitnesses().orElseThrow().get(0);
        assertThat(vkey.vkey()).containsExactly(1);
        assertThat(vkey.signature()).containsExactly(2);
        RawBootstrapWitness bootstrap = set.bootstrapWitnesses().orElseThrow().get(0);
        assertThat(bootstrap.publicKey()).containsExactly(1);
        assertThat(bootstrap.signature()).containsExactly(2);
        assertThat(bootstrap.chainCode()).containsExactly(3);
        assertThat(bootstrap.attributes()).containsExactly(4);
        assertThat(set.nativeScripts().orElseThrow()).extracting(RawScript::type).containsExactly(0);
        assertThat(hex(set.nativeScripts().orElseThrow().get(0).span())).isEqualTo(NATIVE);
        assertThat(set.plutusV1Scripts().orElseThrow()).extracting(RawScript::type).containsExactly(1);
        assertThat(set.plutusV2Scripts().orElseThrow()).extracting(RawScript::type).containsExactly(2);
        assertThat(set.plutusV3Scripts().orElseThrow()).extracting(RawScript::type).containsExactly(3);
        assertThat(set.scripts()).extracting(RawScript::type).containsExactly(0, 1, 2, 3);
        assertThat(hex(set.datums().orElseThrow().get(0).span())).isEqualTo("00");
        RawRedeemer redeemer = set.redeemers().orElseThrow().get(0);
        assertThat(redeemer.tag()).isZero();
        assertThat(redeemer.index()).isEqualTo(1);
        assertThat(hex(redeemer.data())).isEqualTo("02");
        assertThat(redeemer.units().mem()).isEqualTo(5);
        assertThat(redeemer.units().steps()).isEqualTo(6);
        assertThat(set.era()).isEqualTo(Era.Conway);
        assertThat(hex(set.span())).isEqualTo(witnesses);
    }

    @Test
    void anAbsentFieldIsEmptyAndAnEmptyFieldIsAnEmptyList() {
        RawWitnessSet absent = lenient("a0");
        assertThat(absent.vkeyWitnesses()).isEmpty();
        assertThat(absent.nativeScripts()).isEmpty();
        assertThat(absent.bootstrapWitnesses()).isEmpty();
        assertThat(absent.plutusV1Scripts()).isEmpty();
        assertThat(absent.datums()).isEmpty();
        assertThat(absent.redeemers()).isEmpty();
        assertThat(absent.plutusV2Scripts()).isEmpty();
        assertThat(absent.plutusV3Scripts()).isEmpty();
        assertThat(absent.scripts()).isEmpty();
        assertThat(lenient("a1" + "0080").vkeyWitnesses().orElseThrow()).isEmpty();
        assertThat(lenient("a1" + "0580").redeemers().orElseThrow()).isEmpty();
        assertThat(lenient("a1" + "05a0").redeemers().orElseThrow()).isEmpty();
    }

    @Test
    void setsTagsAndIndefiniteLengths() {
        // tag 258, at any width, and an indefinite list, on both readings
        for (String list : List.of("d90102" + "81" + VKEY_WITNESS, "d8ff" + "81" + VKEY_WITNESS)) {
            assertThat(lenient("a100" + list).vkeyWitnesses().orElseThrow()).hasSize(1);
        }
        assertThat(tx("a100" + "d90102" + "81" + VKEY_WITNESS).witnessSet(Era.Conway).vkeyWitnesses().orElseThrow())
                .hasSize(1);
        assertThat(tx("a100" + "9f" + VKEY_WITNESS + "ff").witnessSet(Era.Conway).vkeyWitnesses().orElseThrow())
                .hasSize(1);
        assertThat(lenient("a100" + "9f" + VKEY_WITNESS + "ff").vkeyWitnesses().orElseThrow()).hasSize(1);
        // any other tag: the models look through it, and a view of RawTx.of does not
        assertThat(lenient("c1" + "a100" + "c1" + "81" + VKEY_WITNESS).vkeyWitnesses().orElseThrow()).hasSize(1);
        assertThatThrownBy(() -> tx("a100" + "c1" + "81" + VKEY_WITNESS).witnessSet(Era.Conway).vkeyWitnesses())
                .isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> tx("c1" + "a0")).isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void whatAWitnessCannotBe() {
        for (String bad : List.of("00", "80", "81" + VKEY, "83" + VKEY + SIG + SIG, "82" + "00" + SIG,
                "82" + VKEY + "6161"))
            assertThatThrownBy(() -> RawVkeyWitness.of(span(bad))).as(bad).isInstanceOf(CborRuntimeException.class);
        for (String bad : List.of("00", "83" + VKEY + SIG + SIG, "84" + VKEY + SIG + SIG + "00"))
            assertThatThrownBy(() -> RawBootstrapWitness.of(span(bad))).as(bad)
                    .isInstanceOf(CborRuntimeException.class);
        // a length is the policy's to check, and a tag on an item is looked through
        assertThat(RawVkeyWitness.of(span("c1" + "82" + "c1" + VKEY + "5f" + SIG + "ff")).signature())
                .containsExactly(2);
        assertThatThrownBy(() -> lenient("a100" + "00").vkeyWitnesses()).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> lenient("a2" + "0080" + "1800" + "80")).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> lenient("00")).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> tx("a0").witnessSet(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void redeemersOfRawTxKeepEveryEntryAndRejectAnyOtherShape() {
        String map = "a3" + "820000" + "82" + "01" + UNITS + "821800" + "00" + "82" + "02" + UNITS
                + "820001" + "82" + "03" + UNITS;
        List<RawRedeemer> entries = tx("a105" + map).redeemers();
        assertThat(entries).extracting(RawRedeemer::index).containsExactly(0L, 0L, 1L);
        assertThat(entries).extracting(redeemer -> hex(redeemer.data())).containsExactly("01", "02", "03");
        assertThat(tx("a105" + map).witnessSet(Era.Conway).redeemers().orElseThrow()).hasSize(3);

        for (String bad : List.of("00", "c180", "8100", "8183000000", "a100" + "00", "a1" + "8100" + "820000",
                "a1" + "820000" + "8100"))
            assertThatThrownBy(() -> tx("a105" + bad).redeemers()).as(bad).isInstanceOf(CborRuntimeException.class);
    }

    /** ADR 0003 D7: a model keeps one entry per key, equal as a decoded map's are. */
    @Test
    void redeemersReadAsTheModelsReadThem() {
        String map = "a3" + "820000" + "82" + "01" + UNITS + "821800" + "00" + "82" + "02" + UNITS
                + "820001" + "82" + "03" + UNITS;
        // the first position and the last value
        assertThat(lenient("a105" + map).redeemers().orElseThrow()).extracting(redeemer -> hex(redeemer.data()))
                .containsExactly("02", "03");
        // a chunked key is another key, as a decoded map has it, and a tagged item too
        assertThat(lenient("a105" + "a2" + "820000" + "82" + "01" + UNITS + "9f0000ff" + "82" + "02" + UNITS)
                .redeemers().orElseThrow()).hasSize(2);
        assertThat(lenient("a105" + "a2" + "820000" + "82" + "01" + UNITS + "82c10000" + "82" + "02" + UNITS)
                .redeemers().orElseThrow()).hasSize(2);
        // a field of neither form holds none
        for (String none : List.of("00", "6161", "f6", "4100"))
            assertThat(lenient("a105" + none).redeemers().orElseThrow()).as(none).isEmpty();
        // tags on the field, a redeemer and an item
        assertThat(lenient("a105" + "c1" + "81" + "c1" + "84" + "c100" + "c101" + "00" + UNITS).redeemers()
                .orElseThrow().get(0).index()).isEqualTo(1);
        // the tag is any number, and the index all 64 bits
        RawRedeemer big = lenient("a105" + "81" + "84" + "1a7fffffff" + "1bffffffffffffffff" + "00" + UNITS).redeemers()
                .orElseThrow().get(0);
        assertThat(big.tag()).isEqualTo(Integer.MAX_VALUE);
        assertThat(big.unsignedIndex()).isEqualTo(new BigInteger("18446744073709551615"));
        // what never reads
        for (String bad : List.of("8100", "81" + "83000000", "81" + "84" + "20" + "00" + "00" + UNITS,
                "81" + "84" + "1a80000000" + "00" + "00" + UNITS, "81" + "84" + "00" + "20" + "00" + UNITS,
                "a1" + "00" + "82" + "00" + UNITS, "a1" + "8100" + "82" + "00" + UNITS, "a1" + "820000" + "00",
                "a1" + "820000" + "9f" + "00" + UNITS + "ff"))
            assertThatThrownBy(() -> lenient("a105" + bad).redeemers()).as(bad)
                    .isInstanceOf(CborRuntimeException.class);
        // the key may be indefinite, and an array in tag 30 is definite when decoded
        assertThat(lenient("a105" + "a1" + "9f0000ff" + "82" + "00" + UNITS).redeemers().orElseThrow()).hasSize(1);
    }

    @Test
    void executionUnitsAreTwoUnsignedIntegersInAnyForm() {
        assertThat(RawExUnits.of(span("9f" + "c105" + "06" + "ff")).mem()).isEqualTo(5);
        assertThat(hex(RawExUnits.of(span(UNITS)).span())).isEqualTo(UNITS);
        for (String bad : List.of("00", "8100", "83000000", "822000", "820020", "82c2410100"))
            assertThatThrownBy(() -> RawExUnits.of(span(bad))).as(bad).isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void indefiniteWhenDecodedLooksAtTheTagsADecoderTurnsIntoARecord() {
        assertThat(RawItems.indefiniteWhenDecoded(span("9f0102ff"))).isTrue();
        assertThat(RawItems.indefiniteWhenDecoded(span("c19f0102ff"))).isTrue();
        assertThat(RawItems.indefiniteWhenDecoded(span("820102"))).isFalse();
        assertThat(RawItems.indefiniteWhenDecoded(span("d81e9f0102ff"))).isFalse();
        assertThat(RawItems.indefiniteWhenDecoded(span("c1d81e9f0102ff"))).isFalse();
        assertThat(RawItems.indefiniteWhenDecoded(span("d81ec19f0102ff"))).isFalse();
        assertThat(RawItems.indefiniteWhenDecoded(span("d8269f61616161ff"))).isFalse();
    }

    @Test
    void aWalkEntersEveryWitnessTable() {
        List<String> events = walk("a3" + "0081" + VKEY_WITNESS + "0281" + BOOTSTRAP + "0181" + NATIVE, Era.Conway);
        assertThat(events).containsExactly(
                "enter transaction_witness_set a3" + "0081" + VKEY_WITNESS + "0281" + BOOTSTRAP + "0181" + NATIVE,
                "entry transaction_witness_set 0:NONEMPTY_LIST key=00 value=81" + VKEY_WITNESS + " occurrence=0",
                "check NONEMPTY_LIST 81" + VKEY_WITNESS,
                "enter vkeywitness " + VKEY_WITNESS,
                "entry vkeywitness 0:VKEY key=null value=" + VKEY + " occurrence=0",
                "check VKEY " + VKEY,
                "entry vkeywitness 1:SIGNATURE key=null value=" + SIG + " occurrence=0",
                "check SIGNATURE " + SIG,
                "entry transaction_witness_set 2:NONEMPTY_LIST key=02 value=81" + BOOTSTRAP + " occurrence=0",
                "check NONEMPTY_LIST 81" + BOOTSTRAP,
                "enter bootstrap_witness " + BOOTSTRAP,
                "entry bootstrap_witness 0:VKEY key=null value=" + VKEY + " occurrence=0",
                "check VKEY " + VKEY,
                "entry bootstrap_witness 1:SIGNATURE key=null value=" + SIG + " occurrence=0",
                "check SIGNATURE " + SIG,
                "entry bootstrap_witness 2:BYTES key=null value=4103 occurrence=0",
                "check BYTES 4103",
                "entry bootstrap_witness 3:BYTES key=null value=4104 occurrence=0",
                "check BYTES 4104",
                "entry transaction_witness_set 1:NONEMPTY_LIST key=01 value=81" + NATIVE + " occurrence=0",
                "check NONEMPTY_LIST 81" + NATIVE,
                "check NATIVE_SCRIPT " + NATIVE);
        assertThat(walk("a2" + "0381" + "41aa" + "0481" + "d87980", Era.Conway))
                .contains("check NONEMPTY_SET 8141aa", "check PLUTUS_SCRIPT 41aa", "check PLUTUS_DATA d87980");
        // an era's own type of the same key
        assertThat(walk("a1" + "0080", Era.Babbage)).contains("check LIST 80");
    }

    @Test
    void aWalkReportsAWitnessOfTheWrongShapeAndRejectsWhatItCannotEnter() {
        assertThat(walk("a1" + "0081" + "81" + VKEY, Era.Conway)).contains("missing vkeywitness 1:SIGNATURE");
        assertThat(walk("a1" + "0081" + "83" + VKEY + SIG + "00", Era.Conway))
                .contains("entry vkeywitness null key=null value=00 occurrence=0");
        for (String bad : List.of("a1" + "0081" + "00", "a1" + "0281" + "a0", "a1" + "0181" + "00",
                "a1" + "0381" + "00", "a1" + "0481" + "6161", "a1" + "0500", "a1" + "0581" + "00"))
            assertThatThrownBy(() -> walk(bad, Era.Conway)).as(bad).isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void aWalkEntersBothFormsOfRedeemers() {
        String item = "84" + "00" + "01" + "02" + UNITS;
        assertThat(walk("a1" + "0581" + item, Era.Conway)).containsExactly(
                "enter transaction_witness_set a1" + "0581" + item,
                "entry transaction_witness_set 5:REDEEMERS key=05 value=81" + item + " occurrence=0",
                "check REDEEMERS 81" + item,
                "check NONEMPTY_LIST 81" + item,
                "enter redeemer " + item,
                "entry redeemer 0:REDEEMER_TAG key=null value=00 occurrence=0",
                "check REDEEMER_TAG 00",
                "entry redeemer 1:UINT32 key=null value=01 occurrence=0",
                "check UINT32 01",
                "entry redeemer 2:PLUTUS_DATA key=null value=02 occurrence=0",
                "check PLUTUS_DATA 02",
                "entry redeemer 3:EX_UNITS key=null value=" + UNITS + " occurrence=0",
                "check EX_UNITS " + UNITS,
                "enter ex_units " + UNITS,
                "entry ex_units 0:EX_UNIT key=null value=05 occurrence=0",
                "check EX_UNIT 05",
                "entry ex_units 1:EX_UNIT key=null value=06 occurrence=0",
                "check EX_UNIT 06");
        assertThat(walk("a1" + "0581" + item, Era.Babbage)).contains("check LIST 81" + item);

        String key = "820001";
        String value = "82" + "02" + UNITS;
        assertThat(walk("a1" + "05a1" + key + value, Era.Conway)).containsExactly(
                "enter transaction_witness_set a1" + "05a1" + key + value,
                "entry transaction_witness_set 5:REDEEMERS key=05 value=a1" + key + value + " occurrence=0",
                "check REDEEMERS a1" + key + value,
                "check NONEMPTY_MAP a1" + key + value,
                "enter redeemers_map a1" + key + value,
                "entry redeemers_map 0:REDEEMER_VALUE key=" + key + " value=" + value + " occurrence=0",
                "check REDEEMER_KEY " + key,
                "enter redeemer_key " + key,
                "entry redeemer_key 0:REDEEMER_TAG key=null value=00 occurrence=0",
                "check REDEEMER_TAG 00",
                "entry redeemer_key 1:UINT32 key=null value=01 occurrence=0",
                "check UINT32 01",
                "check REDEEMER_VALUE " + value,
                "enter redeemer_value " + value,
                "entry redeemer_value 0:PLUTUS_DATA key=null value=02 occurrence=0",
                "check PLUTUS_DATA 02",
                "entry redeemer_value 1:EX_UNITS key=null value=" + UNITS + " occurrence=0",
                "check EX_UNITS " + UNITS,
                "enter ex_units " + UNITS,
                "entry ex_units 0:EX_UNIT key=null value=05 occurrence=0",
                "check EX_UNIT 05",
                "entry ex_units 1:EX_UNIT key=null value=06 occurrence=0",
                "check EX_UNIT 06");
    }

    /** Keys that decode alike are one key for {@code occurrence}, however their items are encoded (ADR 0003 D6). */
    @Test
    void aRepeatedRedeemerKeyIsSeen() {
        String value = "82" + "02" + UNITS;
        assertThat(walk("a1" + "05a3" + "820001" + value + "82001801" + value + "820002" + value, Era.Conway)).contains(
                "entry redeemers_map 0:REDEEMER_VALUE key=820001 value=" + value + " occurrence=0",
                "entry redeemers_map 0:REDEEMER_VALUE key=82001801 value=" + value + " occurrence=1",
                "entry redeemers_map 0:REDEEMER_VALUE key=820002 value=" + value + " occurrence=0");
    }

    /** The accessors that take no era read what they always did. */
    @Test
    void theRawTxAccessorsReadTheSameFields() {
        RawTx tx = tx("a4" + "0081" + VKEY_WITNESS + "0181" + NATIVE + "0281" + BOOTSTRAP + "048100");
        assertThat(tx.vkeyWitnesses()).extracting(RawWitnessSetTest::hex).containsExactly(VKEY_WITNESS);
        assertThat(tx.bootstrapWitnesses()).extracting(RawWitnessSetTest::hex).containsExactly(BOOTSTRAP);
        assertThat(tx.scripts()).extracting(script -> hex(script.span())).containsExactly(NATIVE);
        assertThat(tx.witnessDatums()).extracting(datum -> hex(datum.span())).containsExactly("00");
        assertThat(tx.redeemers()).isEmpty();
        assertThat(tx("a0").witnessDatums()).isEmpty();
    }
}
