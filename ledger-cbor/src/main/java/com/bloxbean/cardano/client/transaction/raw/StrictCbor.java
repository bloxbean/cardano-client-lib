package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.transaction.raw.LedgerDecodingException.Reason;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Typed reads of {@link CborSpan}s with the strictness of Haskell's decoders, for {@link HaskellPolicy}.
 *
 * <p>{@code CborSpan} navigates the original bytes but its scalar accessors are deliberately lenient (an integer may be
 * negative or a bignum, a byte string may be chunked, a type is read through tags). Haskell's decoders are not: an
 * unsigned field is an untagged major-0 integer, hashes and keys are definite-length byte strings below decoder
 * version 12 ({@code decodeBytesDefinite}, cardano-ledger-binary Decoding/Decoder.hs:350-361), and a record has exactly
 * its fields. Every read here checks that and fails with a {@link LedgerDecodingException} otherwise; a tag is never
 * looked through.</p>
 */
final class StrictCbor {

    private static final int MAJOR_UNSIGNED = 0;
    private static final int MAJOR_NEGATIVE = 1;
    private static final int MAJOR_BYTES = 2;
    private static final int MAJOR_TEXT = 3;
    private static final int MAJOR_ARRAY = 4;
    private static final int MAJOR_MAP = 5;
    private static final int SET_TAG = 258;
    private static final BigInteger TWO_64 = BigInteger.ONE.shiftLeft(64);

    private StrictCbor() {
    }

    /**
     * @param bytes exactly one well-formed CBOR item
     * @return its span
     * @throws LedgerDecodingException ({@link Reason#NOT_WELL_FORMED}) otherwise
     */
    static CborSpan span(byte[] bytes) {
        try {
            return CborSpan.of(bytes);
        } catch (CborRuntimeException e) {
            throw new LedgerDecodingException(Reason.NOT_WELL_FORMED, e.getMessage(), e);
        }
    }

    /** @return the major type of the item's first head: 6 for a tagged item */
    static int major(CborSpan item) {
        return (item.buffer()[item.offset()] & 0xff) >>> 5;
    }

    /** @return the items of an untagged array, definite or indefinite */
    static List<CborSpan> array(CborSpan item) {
        expect(item, MAJOR_ARRAY, "an array");
        return item.items();
    }

    /** @return the items of an untagged array of exactly {@code size} items, else fails with {@code reason} */
    static List<CborSpan> array(CborSpan item, int size, Reason reason, String message) {
        List<CborSpan> items = array(item);
        if (items.size() != size) {
            throw new LedgerDecodingException(reason, message);
        }
        return items;
    }

    /** @return the entries of an untagged map, definite or indefinite, in encoded order with any repeated key */
    static List<Map.Entry<CborSpan, CborSpan>> map(CborSpan item) {
        expect(item, MAJOR_MAP, "a map");
        return item.entries();
    }

    /** @return the elements of a set: an array, optionally in tag 258 ({@code decodeSet} from version 9) */
    static List<CborSpan> set(CborSpan item) {
        return array(item.untagIf(SET_TAG));
    }

    /** @return the value of an untagged unsigned integer, up to 2^64 - 1 */
    static BigInteger unsigned(CborSpan item) {
        expect(item, MAJOR_UNSIGNED, "an unsigned integer");
        long value = argument(item);
        return value >= 0 ? BigInteger.valueOf(value) : BigInteger.valueOf(value).add(TWO_64);
    }

    /** @return the 64 bits of an untagged unsigned integer, unsigned, so that checking one allocates nothing */
    static long word64(CborSpan item) {
        expect(item, MAJOR_UNSIGNED, "an unsigned integer");
        return argument(item);
    }

    /** @return the value of an untagged unsigned integer that fits 63 bits */
    static long unsignedLong(CborSpan item) {
        expect(item, MAJOR_UNSIGNED, "an unsigned integer");
        long value = argument(item);
        if (value < 0) {
            throw new LedgerDecodingException(Reason.INTEGER_RANGE, "unsigned integer " + unsigned(item)
                    + " does not fit 63 bits at " + item.offset());
        }
        return value;
    }

    /** @return the value of an untagged major-0 or major-1 integer (not a bignum) */
    static BigInteger integer(CborSpan item) {
        int major = major(item);
        if (major == MAJOR_UNSIGNED) {
            return unsigned(item);
        }
        if (major != MAJOR_NEGATIVE) {
            throw unexpected(item, "an integer");
        }
        long value = argument(item);
        BigInteger magnitude = value >= 0 ? BigInteger.valueOf(value) : BigInteger.valueOf(value).add(TWO_64);
        return magnitude.negate().subtract(BigInteger.ONE);
    }

    /** @return the content of an untagged byte string, the chunks of an indefinite one joined */
    static byte[] bytes(CborSpan item) {
        expect(item, MAJOR_BYTES, "a byte string");
        return item.byteString();
    }

    /** @return the content of an untagged definite-length byte string */
    static byte[] definiteBytes(CborSpan item) {
        definiteLength(item);
        return item.byteString();
    }

    /** @return the content's length of an untagged definite-length byte string, which is not copied */
    static int definiteLength(CborSpan item) {
        expect(item, MAJOR_BYTES, "a byte string");
        if (item.isIndefinite()) {
            throw new LedgerDecodingException(Reason.INDEFINITE_STRING, "an indefinite-length byte string at "
                    + item.offset() + " (definite only below decoder version 12)");
        }
        return item.length() - item.headerLength();
    }

    /** @return the UTF-8 bytes of an untagged definite-length text string (cborg's {@code decodeString}) */
    static byte[] definiteText(CborSpan item) {
        expect(item, MAJOR_TEXT, "a text string");
        if (item.isIndefinite()) {
            throw new LedgerDecodingException(Reason.INDEFINITE_STRING, "an indefinite-length text string at "
                    + item.offset());
        }
        return payload(item);
    }

    private static byte[] payload(CborSpan item) {
        int start = item.offset() + item.headerLength();
        byte[] out = new byte[item.length() - item.headerLength()];
        System.arraycopy(item.buffer(), start, out, 0, out.length);
        return out;
    }

    private static void expect(CborSpan item, int major, String what) {
        if (major(item) != major) {
            throw unexpected(item, what);
        }
    }

    private static LedgerDecodingException unexpected(CborSpan item, String what) {
        return new LedgerDecodingException(Reason.UNEXPECTED_TYPE, "expected " + what + " at " + item.offset()
                + ", found initial byte 0x" + Integer.toHexString(item.buffer()[item.offset()] & 0xff));
    }

    /** The head's argument as unsigned 64 bits; {@code item} is an untagged integer (checked by the caller). */
    private static long argument(CborSpan item) {
        byte[] buffer = item.buffer();
        int offset = item.offset();
        int info = buffer[offset] & 0x1f;
        if (info < 24) {
            return info;
        }
        int size = 1 << (info - 24);
        long value = 0;
        for (int i = 1; i <= size; i++) {
            value = (value << 8) | (buffer[offset + i] & 0xff);
        }
        return value;
    }
}
