package com.bloxbean.cardano.client.plutus.spec;

import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.List;

import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;
import static com.bloxbean.cardano.client.util.HexUtil.encodeHexString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlutusDataCodecTest {

    private static PlutusData decode(String hex) throws CborDeserializationException {
        return PlutusData.deserialize(decodeHexString(hex));
    }

    @ParameterizedTest(name = "{0} -> alternative {1}")
    @CsvSource({
            "d87980, 0", "d87f80, 6",                     // tags 121..127
            "d9050080, 7", "d9057880, 127",               // tags 1280..1400
            "d866820080, 0", "d86682188080, 128", "d866821b7fffffffffffffff80, 9223372036854775807", // tag 102
    })
    void everyConstructorForm(String hex, long alternative) throws Exception {
        ConstrPlutusData constr = (ConstrPlutusData) decode(hex);
        assertThat(constr.getAlternative()).isEqualTo(alternative);
        assertThat(constr.getData().getPlutusDataList()).isEmpty();
    }

    @Test
    void constructorsSerializeInTheirCanonicalForm() throws Exception {
        assertThat(ConstrPlutusData.of(0).serializeToHex()).isEqualTo("d87980");
        assertThat(ConstrPlutusData.of(7).serializeToHex()).isEqualTo("d9050080");
        assertThat(ConstrPlutusData.of(127).serializeToHex()).isEqualTo("d9057880");
        assertThat(ConstrPlutusData.of(128).serializeToHex()).isEqualTo("d86682188080");
        assertThat(ConstrPlutusData.of(1, BigIntPlutusData.of(5)).serializeToHex()).isEqualTo("d87a9f05ff");
    }

    @Test
    void generalFormMayBeIndefinite() throws Exception {
        // plutus-core's decodeConstrExtended reads [alternative, [fields]] with decodeListLenOrIndef
        ConstrPlutusData constr = (ConstrPlutusData) decode("d8669f" + "182a" + "9f01ff" + "ff");
        assertThat(constr.getAlternative()).isEqualTo(42);
        assertThat(constr.getData().getPlutusDataList()).containsExactly(BigIntPlutusData.of(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"d866822080", "d866820100", "d86683008000", "d8668100"})
    void malformedGeneralFormIsRejected(String hex) {
        // a negative alternative, fields that are not a list, the wrong number of items
        assertThatThrownBy(() -> decode(hex)).isInstanceOf(CborDeserializationException.class);
    }

    @Test
    void unknownConstructorTagIsRejected() {
        // the recursive code threw a NullPointerException here
        assertThatThrownBy(() -> decode("c180")).isInstanceOf(CborDeserializationException.class);
        assertThatThrownBy(() -> decode("d9014080")).isInstanceOf(CborDeserializationException.class);
    }

    @Test
    void bignumsAndChunkedBytes() throws Exception {
        BigInteger twoTo64 = BigInteger.ONE.shiftLeft(64);
        assertThat(((BigIntPlutusData) decode("c249010000000000000000")).getValue()).isEqualTo(twoTo64);
        assertThat(((BigIntPlutusData) decode("c349010000000000000000")).getValue()).isEqualTo(twoTo64.negate().subtract(BigInteger.ONE));
        assertThat(((BigIntPlutusData) decode("c25f4101480000000000000000ff")).getValue()).isEqualTo(twoTo64);
        assertThat(((BytesPlutusData) decode("5f41aa41bbff")).getValue()).isEqualTo(new byte[]{(byte) 0xaa, (byte) 0xbb});
        // byte strings over 64 bytes serialize in 64-byte chunks
        BytesPlutusData long100 = BytesPlutusData.of(new byte[100]);
        assertThat(long100.serializeToHex()).startsWith("5f5840").endsWith("ff");
        assertThat(decode(long100.serializeToHex())).isEqualTo(long100);
    }

    @Test
    void listsKeepTheirChunkedFlag() throws Exception {
        ListPlutusData indefinite = (ListPlutusData) decode("9f0102ff");
        ListPlutusData definite = (ListPlutusData) decode("820102");
        assertThat(indefinite.isChunked()).isTrue();
        assertThat(definite.isChunked()).isFalse();
        assertThat(indefinite).isNotEqualTo(definite);
        assertThat(indefinite.serializeToHex()).isEqualTo("9f0102ff");
        assertThat(definite.serializeToHex()).isEqualTo("820102");
    }

    @Test
    void mapsKeepTheFirstPositionAndLastValueOfARepeatedKey() throws Exception {
        // {1: 0, 2: 2, 1: 1}: on chain a list of three pairs; the model keeps one entry per key (lossy)
        MapPlutusData map = (MapPlutusData) decode("a3010002020101");
        assertThat(map.getMap()).hasSize(2);
        assertThat(List.copyOf(map.getMap().keySet())).containsExactly(BigIntPlutusData.of(1), BigIntPlutusData.of(2));
        assertThat(map.getMap().get(BigIntPlutusData.of(1))).isEqualTo(BigIntPlutusData.of(1));
    }

    @Test
    void mapsWithBigIntegerKeysKeepEveryEntry() throws Exception {
        // the recursive code serialized through a cbor-java map that treats all big integers over 64 bits of the same
        // sign as one key, dropping entries
        MapPlutusData map = new MapPlutusData();
        map.put(BigIntPlutusData.of(BigInteger.ONE.shiftLeft(70)), BigIntPlutusData.of(1));
        map.put(BigIntPlutusData.of(BigInteger.ONE.shiftLeft(80)), BigIntPlutusData.of(2));
        String hex = map.serializeToHex();
        assertThat(hex).startsWith("a2");
        assertThat(decode(hex)).isEqualTo(map);
    }

    @Test
    void mapsWithByteStringKeysOver64BytesKeepEveryEntry() throws Exception {
        // a valid on-chain map whose two keys are chunked 65-byte strings: serializing it kept one entry, as every
        // chunked byte string was equal to every other
        String keyA = "5f" + "5840" + "aa".repeat(64) + "41aa" + "ff";
        String keyB = "5f" + "5840" + "bb".repeat(64) + "41bb" + "ff";
        MapPlutusData decoded = (MapPlutusData) decode("a2" + keyA + "01" + keyB + "02");
        assertThat(decoded.getMap()).hasSize(2);
        String hex = decoded.serializeToHex();
        assertThat(hex).isEqualTo("a2" + keyA + "01" + keyB + "02");
        assertThat(decode(hex)).isEqualTo(decoded);
        assertThat(decoded.getDatumHash()).isEqualTo(encodeHexString(Blake2bUtil.blake2bHash256(decodeHexString(hex))));

        MapPlutusData built = new MapPlutusData();
        built.put(BytesPlutusData.of(new byte[100]), BigIntPlutusData.of(1));
        byte[] other = new byte[100];
        other[99] = 1;
        built.put(BytesPlutusData.of(other), BigIntPlutusData.of(2));
        assertThat(((MapPlutusData) decode(built.serializeToHex())).getMap()).hasSize(2);
    }

    @Test
    void equalityAndHashCode() throws Exception {
        // maps compare regardless of order, lists by order
        assertThat(decode("a201020304")).isEqualTo(decode("a203040102"));
        assertThat(decode("a201020304").hashCode()).isEqualTo(decode("a203040102").hashCode());
        assertThat(decode("820102")).isNotEqualTo(decode("820201"));
        assertThat(decode("d87a820102")).isNotEqualTo(decode("d87b820102"));
        // an integer is equal whatever its encoding
        assertThat(decode("8105")).isEqualTo(decode("81c24105"));
        assertThat(decode("d87980")).isNotEqualTo(decode("80"));
        assertThat(ConstrPlutusData.of(0, BigIntPlutusData.of(1)).equals(null)).isFalse();
    }

    @Test
    void equalDeepMapKeysAreFoundWithoutRecursion() throws Exception {
        // two keys that are equal as Plutus data (the same chain of maps, one with definite and one with indefinite
        // encoding) in one map: putting the second compares them, 4,000 map levels deep
        int levels = 4_000;
        String definite = "a1".repeat(levels) + "00" + "00".repeat(levels);
        StringBuilder indefinite = new StringBuilder();
        indefinite.append("bf".repeat(levels)).append("00");
        for (int i = 0; i < levels; i++)
            indefinite.append("00ff");
        MapPlutusData map = (MapPlutusData) decode("a2" + definite + "01" + indefinite + "02");
        assertThat(map.getMap()).hasSize(1);
        assertThat(map.getMap().values()).containsExactly(BigIntPlutusData.of(2));
        assertThat(encodeHexString(map.serializeToBytes())).startsWith("a1");
    }
}
