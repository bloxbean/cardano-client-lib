package com.bloxbean.cardano.vds.core.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VarIntsTest {

    @Test
    void maximumSignedIntegerRoundTripsCanonically() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        VarInts.writeUnsignedInt(Integer.MAX_VALUE, output);

        assertArrayEquals(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF, 0x07}, output.toByteArray());
        assertEquals(Integer.MAX_VALUE,
                VarInts.readUnsignedInt(output.toByteArray(), 0).value());
    }

    @Test
    void rejectsSignedOverflowAndOverlongEncoding() {
        assertThrows(IllegalArgumentException.class, () -> VarInts.readUnsignedInt(
                new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x08}, 0));
        assertThrows(IllegalArgumentException.class, () -> VarInts.readUnsignedInt(
                new byte[]{(byte) 0x80, (byte) 0x80, (byte) 0x80,
                        (byte) 0x80, (byte) 0x80, 0x00}, 0));
    }

    @Test
    void rejectsNonCanonicalAndTruncatedEncoding() {
        assertThrows(IllegalArgumentException.class,
                () -> VarInts.readUnsignedInt(new byte[]{(byte) 0x81, 0x00}, 0));
        assertThrows(IllegalArgumentException.class,
                () -> VarInts.readUnsignedInt(new byte[]{(byte) 0x80}, 0));
    }

    @Test
    void rejectsInvalidOffset() {
        assertThrows(IllegalArgumentException.class,
                () -> VarInts.readUnsignedInt(new byte[]{0}, -1));
        assertThrows(IllegalArgumentException.class,
                () -> VarInts.readUnsignedInt(new byte[]{0}, 1));
    }
}
