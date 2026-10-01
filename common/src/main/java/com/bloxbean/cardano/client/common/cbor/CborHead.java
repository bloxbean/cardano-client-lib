package com.bloxbean.cardano.client.common.cbor;

import com.bloxbean.cardano.client.exception.CborRuntimeException;

/**
 * The head of one CBOR data item: major type, additional information, argument and where it ends. One place for the
 * RFC 8949 section 5.3 head rules, shared by the {@link CborSpan} walker and the {@link DataItemDecoder}. BREAK
 * ({@code ff}) is handled by callers.
 */
final class CborHead {
    static final int INDEFINITE = -1;

    private static final int MAJOR_BYTES = 2;
    private static final int MAJOR_MAP = 5;
    private static final int MAJOR_SIMPLE = 7;

    int start;
    int major;
    int info;
    long argument; // value, length, count, tag number, or simple/float bits; unsigned 64-bit
    boolean indefinite;
    int end;

    void read(byte[] buffer, int pos, int limit) {
        if (pos >= limit)
            throw error("truncated CBOR: missing header", pos);
        start = pos;
        int initial = buffer[pos++] & 0xff;
        major = initial >>> 5;
        info = initial & 0x1f;
        indefinite = false;
        if (info < 24) {
            argument = info;
        } else if (info <= 27) {
            int size = 1 << (info - 24);
            if (size > limit - pos)
                throw error("truncated CBOR header argument", start);
            long value = 0;
            for (int i = 0; i < size; i++)
                value = (value << 8) | (buffer[pos++] & 0xff);
            argument = value;
        } else if (info == 31 && major >= MAJOR_BYTES && major <= MAJOR_MAP) {
            indefinite = true;
            argument = INDEFINITE;
        } else if (info == 31) {
            throw error(major == MAJOR_SIMPLE ? "unexpected BREAK" : "indefinite length is not allowed for major type " + major, start);
        } else {
            throw error("reserved CBOR additional information " + info, start);
        }
        end = pos;
    }

    /**
     * @return the end of a definite string whose head this is; the declared length must fit the remaining input
     */
    static int checkedStringEnd(CborHead head, int pos, int limit) {
        if (head.argument < 0 || head.argument > limit - pos)
            throw error("truncated CBOR string: declared length " + Long.toUnsignedString(head.argument)
                    + " exceeds the remaining " + (limit - pos) + " bytes", head.start);
        return pos + (int) head.argument;
    }

    /**
     * Every item takes at least one byte, so a count larger than the remaining input is rejected before anything is
     * allocated for it.
     *
     * @return the number of items (map pairs count twice)
     */
    static int checkedCount(CborHead head, int itemsPerEntry, int pos, int limit) {
        if (head.argument < 0 || head.argument > (limit - pos) / itemsPerEntry)
            throw error("declared CBOR " + (itemsPerEntry == 1 ? "array" : "map") + " size "
                    + Long.toUnsignedString(head.argument) + " exceeds the remaining " + (limit - pos) + " bytes", head.start);
        return (int) head.argument * itemsPerEntry;
    }

    static CborRuntimeException error(String reason, int offset) {
        return new CborRuntimeException(reason + " at offset " + offset);
    }
}
