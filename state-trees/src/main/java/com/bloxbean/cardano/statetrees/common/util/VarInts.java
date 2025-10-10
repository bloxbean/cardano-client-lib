package com.bloxbean.cardano.statetrees.common.util;

import java.io.ByteArrayOutputStream;

/**
 * Minimal unsigned LEB128-style varint utilities used by JMT node key encoding.
 */
public final class VarInts {

    private VarInts() {
        throw new AssertionError("Utility class");
    }

    /**
     * Writes {@code value} as an unsigned variable-length integer to the provided stream.
     */
    public static void writeUnsignedInt(int value, ByteArrayOutputStream out) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be >= 0");
        }
        int v = value;
        while ((v & ~0x7F) != 0) {
            out.write((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.write(v & 0x7F);
    }

    /**
     * Reads an unsigned variable-length integer starting at {@code offset}.
     */
    public static ReadResult readUnsignedInt(byte[] bytes, int offset) {
        if (bytes == null) throw new IllegalArgumentException("bytes cannot be null");
        int idx = offset;
        int shift = 0;
        int value = 0;
        while (idx < bytes.length) {
            int b = bytes[idx++] & 0xFF;
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return new ReadResult(value, idx);
            }
            shift += 7;
            if (shift > 28) {
                throw new IllegalArgumentException("VarInt too long");
            }
        }
        throw new IllegalArgumentException("Truncated VarInt at offset " + offset);
    }

    public static final class ReadResult {
        private final int value;
        private final int nextOffset;

        public ReadResult(int value, int nextOffset) {
            this.value = value;
            this.nextOffset = nextOffset;
        }

        public int value() {
            return value;
        }

        public int nextOffset() {
            return nextOffset;
        }
    }
}

