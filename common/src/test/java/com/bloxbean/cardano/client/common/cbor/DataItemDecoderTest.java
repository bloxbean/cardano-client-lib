package com.bloxbean.cardano.client.common.cbor;

import co.nstant.in.cbor.model.*;
import co.nstant.in.cbor.model.Number;
import com.bloxbean.cardano.client.common.cbor.custom.EncodedKeyMap;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.List;

import static com.bloxbean.cardano.client.util.HexUtil.decodeHexString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The cbor-java tree shapes {@link CborSerializationUtil#deserialize(byte[])} reproduces, and what it rejects.
 */
class DataItemDecoderTest {

    private static DataItem decode(String hex) {
        return CborSerializationUtil.deserialize(decodeHexString(hex));
    }

    @Test
    void indefiniteArrayIsChunkedAndEndsWithBreak() {
        Array array = (Array) decode("9f0102ff");
        assertThat(array.isChunked()).isTrue();
        assertThat(array.getDataItems()).hasSize(3);
        assertThat(array.getDataItems().get(2)).isSameAs(Special.BREAK);

        Array definite = (Array) decode("980201" + "02");
        assertThat(definite.isChunked()).isFalse();
        assertThat(definite.getDataItems()).hasSize(2);
    }

    @Test
    void indefiniteMapIsChunkedWithoutBreak() {
        Map map = (Map) decode("bf0102ff");
        assertThat(map).isInstanceOf(EncodedKeyMap.class);
        assertThat(map.isChunked()).isTrue();
        assertThat(map.getKeys()).hasSize(1);
        assertThat(map.get(new UnsignedInteger(1))).isEqualTo(new UnsignedInteger(2));
    }

    @Test
    void chunkedStringsAreJoinedAndNotChunked() {
        ByteString bytes = (ByteString) decode("5f41aa41bbff");
        assertThat(bytes.getBytes()).isEqualTo(new byte[]{(byte) 0xaa, (byte) 0xbb});
        assertThat(bytes.isChunked()).isFalse();
        UnicodeString text = (UnicodeString) decode("7f6161626263ff");
        assertThat(text.getString()).isEqualTo("abc");
        assertThat(text.isChunked()).isFalse();
    }

    @Test
    void tagsAreChainedFromTheInnermost() {
        // 1(2(3(0))): the item carries 3, which carries 2, which carries 1
        DataItem item = decode("c1c2c300");
        assertThat(item.getTag().getValue()).isEqualTo(3);
        assertThat(item.getTag().getTag().getValue()).isEqualTo(2);
        assertThat(item.getTag().getTag().getTag().getValue()).isEqualTo(1);
        assertThat(item.getTag().getTag().getTag().hasTag()).isFalse();
        // tag 258 at any width
        assertThat(decode("db000000000000010280").getTag().getValue()).isEqualTo(258);
    }

    @Test
    void tag30IsARationalNumber() {
        DataItem item = decode("d81e82011903e8");
        assertThat(item).isInstanceOf(RationalNumber.class);
        RationalNumber rational = (RationalNumber) item;
        assertThat(rational.getNumerator().getValue()).isEqualTo(BigInteger.ONE);
        assertThat(rational.getDenominator().getValue()).isEqualTo(BigInteger.valueOf(1000));
        assertThat(rational.getTag().getValue()).isEqualTo(30);
        // an outer tag chains on tag 30
        assertThat(decode("c1d81e820102").getTag().getTag().getValue()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"d81e01", "d81e8101", "d81e83010203", "d81e820100", "d81e82016161", "d8268201616d", "d8268261610a"})
    void malformedRationalsAndLanguageStringsAreRejectedLikeCborJava(String hex) {
        assertThatThrownBy(() -> decode(hex)).isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void tag38IsALanguageTaggedString() {
        DataItem item = decode("d82682626573626869");
        assertThat(item).isInstanceOf(LanguageTaggedString.class);
        assertThat(((LanguageTaggedString) item).getString().getString()).isEqualTo("hi");
    }

    @Test
    void untaggedSimpleValuesAreTheSingletons() {
        Array array = (Array) decode("84f4f5f6f7");
        assertThat(array.getDataItems().get(0)).isSameAs(SimpleValue.FALSE);
        assertThat(array.getDataItems().get(1)).isSameAs(SimpleValue.TRUE);
        assertThat(array.getDataItems().get(2)).isSameAs(SimpleValue.NULL);
        assertThat(array.getDataItems().get(3)).isSameAs(SimpleValue.UNDEFINED);
    }

    @Test
    void taggedSimpleValuesDoNotTouchTheSingletons() {
        DataItem tagged = decode("c1f5");
        assertThat(tagged).isNotSameAs(SimpleValue.TRUE).isEqualTo(taggedTrue());
        assertThat(SimpleValue.TRUE.hasTag()).isFalse();
        assertThat(decode("f5")).isSameAs(SimpleValue.TRUE);
    }

    private static SimpleValue taggedTrue() {
        SimpleValue value = new SimpleValue(SimpleValueType.TRUE);
        value.setTag(1);
        return value;
    }

    @Test
    void otherSimpleValuesAndFloats() {
        assertThat(decode("e0")).isEqualTo(new SimpleValue(0));
        assertThat(decode("f820")).isEqualTo(new SimpleValue(32));
        assertThat(((HalfPrecisionFloat) decode("f93c00")).getValue()).isEqualTo(1.0f);
        assertThat(((SinglePrecisionFloat) decode("fa3fc00000")).getValue()).isEqualTo(1.5f);
        assertThat(((DoublePrecisionFloat) decode("fb3ff8000000000000")).getValue()).isEqualTo(1.5d);
    }

    @Test
    void integersOfEveryWidth() {
        assertThat(((Number) decode("1bffffffffffffffff")).getValue()).isEqualTo(BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE));
        assertThat(((Number) decode("3bffffffffffffffff")).getValue()).isEqualTo(BigInteger.ONE.shiftLeft(64).negate());
        assertThat(decode("1800")).isEqualTo(new UnsignedInteger(0));
        assertThat(decode("38ff")).isEqualTo(new NegativeInteger(-256));
    }

    @Test
    void repeatedMapKeyKeepsFirstPositionAndLastValue() {
        // {1: 0, 2: 2, 1 (non-minimal): 1}
        Map map = (Map) decode("a3010002021801" + "01");
        assertThat(map.getKeys()).containsExactly(new UnsignedInteger(1), new UnsignedInteger(2));
        assertThat(map.get(new UnsignedInteger(1))).isEqualTo(new UnsignedInteger(1));
    }

    @Test
    void deserializeReturnsTheFirstOfSeveralItems() {
        assertThat(decode("0102")).isEqualTo(new UnsignedInteger(1));
        assertThat(CborSerializationUtil.deserializeAll(decodeHexString("0102f6")))
                .containsExactly(new UnsignedInteger(1), new UnsignedInteger(2), SimpleValue.NULL);
        assertThat(CborSerializationUtil.deserializeAll(new byte[0])).isEmpty();
        // later items must be well-formed too, as with cbor-java
        assertThatThrownBy(() -> decode("011c")).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> CborSerializationUtil.deserialize(new byte[0])).isInstanceOf(CborRuntimeException.class);
    }

    /**
     * What cbor-java accepted although it is not well-formed CBOR, and the walker's rules that the decoder shares.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "ff",          // BREAK at top level
            "82ff01",      // BREAK in a definite array
            "bf01ffff",    // BREAK as a map value
            "9fc1ffff",    // tag on BREAK
            "c1ff",        // tag on BREAK at top level
            "5f5fffff",    // nested indefinite chunk
            "5fc14100ff",  // tagged chunk
            "5f6161ff",    // text chunk in a byte string
            "1c", "3d", "5e", "fc",   // reserved additional information
            "1f", "df01",  // indefinite integer or tag
            "8201", "9f01", "a101", "c1", "4201", "1901", // truncated
            "9bffffffffffffffff", "9a7fffffff00", "5a7fffffff00", // declared size beyond the input
    })
    void malformedInputIsRejected(String hex) {
        assertThatThrownBy(() -> decode(hex)).isInstanceOf(CborRuntimeException.class).hasMessageContaining("at offset");
    }

    @Test
    void decodedMapsArePlainMapsToCallers() {
        Map map = (Map) decode("a2" + "00" + "8201" + "02" + "01" + "a10102");
        assertThat((DataItem) map).isInstanceOf(Map.class);
        List<DataItem> keys = List.copyOf(map.getKeys());
        assertThat(keys).containsExactly(new UnsignedInteger(0), new UnsignedInteger(1));
        assertThat(map.getValues()).hasSize(2);
        assertThatThrownBy(() -> map.getKeys().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
