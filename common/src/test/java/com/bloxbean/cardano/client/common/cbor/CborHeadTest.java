package com.bloxbean.cardano.client.common.cbor;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CborHead#write}: the shortest head for each argument, read back by {@link CborSpan}.
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
        assertThatThrownBy(() -> write(8, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> write(-1, 0)).isInstanceOf(IllegalArgumentException.class);
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
