package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.plutus.spec.MapPlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR 0002 D6.2 and D6.3, I8: an inline datum keeps its original bytes while the decoded datum still represents them,
 * and retained bytes are never written for a datum they no longer represent, at any depth.
 */
class TransactionOutputBytesTest {

    private static final String ADDRESS = "581d60" + "00".repeat(28);

    /** A map repeating key 1: decoding keeps {1: 1}, and the bytes keep both pairs. */
    private static final String REPEATED = "a201000101";
    private static final String COLLAPSED = "a10101";

    private static TransactionOutput decode(String datum) throws Exception {
        byte[] payload = hex(datum);
        String head = payload.length < 24 ? "d818" + String.format("%02x", 0x40 + payload.length)
                : "d81858" + String.format("%02x", payload.length);
        return TransactionOutput.deserialize(CborSerializationUtil.deserialize(
                hex("a300" + ADDRESS + "0100" + "028201" + head + datum)));
    }

    private static String written(TransactionOutput output) throws Exception {
        Map map = (Map) output.serialize();
        Array option = (Array) map.get(new UnsignedInteger(2));
        return HexFormat.of().formatHex(((ByteString) option.getDataItems().get(1)).getBytes());
    }

    private static PlutusData datum(String hex) throws Exception {
        return PlutusData.deserialize(hex(hex));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            REPEATED, // repeated key
            "a10102", "a203040102", // map order
            "bf0102ff", // indefinite map
            "9f0102ff", "9f9fffff", "8401020304", // list forms
            "5f4101ff", // chunked bytes
            "c340", "1801", // non-minimal integers
            "81" + REPEATED, "d87981" + REPEATED // nested
    })
    void anUnchangedDecodedDatumKeepsItsBytes(String datum) throws Exception {
        assertThat(written(decode(datum))).isEqualTo(datum);
    }

    @Test
    void replacingTheDatumWritesTheReplacement() throws Exception {
        TransactionOutput output = decode(REPEATED);

        output.setInlineDatum(datum(COLLAPSED));

        assertThat(written(output)).isEqualTo(COLLAPSED);
    }

    @Test
    void aBuilderCopyWithANewDatumWritesTheNewDatum() throws Exception {
        TransactionOutput copy = decode(REPEATED).toBuilder().inlineDatum(datum(COLLAPSED)).build();

        assertThat(written(copy)).isEqualTo(COLLAPSED);
    }

    @Test
    void aBuilderCopyEncodesTheModel() throws Exception {
        assertThat(written(decode(REPEATED).toBuilder().build())).isEqualTo(COLLAPSED);
    }

    @Test
    void reorderingAMapInPlaceDropsTheRetainedBytes() throws Exception {
        TransactionOutput output = decode("a203040102");
        MapPlutusData map = (MapPlutusData) output.getInlineDatum();

        PlutusData first = BigIntPlutusData.of(3);
        map.put(first, map.getMap().remove(first));

        assertThat(written(output)).isEqualTo("a201020304"); // the model's encoding, canonical as for any datum
    }

    @Test
    void changingAMapInPlaceWritesTheChange() throws Exception {
        TransactionOutput output = decode(REPEATED);

        ((MapPlutusData) output.getInlineDatum()).put(BigIntPlutusData.of(1), BigIntPlutusData.of(9));

        assertThat(written(output)).isEqualTo("a10109");
    }

    @Test
    void replacingARepeatedMapInAListWritesTheReplacement() throws Exception {
        TransactionOutput output = decode("81" + REPEATED);

        ((ListPlutusData) output.getInlineDatum()).getPlutusDataList().set(0, datum(COLLAPSED));

        assertThat(written(output)).isEqualTo("81" + COLLAPSED);
    }

    @Test
    void replacingARepeatedMapInAConstructorWritesTheReplacement() throws Exception {
        TransactionOutput output = decode("d87981" + REPEATED);

        ((ConstrPlutusData) output.getInlineDatum()).getData().getPlutusDataList().set(0, datum(COLLAPSED));

        assertThat(written(output)).isEqualTo("d87981" + COLLAPSED);
    }

    @Test
    void replacingARepeatedMapInAMapValueWritesTheReplacement() throws Exception {
        TransactionOutput output = decode("a107" + REPEATED);

        ((MapPlutusData) output.getInlineDatum()).put(BigIntPlutusData.of(7), datum(COLLAPSED));

        assertThat(written(output)).isEqualTo("a107" + COLLAPSED);
    }

    @Test
    void replacingARepeatedMapWithOneRepeatingDifferentlyWritesTheReplacement() throws Exception {
        TransactionOutput output = decode("81" + REPEATED);

        ((ListPlutusData) output.getInlineDatum()).getPlutusDataList().set(0, datum("a201020101"));

        assertThat(written(output)).isEqualTo("81" + COLLAPSED);
    }

    @Test
    void replacingAMapWhoseRepeatedValueRepeatsWritesTheReplacement() throws Exception {
        TransactionOutput output = decode("81a201a2010001010100");

        ((ListPlutusData) output.getInlineDatum()).getPlutusDataList().set(0, datum("a201a101010100"));

        assertThat(written(output)).isEqualTo("81a10100");
    }

    @Test
    void replacingAMapWhoseRepeatedKeyRepeatsWritesTheReplacement() throws Exception {
        TransactionOutput output = decode("81a2a1010100a20100010101");

        ((ListPlutusData) output.getInlineDatum()).getPlutusDataList().set(0, datum("a2a1010100a1010101"));

        assertThat(written(output)).isEqualTo("81a1a1010101");
    }

    @Test
    void replacingChildrenSoARepeatMovesFromInsideARepeatedValueToAnotherMapWritesTheReplacement() throws Exception {
        TransactionOutput output = decode("82a10101a20183a20100010100000100");
        List<PlutusData> children = ((ListPlutusData) output.getInlineDatum()).getPlutusDataList();

        children.set(0, datum("a201000101"));
        children.set(1, datum("a20183a1010100000100"));

        assertThat(written(output)).isEqualTo("82a10101a10100");
    }

    @ParameterizedTest
    @CsvSource({
            // CBOR keys that differ but are one Plutus key, so the conversion to the model drops a pair
            "81a2a000bfff01, a1a001, 81a1a001",
            "81a2d8798000d86682008001, a1d8798001, 81a1d8798001",
            "81a20100c2410102, a10102, 81a10102",
            // the same lost pair at another position
            "81a3a000bfff010203, a3a0000203bfff01, 81a20203a001",
            // a key repeated in the CBOR and equal to one in another form, whose order only their forms show
            "81a3c241010101200100, a30120c241010001c24101, 81a10100",
            // the same, inside a list key
            "81a381c2410101810120810100, a381012081c24101008101c24101, 81a1810100",
            // the same, with a key's definite and indefinite forms
            "81a3a020bfffa0a080, bfbfff80a020a0a0ff, 81a1a0a0",
            // model keys that differ but encode the same, so the encoding keeps one pair
            "81a29fff018002, a18002, 81a18002",
            // the same pairs of such keys in another order, which the encoding can't show
            "81a29fff018001, a280019fff01, 81a18001",
    })
    void replacingAMapThatLostAPairWritesTheReplacement(String original, String replacement, String model)
            throws Exception {
        TransactionOutput unchanged = decode(original);
        assertThat(written(unchanged)).isEqualTo(original);

        TransactionOutput output = decode(original);
        ((ListPlutusData) output.getInlineDatum()).getPlutusDataList().set(0, datum(replacement));

        assertThat(written(output)).isEqualTo(model);
    }

    @Test
    void movingARepeatedMapWithinAListWritesTheModel() throws Exception {
        TransactionOutput output = decode("82" + REPEATED + COLLAPSED);

        Collections.swap(((ListPlutusData) output.getInlineDatum()).getPlutusDataList(), 0, 1);

        assertThat(written(output)).isEqualTo("82" + COLLAPSED + COLLAPSED);
    }

    @Test
    void aBuiltOutputEncodesItsDatum() throws Exception {
        TransactionOutput output = TransactionOutput.builder().address(decode(COLLAPSED).getAddress())
                .value(decode(COLLAPSED).getValue()).inlineDatum(datum("a203040102")).build();

        assertThat(written(output)).isEqualTo("a201020304"); // canonical, as before
    }

    @Test
    void aCopyWritesTheSameBytes() throws Exception {
        assertThat(written(decode(REPEATED).copy())).isEqualTo(REPEATED);
    }

    @Test
    void aCopyWithANewDatumWritesTheNewDatum() throws Exception {
        TransactionOutput copy = decode(REPEATED).copy();

        copy.setInlineDatum(datum(COLLAPSED));

        assertThat(written(copy)).isEqualTo(COLLAPSED);
    }

    @Test
    void retainedBytesAreNotPartOfTheValue() throws Exception {
        TransactionOutput decoded = decode(REPEATED);
        TransactionOutput copy = decoded.toBuilder().build();

        assertThat(decoded).isEqualTo(copy).hasSameHashCodeAs(copy);
        assertThat(decoded.toString()).doesNotContain("decodedDatum");
    }

    @ParameterizedTest
    @CsvSource({
            "9f00820180ff,8200820180", // indefinite outer array, definite inner kept
            "9f009f0180ffff,82009f0180ff", // indefinite inner kept
            "820243010203,820243010203", // already definite
            "9f01ff,9f01ff", // not a pair: written as it is
            "9f,9f" // not CBOR: written as it is
    })
    void scriptRefOuterArrayIsWrittenDefinite(String stored, String expected) throws Exception {
        TransactionOutput output = decode(COLLAPSED);
        output.setScriptRef(hex(stored));

        Map map = (Map) output.serialize();

        assertThat(((ByteString) map.get(new UnsignedInteger(3))).getBytes()).isEqualTo(hex(expected));
        assertThat(output.getScriptRef()).isEqualTo(hex(stored));
    }

    private static byte[] hex(String hex) {
        return HexFormat.of().parseHex(hex);
    }
}
