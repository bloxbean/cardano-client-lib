package com.bloxbean.cardano.client.transaction.raw;

import co.nstant.in.cbor.model.SimpleValue;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.common.cbor.custom.EncodedKeyMap;
import com.bloxbean.cardano.client.spec.UnitInterval;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.bloxbean.cardano.client.transaction.raw.RawTx.error;

/**
 * Reading one item of a ledger record. Tags are looked through, as the models' decoders always have (ADR 0003 D5): a
 * policy that cares sees them on the span. Strings may be chunked and integers non-minimal.
 */
public final class RawItems {
    private static final long RATIONAL = 30;

    private RawItems() {
    }

    public static CborSpan content(CborSpan item) {
        CborSpan content = item;
        while (content.isTagged())
            content = content.untag();
        return content;
    }

    public static List<CborSpan> array(CborSpan item, String what) {
        CborSpan content = content(item);
        if (content.majorType() != 4)
            throw error("A " + what + " is an array", item);
        return content.items();
    }

    public static List<Map.Entry<CborSpan, CborSpan>> map(CborSpan item, String what) {
        CborSpan content = content(item);
        if (content.majorType() != 5)
            throw error("A " + what + " is a map", item);
        return content.entries();
    }

    /**
     * The entries of a record as the models read it: a map, tags looked through, whose keys of any kind are all
     * distinct. Keys are equal as a decoded map's are (D7): by value, and with the same tags.
     */
    static List<Map.Entry<CborSpan, CborSpan>> record(CborSpan item, String what) {
        List<Map.Entry<CborSpan, CborSpan>> entries = map(item, what);
        if (repeatsKey(entries))
            throw error("The " + what + " repeats a key", item);
        return entries;
    }

    /**
     * The entries of a record read as a decoded map reads it, last entry first, so that looking up a repeated key finds
     * its last value. Tags are looked through.
     */
    static List<Map.Entry<CborSpan, CborSpan>> lastFirst(CborSpan item, String what) {
        List<Map.Entry<CborSpan, CborSpan>> entries = new ArrayList<>(map(item, what));
        Collections.reverse(entries);
        return entries;
    }

    private static boolean repeatsKey(List<Map.Entry<CborSpan, CborSpan>> entries) {
        long seen = 0;
        for (Map.Entry<CborSpan, CborSpan> entry : entries) {
            CborSpan key = entry.getKey();
            // any key but the usual one byte unsigned ones is compared as a decoded map compares it
            int value = key.length() == 1 ? key.buffer()[key.offset()] & 0xff : Integer.MAX_VALUE;
            if (value >= 24)
                return repeatsDecodedKey(entries);
            if ((seen & (1L << value)) != 0)
                return true;
            seen |= 1L << value;
        }
        return false;
    }

    private static boolean repeatsDecodedKey(List<Map.Entry<CborSpan, CborSpan>> entries) {
        EncodedKeyMap decoded = new EncodedKeyMap(entries.size());
        for (Map.Entry<CborSpan, CborSpan> entry : entries)
            decoded.put(CborSerializationUtil.deserialize(entry.getKey().bytes()), SimpleValue.NULL);
        return decoded.hasRepeatedKey();
    }

    /**
     * The entries of a map with byte string keys, one per key: a repeated key keeps its first position and its last
     * value, as a decoded map and the ledger's map decoder before Conway keep them, and the span still has every entry.
     * Keys are equal as a decoded map's are: the same bytes, however chunked, and the same tags.
     */
    static List<Map.Entry<CborSpan, CborSpan>> distinct(CborSpan item, String what) {
        List<Map.Entry<CborSpan, CborSpan>> entries = map(item, what);
        if (entries.size() < 2)
            return entries;
        Map<Object, Map.Entry<CborSpan, CborSpan>> distinct = new LinkedHashMap<>(2 * entries.size());
        for (Map.Entry<CborSpan, CborSpan> entry : entries)
            distinct.put(identity(entry.getKey()), entry);
        return new ArrayList<>(distinct.values());
    }

    // What makes two byte string keys equal.
    static Object identity(CborSpan key) {
        if (!key.isTagged())
            return ByteBuffer.wrap(key.byteString());
        List<Object> tagsAndBytes = new ArrayList<>();
        CborSpan content = key;
        while (content.isTagged()) {
            tagsAndBytes.add(content.tag());
            content = content.untag();
        }
        tagsAndBytes.add(ByteBuffer.wrap(content.byteString()));
        return tagsAndBytes;
    }

    public static byte[] bytes(CborSpan item) {
        return content(item).byteString();
    }

    public static String text(CborSpan item) {
        return content(item).text();
    }

    /** @return the value, or null for an untagged null */
    public static <T> T nullable(CborSpan item, Function<CborSpan, T> read) {
        return item.isNull() ? null : read.apply(item);
    }

    /** @return an unsigned integer that fits an int */
    public static int smallUnsigned(CborSpan item) {
        BigInteger value = unsigned(item);
        if (value.bitLength() >= Integer.SIZE)
            throw error("Expected an unsigned integer that fits an int, found " + value, item);
        return value.intValue();
    }

    /** @return the 64 bits of an unsigned integer: a value of 2^63 or more reads as negative */
    public static long unsignedLong(CborSpan item) {
        return unsigned(item).longValue();
    }

    /** @return an unsigned integer of any size */
    public static BigInteger unsigned(CborSpan item) {
        CborSpan content = content(item);
        if (content.majorType() != 0)
            throw error("Expected an unsigned integer", item);
        return content.asBigInteger();
    }

    private static BigInteger integer(CborSpan item) {
        CborSpan content = item;
        while (content.isTagged() && content.tag() != 2 && content.tag() != 3)
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
    public static BigInteger integer(CborSpan item, boolean lenient) {
        return lenient ? lenientInteger(item) : integer(item);
    }

    /** @return a rational, tag 30 on {@code [int, int]} */
    public static UnitInterval rational(CborSpan item, boolean lenient) {
        boolean tagged = false;
        CborSpan content = item;
        while (content.isTagged()) {
            tagged |= content.tag() == RATIONAL;
            content = content.untag();
        }
        if (!tagged || content.majorType() != 4 || content.size() != 2)
            throw error("A rational is tag 30 on an array of 2 items", item);
        return new UnitInterval(integer(content.get(0), lenient), integer(content.get(1), lenient));
    }
}
