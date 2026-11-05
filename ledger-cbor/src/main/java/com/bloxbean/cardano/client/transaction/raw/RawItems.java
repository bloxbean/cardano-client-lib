package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.spec.UnitInterval;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.bloxbean.cardano.client.transaction.raw.RawTx.error;

/**
 * Reading one item of a ledger record. Tags are looked through, as the models' decoders always have (ADR 0003 D5): a
 * policy that cares sees them on the span. Strings may be chunked and integers non-minimal.
 */
final class RawItems {
    private static final long RATIONAL = 30;

    private RawItems() {
    }

    static CborSpan content(CborSpan item) {
        CborSpan content = item;
        while (content.tag() != -1)
            content = content.untag();
        return content;
    }

    static List<CborSpan> array(CborSpan item, String what) {
        CborSpan content = content(item);
        if (content.majorType() != 4)
            throw error("A " + what + " is an array", item);
        return content.items();
    }

    static List<Map.Entry<CborSpan, CborSpan>> map(CborSpan item, String what) {
        CborSpan content = content(item);
        if (content.majorType() != 5)
            throw error("A " + what + " is a map", item);
        return content.entries();
    }

    static byte[] bytes(CborSpan item) {
        return content(item).byteString();
    }

    static String text(CborSpan item) {
        return content(item).text();
    }

    /** @return the value, or null for an untagged null */
    static <T> T nullable(CborSpan item, Function<CborSpan, T> read) {
        return item.isNull() ? null : read.apply(item);
    }

    /** @return an unsigned integer that fits an int */
    static int smallUnsigned(CborSpan item) {
        BigInteger value = unsigned(item);
        if (value.bitLength() >= Integer.SIZE)
            throw error("Expected an unsigned integer that fits an int, found " + value, item);
        return value.intValue();
    }

    /** @return the 64 bits of an unsigned integer: a value of 2^63 or more reads as negative */
    static long unsignedLong(CborSpan item) {
        return unsigned(item).longValue();
    }

    private static BigInteger unsigned(CborSpan item) {
        CborSpan content = content(item);
        if (content.majorType() != 0)
            throw error("Expected an unsigned integer", item);
        return content.asBigInteger();
    }

    private static BigInteger integer(CborSpan item) {
        CborSpan content = item;
        while (content.tag() != -1 && content.tag() != 2 && content.tag() != 3)
            content = content.untag();
        return content.asBigInteger();
    }

    /**
     * What the models' decoders have always read at a position of a number: {@link CborSerializationUtil#getBigInteger}
     * looks through tags and returns null for an item that is no number. Kept as they keep it (ADR 0003 D7).
     */
    private static BigInteger lenientInteger(CborSpan item) {
        CborSpan content = content(item);
        return content.majorType() <= 1 ? content.asBigInteger()
                : CborSerializationUtil.getBigInteger(CborSerializationUtil.deserialize(item.bytes()));
    }

    /** @return an integer or a bignum; lenient, what {@link #lenientInteger} reads */
    static BigInteger integer(CborSpan item, boolean lenient) {
        return lenient ? lenientInteger(item) : integer(item);
    }

    /** @return a rational, tag 30 on {@code [int, int]} */
    static UnitInterval rational(CborSpan item, boolean lenient) {
        boolean tagged = false;
        CborSpan content = item;
        while (content.tag() != -1) {
            tagged |= content.tag() == RATIONAL;
            content = content.untag();
        }
        if (!tagged || content.majorType() != 4 || content.size() != 2)
            throw error("A rational is tag 30 on an array of 2 items", item);
        return new UnitInterval(integer(content.get(0), lenient), integer(content.get(1), lenient));
    }
}
