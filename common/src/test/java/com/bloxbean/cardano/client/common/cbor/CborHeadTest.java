package com.bloxbean.cardano.client.common.cbor;

import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CborHead#write}: the shortest head for each argument, read back by {@link CborSpan}; {@link CborHead#at}: a
 * head's parts and the section 5.3 rules.
 */
class CborHeadTest {

    @Test
    void writesTheShortestHeadAtEveryBoundary() {
        long[][] cases = { // argument, head length
                {0, 1}, {23, 1}, {24, 2}, {255, 2}, {256, 3}, {65535, 3}, {65536, 5}, {0xffff_ffffL, 5},
                {0x1_0000_0000L, 9}, {Long.MAX_VALUE, 9}, {-1, 9} // -1: 2^64 - 1, unsigned
        };
        for (long[] c : cases) {
            byte[] head = write(0, c[0]);
            assertThat(head).as("argument %s", Long.toUnsignedString(c[0])).hasSize((int) c[1]);
            assertThat(CborSpan.of(head).asBigInteger()).isEqualTo(new BigInteger(Long.toUnsignedString(c[0])));
        }
    }

    @Test
    void writesTheMajorType() {
        assertThat(write(4, 2)).containsExactly(0x82);
        assertThat(write(5, 300)).containsExactly(0xb9, 0x01, 0x2c);
        assertThat(CborSpan.of(concat(write(2, 3), new byte[]{1, 2, 3})).byteString()).containsExactly(1, 2, 3);
        assertThat(CborSpan.of(concat(write(6, 24), write(0, 1))).tag()).isEqualTo(24);
        assertThatThrownBy(() -> write(8, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> write(-1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readsAHeadsParts() {
        CborHead head = CborHead.at(HexUtil.decodeHexString("00" + "1a00010000"), 1, 6);
        assertThat(head.start()).isEqualTo(1);
        assertThat(head.end()).isEqualTo(6);
        assertThat(head.major()).isZero();
        assertThat(head.info()).isEqualTo(26);
        assertThat(head.argument()).isEqualTo(65536);
        assertThat(head.isIndefinite()).isFalse();

        CborHead big = CborHead.at(HexUtil.decodeHexString("3bffffffffffffffff"), 0, 9);
        assertThat(big.major()).isEqualTo(1);
        assertThat(Long.toUnsignedString(big.argument())).isEqualTo("18446744073709551615");

        CborHead chunked = CborHead.at(HexUtil.decodeHexString("5f4101ff"), 0, 4);
        assertThat(chunked.isIndefinite()).isTrue();
        assertThat(chunked.argument()).isEqualTo(-1);
        assertThat(chunked.end()).isEqualTo(1);

        CborHead tag = CborHead.at(HexUtil.decodeHexString("d81e"), 0, 2);
        assertThat(tag.major()).isEqualTo(6);
        assertThat(tag.argument()).isEqualTo(30);
    }

    @Test
    void aStringsContentEndsWhereItsLengthSays() {
        byte[] text = HexUtil.decodeHexString("63616263ff");
        CborHead head = CborHead.at(text, 0, text.length);
        assertThat(head.stringEnd(text.length)).isEqualTo(4);
        assertThatThrownBy(() -> head.stringEnd(3)).isInstanceOf(CborRuntimeException.class)
                .hasMessageContaining("truncated");
        assertThatThrownBy(() -> CborHead.at(HexUtil.decodeHexString("5f"), 0, 1).stringEnd(1))
                .isInstanceOf(CborRuntimeException.class);
        assertThatThrownBy(() -> CborHead.at(HexUtil.decodeHexString("8100"), 0, 2).stringEnd(2))
                .isInstanceOf(CborRuntimeException.class);
    }

    @Test
    void aHeadThatIsNotWellFormedIsRefused() {
        for (String bad : new String[]{"", "18", "1c", "1f", "df", "ff", "f800", "f81f", "5a0000"}) {
            byte[] bytes = HexUtil.decodeHexString(bad);
            assertThatThrownBy(() -> CborHead.at(bytes, 0, bytes.length)).as(bad)
                    .isInstanceOf(CborRuntimeException.class);
        }
        // a string's head alone is well formed; whether its content fits is stringEnd's to say
        assertThat(CborHead.at(HexUtil.decodeHexString("5a0000ffff"), 0, 5).argument()).isEqualTo(0xffff);
    }

    @Test
    void majorType7IsNotWritten() {
        // simple values and floats: f818 or f81f would not be well-formed (RFC 8949 section 3.3)
        for (long argument : new long[]{0, 20, 24, 31})
            assertThatThrownBy(() -> write(7, argument)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("0 to 6");
    }

    private static byte[] write(int major, long argument) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CborHead.write(out, major, argument);
        return out.toByteArray();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] c = new byte[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }
}
