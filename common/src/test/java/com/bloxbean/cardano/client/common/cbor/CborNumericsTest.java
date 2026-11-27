package com.bloxbean.cardano.client.common.cbor;

import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.NegativeInteger;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;

import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.getBigInteger;
import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.toUnsignedInt;
import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.toUnsignedLong;
import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.unsignedLongItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CborNumericsTest {

    private static final BigInteger TWO_64 = BigInteger.ONE.shiftLeft(64);

    @Test
    void bignumContentIsAnUnsignedMagnitude() {
        byte[] highBitSet = {(byte) 0x80, 0, 0, 0, 0, 0, 0, 0, 0};
        BigInteger magnitude = BigInteger.ONE.shiftLeft(71);

        assertThat(getBigInteger(bignum(2, highBitSet))).isEqualTo(magnitude);
        assertThat(getBigInteger(bignum(3, highBitSet))).isEqualTo(BigInteger.ONE.negate().subtract(magnitude));
    }

    @Test
    void negativeBignumIsMinusOneMinusItsMagnitude() {
        assertThat(getBigInteger(bignum(3, new byte[]{0}))).isEqualTo(BigInteger.ONE.negate());
    }

    @Test
    void toUnsignedIntReadsAnIntAndRejectsAnythingWider() {
        assertThat(toUnsignedInt(new UnsignedInteger(Integer.MAX_VALUE))).isEqualTo(Integer.MAX_VALUE);
        assertThatThrownBy(() -> toUnsignedInt(new UnsignedInteger(BigInteger.ONE.shiftLeft(31))))
                .isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> toUnsignedInt(new UnsignedInteger(BigInteger.ONE.shiftLeft(32).add(BigInteger.TWO))))
                .isInstanceOf(CborRuntimeException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "2147483648", "4294967296", "9223372036854775807", "9223372036854775808",
            "18446744073709551615"})
    void unsignedLongRoundTrips(String decimal) {
        UnsignedInteger item = new UnsignedInteger(new BigInteger(decimal));

        assertThat(unsignedLongItem(toUnsignedLong(item))).isEqualTo(item);
    }

    @Test
    void anythingNotANumberIsRejectedWithoutRenderingIt() {
        DataItem deep = CborSerializationUtil.deserialize(java.util.HexFormat.of().parseHex("81".repeat(6000) + "00"));
        DataItem untaggedBytes = new ByteString(new byte[]{1});

        for (DataItem item : new DataItem[]{deep, untaggedBytes}) {
            assertThatThrownBy(() -> toUnsignedInt(item)).isInstanceOf(CborRuntimeException.class);
            assertThatThrownBy(() -> toUnsignedLong(item)).isInstanceOf(CborRuntimeException.class);
        }
    }

    @Test
    void aNegativeIntegerOrBignumIsNotAnUnsignedInteger() {
        for (DataItem item : new DataItem[]{new NegativeInteger(-1), bignum(2, new byte[]{0}), bignum(2, new byte[]{1})}) {
            assertThatThrownBy(() -> toUnsignedInt(item)).isInstanceOf(CborRuntimeException.class);
            assertThatThrownBy(() -> toUnsignedLong(item)).isInstanceOf(CborRuntimeException.class);
        }
    }

    @Test
    void toUnsignedLongRejectsWhat64BitsCannotHold() {
        assertThatThrownBy(() -> toUnsignedLong(new UnsignedInteger(TWO_64))).isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> toUnsignedLong(bignum(3, new byte[]{0}))).isInstanceOf(CborRuntimeException.class);
    }

    private static DataItem bignum(long tag, byte[] content) {
        ByteString item = new ByteString(content);
        item.setTag(tag);
        return item;
    }
}
