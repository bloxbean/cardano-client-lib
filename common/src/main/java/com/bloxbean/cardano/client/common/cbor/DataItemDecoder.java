package com.bloxbean.cardano.client.common.cbor;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.DoublePrecisionFloat;
import co.nstant.in.cbor.model.HalfPrecisionFloat;
import co.nstant.in.cbor.model.LanguageTaggedString;
import co.nstant.in.cbor.model.NegativeInteger;
import co.nstant.in.cbor.model.Number;
import co.nstant.in.cbor.model.RationalNumber;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.SimpleValueType;
import co.nstant.in.cbor.model.SinglePrecisionFloat;
import co.nstant.in.cbor.model.Special;
import co.nstant.in.cbor.model.Tag;
import co.nstant.in.cbor.model.UnicodeString;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.common.cbor.custom.EncodedKeyMap;
import com.bloxbean.cardano.client.exception.CborRuntimeException;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Decodes CBOR bytes into the same {@link DataItem} trees as cbor-java 0.9's {@code CborDecoder} with its default
 * settings, without recursion: containers are frames on an explicit stack, so any nesting depth decodes on any thread.
 * <p>
 * Trees match cbor-java's: an indefinite array is {@code chunked} and ends with {@link Special#BREAK}; an indefinite map
 * is {@code chunked}; chunked byte and text strings are joined and not marked chunked; the innermost tag is attached to
 * the item and outer tags are chained on it; tag 30 becomes a {@link RationalNumber} and tag 38 a
 * {@link LanguageTaggedString}; untagged {@code false/true/null/undefined} are cbor-java's singletons; a repeated map
 * key keeps its first position and the last value. Maps are {@link EncodedKeyMap}s.
 * <p>
 * Input must be well-formed (RFC 8949 section 5.3), checked with the same head rules as {@link CborSpan}: unlike
 * cbor-java, a misplaced BREAK, a tag on BREAK and an invalid string chunk are rejected. Declared sizes are checked
 * against the remaining input before anything is allocated.
 */
final class DataItemDecoder {
    private static final int MAJOR_UNSIGNED = 0;
    private static final int MAJOR_NEGATIVE = 1;
    private static final int MAJOR_BYTES = 2;
    private static final int MAJOR_TEXT = 3;
    private static final int MAJOR_ARRAY = 4;
    private static final int MAJOR_MAP = 5;
    private static final int MAJOR_TAG = 6;
    private static final int BREAK = 0xff;
    private static final long TAG_RATIONAL = 30;
    private static final long TAG_LANGUAGE_STRING = 38;
    private static final BigInteger MINUS_ONE = BigInteger.ONE.negate();
    private static final long[] NO_TAGS = {};

    private final byte[] buffer;
    private final int limit;
    private final CborHead head = new CborHead();
    private int pos;

    private DataItemDecoder(byte[] buffer, int pos, int limit) {
        this.buffer = buffer;
        this.pos = pos;
        this.limit = limit;
    }

    /**
     * @return the first data item in the buffer; the items after it are decoded too, so they must be well-formed
     */
    static DataItem decodeFirst(byte[] buffer) {
        DataItemDecoder decoder = new DataItemDecoder(buffer, 0, buffer.length);
        if (buffer.length == 0)
            throw new CborRuntimeException("Cbor de-serialization error: no data item");
        DataItem first = decoder.decodeItem();
        while (decoder.pos < decoder.limit)
            decoder.decodeItem();
        return first;
    }

    /**
     * @return every data item in the buffer, in order
     */
    static List<DataItem> decodeAll(byte[] buffer) {
        DataItemDecoder decoder = new DataItemDecoder(buffer, 0, buffer.length);
        List<DataItem> items = new ArrayList<>(1);
        while (decoder.pos < decoder.limit)
            items.add(decoder.decodeItem());
        return items;
    }

    // One open container or indefinite string, with the tags read before its head.
    private static final class Frame {
        final Frame parent;
        final int major;
        final long[] tags;
        int remaining;             // items still to read, CborHead.INDEFINITE until BREAK
        Array array;
        EncodedKeyMap map;
        DataItem key;              // a map key waiting for its value
        ByteArrayOutputStream bytes;
        StringBuilder text;

        Frame(Frame parent, int major, long[] tags, int remaining) {
            this.parent = parent;
            this.major = major;
            this.tags = tags;
            this.remaining = remaining;
        }
    }

    private DataItem decodeItem() {
        Frame top = null; // the innermost open container; frames are chained through parent
        long[] tags = NO_TAGS;
        while (true) {
            DataItem item;
            if (top != null && top.remaining == 0) {
                item = close(top);
                top = top.parent;
            } else {
                if (pos >= limit)
                    throw CborHead.error("truncated CBOR: expected a data item", pos);
                if ((buffer[pos] & 0xff) == BREAK) {
                    if (top == null || top.remaining != CborHead.INDEFINITE || tags.length > 0)
                        throw CborHead.error("unexpected BREAK", pos);
                    if (top.key != null)
                        throw CborHead.error("BREAK after a map key without its value", pos);
                    pos++;
                    if (top.array != null)
                        top.array.add(Special.BREAK);
                    item = close(top);
                    top = top.parent;
                } else {
                    head.read(buffer, pos, limit);
                    if (top != null && (top.bytes != null || top.text != null)
                            && (head.major != top.major || head.indefinite))
                        throw CborHead.error("indefinite-length string chunk must be a definite string of the same major type", pos);
                    pos = head.end;
                    if (head.major == MAJOR_TAG) {
                        tags = Arrays.copyOf(tags, tags.length + 1);
                        tags[tags.length - 1] = head.argument;
                        continue;
                    }
                    if (top != null && top.remaining > 0)
                        top.remaining--;
                    Frame opened = open(top, tags);
                    if (opened != null) {
                        top = opened;
                        tags = NO_TAGS;
                        continue;
                    }
                    if (tags.length == 0) {
                        item = leaf(false);
                    } else {
                        item = applyTags(tags, leaf(true));
                        tags = NO_TAGS;
                    }
                }
            }

            if (top == null)
                return item;
            Frame parent = top;
            if (parent.array != null) {
                parent.array.add(item);
            } else if (parent.map != null) {
                if (parent.key == null) {
                    parent.key = item;
                } else {
                    parent.map.put(parent.key, item);
                    parent.key = null;
                }
            } else if (parent.bytes != null) {
                byte[] chunk = ((ByteString) item).getBytes();
                parent.bytes.write(chunk, 0, chunk.length);
            } else {
                parent.text.append(((UnicodeString) item).getString());
            }
        }
    }

    // Opens a container or indefinite string for the head just read, or returns null for a leaf.
    private Frame open(Frame parent, long[] tags) {
        switch (head.major) {
            case MAJOR_ARRAY: {
                int count = head.indefinite ? CborHead.INDEFINITE : CborHead.checkedCount(head, 1, pos, limit);
                Frame frame = new Frame(parent, MAJOR_ARRAY, tags, count);
                frame.array = new Array(Math.max(count, 0));
                frame.array.setChunked(head.indefinite);
                return frame;
            }
            case MAJOR_MAP: {
                int count = head.indefinite ? CborHead.INDEFINITE : CborHead.checkedCount(head, 2, pos, limit);
                Frame frame = new Frame(parent, MAJOR_MAP, tags, count);
                frame.map = new EncodedKeyMap(Math.max(count / 2, 0));
                frame.map.setChunked(head.indefinite);
                return frame;
            }
            case MAJOR_BYTES:
            case MAJOR_TEXT:
                if (!head.indefinite)
                    return null;
                Frame frame = new Frame(parent, head.major, tags, CborHead.INDEFINITE);
                if (head.major == MAJOR_BYTES)
                    frame.bytes = new ByteArrayOutputStream();
                else
                    frame.text = new StringBuilder();
                return frame;
            default:
                return null;
        }
    }

    private static DataItem close(Frame frame) {
        DataItem item;
        if (frame.array != null)
            item = frame.array;
        else if (frame.map != null)
            item = frame.map;
        else if (frame.bytes != null)
            item = new ByteString(frame.bytes.toByteArray());
        else
            item = new UnicodeString(frame.text.toString());
        return frame.tags.length == 0 ? item : applyTags(frame.tags, item);
    }

    // A leaf for the head just read (integer, definite string or simple value).
    private DataItem leaf(boolean tagged) {
        switch (head.major) {
            case MAJOR_UNSIGNED:
                return new UnsignedInteger(unsigned(head.argument));
            case MAJOR_NEGATIVE:
                return new NegativeInteger(MINUS_ONE.subtract(unsigned(head.argument)));
            case MAJOR_BYTES: {
                int start = pos;
                pos = CborHead.checkedStringEnd(head, pos, limit);
                return new ByteString(Arrays.copyOfRange(buffer, start, pos));
            }
            case MAJOR_TEXT: {
                int start = pos;
                pos = CborHead.checkedStringEnd(head, pos, limit);
                return new UnicodeString(new String(buffer, start, pos - start, StandardCharsets.UTF_8));
            }
            default:
                return simple(tagged);
        }
    }

    private DataItem simple(boolean tagged) {
        switch (head.info) {
            case 20:
                return tagged ? new SimpleValue(SimpleValueType.FALSE) : SimpleValue.FALSE;
            case 21:
                return tagged ? new SimpleValue(SimpleValueType.TRUE) : SimpleValue.TRUE;
            case 22:
                return tagged ? new SimpleValue(SimpleValueType.NULL) : SimpleValue.NULL;
            case 23:
                return tagged ? new SimpleValue(SimpleValueType.UNDEFINED) : SimpleValue.UNDEFINED;
            case 25:
                return new HalfPrecisionFloat(halfToFloat((int) head.argument));
            case 26:
                return new SinglePrecisionFloat(Float.intBitsToFloat((int) head.argument));
            case 27:
                return new DoublePrecisionFloat(Double.longBitsToDouble(head.argument));
            default: // 0..19 directly, 24 in the next byte
                return new SimpleValue((int) head.argument);
        }
    }

    /**
     * Applies tags innermost first, as cbor-java does: tag 30 turns the item into a {@link RationalNumber}, tag 38 into a
     * {@link LanguageTaggedString}, and any other tag is chained outside the item's existing tags.
     */
    private static DataItem applyTags(long[] tags, DataItem item) {
        DataItem outermost = item;
        for (int i = tags.length - 1; i >= 0; i--) {
            if (tags[i] == TAG_RATIONAL) {
                item = rational(item);
                outermost = item.getTag();
            } else if (tags[i] == TAG_LANGUAGE_STRING) {
                item = languageTaggedString(item);
                outermost = item.getTag();
            } else {
                Tag tag = new Tag(tags[i]);
                outermost.setTag(tag);
                outermost = tag;
            }
        }
        return item;
    }

    private static RationalNumber rational(DataItem item) {
        List<DataItem> items = item instanceof Array ? ((Array) item).getDataItems() : List.of();
        if (items.size() != 2 || !(items.get(0) instanceof Number) || !(items.get(1) instanceof Number))
            throw new CborRuntimeException("CBOR tag 30 (rational number) must wrap an array of two integers");
        try {
            return new RationalNumber((Number) items.get(0), (Number) items.get(1));
        } catch (CborException e) {
            throw new CborRuntimeException("CBOR tag 30 (rational number): " + e.getMessage(), e);
        }
    }

    private static LanguageTaggedString languageTaggedString(DataItem item) {
        List<DataItem> items = item instanceof Array ? ((Array) item).getDataItems() : List.of();
        if (items.size() != 2 || !(items.get(0) instanceof UnicodeString) || !(items.get(1) instanceof UnicodeString))
            throw new CborRuntimeException("CBOR tag 38 (language-tagged string) must wrap an array of two text strings");
        return new LanguageTaggedString((UnicodeString) items.get(0), (UnicodeString) items.get(1));
    }

    private static BigInteger unsigned(long argument) {
        return argument >= 0 ? BigInteger.valueOf(argument) : new BigInteger(Long.toUnsignedString(argument));
    }

    // Same conversion as cbor-java's HalfPrecisionFloatDecoder.
    private static float halfToFloat(int bits) {
        int s = (bits & 0x8000) >> 15;
        int e = (bits & 0x7C00) >> 10;
        int f = bits & 0x03FF;
        if (e == 0)
            return (float) ((s != 0 ? -1 : 1) * Math.pow(2, -14) * (f / Math.pow(2, 10)));
        else if (e == 0x1F)
            return f != 0 ? Float.NaN : (s != 0 ? -1 : 1) * Float.POSITIVE_INFINITY;
        return (float) ((s != 0 ? -1 : 1) * Math.pow(2, e - 15) * (1 + f / Math.pow(2, 10)));
    }
}
