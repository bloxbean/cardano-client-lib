package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.transaction.raw.LedgerDecodingException.Reason;

/**
 * Plutus {@code Data} well-formedness, as plutus-core's {@code decodeData} (plutus 1.65.0.0,
 * {@code PlutusCore/Data.hs:209-300}) over cborg. Part of the Haskell-strict policy (ADR 0005); CCL's
 * {@code plutus.spec.PlutusData} model stays lenient.
 *
 * <p>The rules: integers (major 0 or 1, or bignums tagged 2/3 whose byte string follows the byte-string rule), byte
 * strings definite of at most 64 bytes or indefinite with definite chunks of at most 64 bytes each
 * ({@code decodeBoundedBytes}), lists and maps definite or indefinite, constructors tagged 121–127, 1280–1400 (a list
 * of fields) or 102 ({@code [Word64, fields]}); anything else (text, floats, simple values, other tags) is refused.</p>
 *
 * <p>Two entry points, as callers decode differently:</p>
 * <ul>
 *   <li>{@link #validate}: the bytes are one {@code Data} and nothing else. The ledger's datums and redeemer data are
 *       items of the transaction ({@code DecCBOR (PlutusData era) = fromPlainDecoder Cborg.decode}, cardano-ledger-core
 *       Plutus/Data.hs:99-103), and an inline datum is decoded with {@code decodeFull'}, which refuses leftover bytes
 *       ({@code makeBinaryData}, :154-173, after the definite tag-24 byte string of {@code decodeNestedCborBytes}).</li>
 *   <li>{@link #validateFirst}: a {@code Data} constant of a Plutus script ({@code FlatViaSerialise}), decoded with
 *       serialise's {@code deserialiseOrFail} (Codec/Serialise.hs:134-143), which stops after the first item and
 *       ignores whatever follows it.</li>
 * </ul>
 *
 * <p>The walk keeps an explicit stack, so {@code Data} of any nesting depth is judged in linear time on any
 * thread. Every rejection is a {@link LedgerDecodingException} with {@link Reason#PLUTUS_DATA}.</p>
 */
public final class PlutusDataValidator {

    private static final int MAX_CHUNK = 64;
    private static final long TAG_GENERAL_CONSTR = 102;
    private static final int BIGNUM_POSITIVE_HEAD = 0xc2;
    private static final int BIGNUM_NEGATIVE_HEAD = 0xc3;

    private PlutusDataValidator() {
    }

    /**
     * @param bytes the CBOR of one {@code Data} item, nothing after it
     * @throws LedgerDecodingException when plutus-core would not decode it
     */
    public static void validate(byte[] bytes) {
        validate(new HeadCursor(bytes, 0, bytes.length, Reason.PLUTUS_DATA));
    }

    /**
     * @param item one {@code Data} item of a transaction, as encoded
     * @throws LedgerDecodingException when plutus-core would not decode it
     */
    static void validate(CborSpan item) {
        validate(new HeadCursor(item.buffer(), item.offset(), item.offset() + item.length(), Reason.PLUTUS_DATA));
    }

    private static void validate(HeadCursor cursor) {
        data(cursor);
        if (!cursor.atEnd()) {
            throw cursor.reject("Data: trailing bytes");
        }
    }

    /**
     * @param bytes the CBOR of a {@code Data} item, possibly followed by other bytes, which are ignored
     * @throws LedgerDecodingException when plutus-core would not decode the leading item
     */
    public static void validateFirst(byte[] bytes) {
        data(new HeadCursor(bytes, 0, bytes.length, Reason.PLUTUS_DATA));
    }

    /** Reads one {@code Data} item. */
    private static void data(HeadCursor c) {
        HeadCursor.Containers open = new HeadCursor.Containers();
        do {
            item(c, open);
        } while (open.next(c));
    }

    /** Reads one item: a leaf in full, or a container's head with a frame for its items. */
    private static void item(HeadCursor c, HeadCursor.Containers open) {
        int initial = c.peek();
        int major = initial >>> 5;
        switch (major) {
            case 0:
            case 1:
                c.next();
                break;
            case 2:
                boundedBytes(c);
                break;
            case 4:
            case 5:
                open.open(major, c.containerHeader(major));
                break;
            case 6:
                tagged(c, open, initial);
                break;
            default:
                throw c.reject("Data: unrecognized value of major type " + major);
        }
    }

    private static void tagged(HeadCursor c, HeadCursor.Containers open, int initial) {
        if ((initial & 0x1f) == 31) {
            throw c.reject("Data: malformed tag");
        }
        long tag = c.next().argument();
        // cborg reports TypeInteger only for the one-byte heads c2/c3; a longer tag head is TypeTag, and decodeConstr
        // rejects tags 2 and 3.
        if (initial == BIGNUM_POSITIVE_HEAD || initial == BIGNUM_NEGATIVE_HEAD) {
            if (c.peek() >>> 5 != 2) {
                throw c.reject("Bignum must contain a byte string");
            }
            boundedBytes(c);
        } else if (tag == TAG_GENERAL_CONSTR) {
            long n = c.containerHeader(4);
            if (c.peek() >>> 5 != 0) {
                throw c.reject("Data: constructor index is not a Word64");
            }
            c.next();
            // [index, fields]: the index is read here, the fields list on top of the sealed array
            open.seal(n, 2);
            open.open(4, c.containerHeader(4));
        } else if ((tag >= 121 && tag <= 127) || (tag >= 1280 && tag <= 1400)) {
            open.open(4, c.containerHeader(4));
        } else {
            throw c.reject("Unrecognized tag " + Long.toUnsignedString(tag));
        }
    }

    /** {@code decodeBoundedBytes} / {@code decodeBoundedBytesIndef}: every chunk at most 64 bytes. */
    private static void boundedBytes(HeadCursor c) {
        if ((c.peek() & 0x1f) == 31) {
            c.next();
            while (!c.atBreak()) {
                int chunk = c.peek();
                if (chunk >>> 5 != 2 || (chunk & 0x1f) == 31) {
                    throw c.reject("Data: an indefinite byte string chunk must be a definite byte string");
                }
                bounded(c, c.definiteString(2));
            }
        } else {
            bounded(c, c.definiteString(2));
        }
    }

    private static void bounded(HeadCursor c, byte[] bytes) {
        if (bytes.length > MAX_CHUNK) {
            throw c.reject("ByteString exceeds 64 bytes");
        }
    }
}
