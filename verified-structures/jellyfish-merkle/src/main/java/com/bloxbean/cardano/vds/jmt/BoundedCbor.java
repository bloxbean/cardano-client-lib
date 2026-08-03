package com.bloxbean.cardano.vds.jmt;

/** Allocation-free canonical structural preflight for CBOR handed to the object decoder. */
public final class BoundedCbor {

    private BoundedCbor() {
        throw new AssertionError("Utility class");
    }

    /**
     * Validates one definite-length CBOR item without allocating from declared container sizes.
     * Stable JMT encodings never use indefinite-length items, so those are rejected.
     */
    public static void validateSingleItem(byte[] encoded,
                                          int maxDepth,
                                          int maxItems,
                                          int maxByteStringLength) {
        if (encoded == null) {
            throw new IllegalArgumentException("CBOR bytes cannot be null");
        }
        if (maxDepth < 0 || maxItems <= 0 || maxByteStringLength < 0) {
            throw new IllegalArgumentException("Invalid CBOR limits");
        }
        Cursor cursor = new Cursor(encoded, maxDepth, maxItems, maxByteStringLength);
        cursor.readItem(0);
        if (cursor.offset != encoded.length) {
            throw new IllegalArgumentException("Trailing CBOR data");
        }
    }

    private static final class Cursor {
        private final byte[] encoded;
        private final int maxDepth;
        private final int maxItems;
        private final int maxByteStringLength;
        private int offset;
        private int items;

        private Cursor(byte[] encoded, int maxDepth, int maxItems, int maxByteStringLength) {
            this.encoded = encoded;
            this.maxDepth = maxDepth;
            this.maxItems = maxItems;
            this.maxByteStringLength = maxByteStringLength;
        }

        private void readItem(int depth) {
            if (depth > maxDepth) {
                throw new IllegalArgumentException("CBOR nesting exceeds limit");
            }
            if (++items > maxItems) {
                throw new IllegalArgumentException("CBOR item count exceeds limit");
            }
            int initial = readByte();
            int major = initial >>> 5;
            int additional = initial & 0x1F;
            if (additional == 31) {
                throw new IllegalArgumentException("Indefinite-length CBOR is not supported");
            }
            long argument = readArgument(additional);
            requireCanonicalArgument(additional, argument);
            switch (major) {
                case 0:
                    return;
                case 2:
                    if (argument > maxByteStringLength || argument > encoded.length - offset) {
                        throw new IllegalArgumentException("CBOR string length exceeds limit/input");
                    }
                    offset += (int) argument;
                    return;
                case 4:
                    readChildren(argument, depth, 1);
                    return;
                default:
                    throw new IllegalArgumentException("Unsupported CBOR major type for JMT");
            }
        }

        private void requireCanonicalArgument(int additional, long value) {
            if ((additional == 24 && value < 24)
                    || (additional == 25 && value <= 0xFFL)
                    || (additional == 26 && value <= 0xFFFFL)
                    || (additional == 27 && value <= 0xFFFFFFFFL)) {
                throw new IllegalArgumentException("Non-canonical CBOR integer/length encoding");
            }
        }

        private void readChildren(long count, int depth, int multiplier) {
            if (count > maxItems || count > (maxItems - items) / multiplier) {
                throw new IllegalArgumentException("CBOR container item count exceeds limit");
            }
            int childCount = (int) count * multiplier;
            for (int i = 0; i < childCount; i++) {
                readItem(depth + 1);
            }
        }

        private long readArgument(int additional) {
            if (additional < 24) {
                return additional;
            }
            int width;
            switch (additional) {
                case 24:
                    width = 1;
                    break;
                case 25:
                    width = 2;
                    break;
                case 26:
                    width = 4;
                    break;
                case 27:
                    width = 8;
                    break;
                default:
                    throw new IllegalArgumentException("Reserved CBOR additional information");
            }
            long value = 0;
            for (int i = 0; i < width; i++) {
                int next = readByte();
                if (value > (Long.MAX_VALUE - next) / 256L) {
                    throw new IllegalArgumentException("CBOR length exceeds signed range");
                }
                value = value * 256L + next;
            }
            return value;
        }

        private int readByte() {
            if (offset >= encoded.length) {
                throw new IllegalArgumentException("Truncated CBOR input");
            }
            return encoded[offset++] & 0xFF;
        }
    }
}
