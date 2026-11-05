package com.bloxbean.cardano.client.common.cbor;

import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.util.HexUtil;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One complete CBOR data item inside an original byte buffer: {@code (buffer, offset, length)}.
 * <p>
 * A span never re-encodes anything. {@link #bytes()} returns exactly the encoded bytes of the item, so hashes computed
 * from spans match the bytes that were signed or hashed on chain, whatever the encoding (indefinite lengths, chunked
 * strings, non-minimal integers and lengths, map key order and duplicate keys are all kept as encoded).
 * <p>
 * The walker ({@link #skip(byte[], int, int)}) is iterative: it uses an explicit frame stack instead of recursion, so
 * any nesting depth is handled without {@link StackOverflowError}. It checks that the input is well-formed CBOR
 * (RFC 8949 section 5.3) and rejects truncated or malformed input with a {@link CborRuntimeException} that carries the
 * byte offset. It never allocates from a declared length before checking it against the remaining input.
 * <p>
 * Navigation ({@link #size()}, {@link #get(int)}, {@link #items()}, {@link #entries()}, {@link #field(long)}) and the
 * scalar accessors work on an untagged item; strip a tag first with {@link #untag()} or {@link #untagIf(long)}. A tag's
 * value is read from its numeric argument, so {@code d9 0102}, {@code da 00000102} and {@code db 0000000000000102} are
 * all tag 258.
 * <p>
 * Spans are immutable and share the caller's buffer, which must not be modified while spans over it are in use.
 */
public final class CborSpan {
    private static final int MAJOR_UNSIGNED = 0;
    private static final int MAJOR_NEGATIVE = 1;
    private static final int MAJOR_BYTES = 2;
    private static final int MAJOR_TEXT = 3;
    private static final int MAJOR_ARRAY = 4;
    private static final int MAJOR_MAP = 5;
    private static final int MAJOR_TAG = 6;

    private static final int BREAK = 0xff;
    private static final int SIMPLE_FALSE = 0xf4;
    private static final int SIMPLE_TRUE = 0xf5;
    private static final int SIMPLE_NULL = 0xf6;
    private static final long TAG_EMBEDDED_CBOR = 24;
    private static final long TAG_POSITIVE_BIGNUM = 2;
    private static final long TAG_NEGATIVE_BIGNUM = 3;

    // Walker frame kinds. ODD marks an indefinite map that has read a key and still needs its value.
    private static final int FRAME_ROOT = 0;
    private static final int FRAME_ARRAY = 1;
    private static final int FRAME_MAP = 2;
    private static final int FRAME_BYTE_CHUNKS = 3;
    private static final int FRAME_TEXT_CHUNKS = 4;
    private static final int FRAME_ODD = 8;
    private static final int INDEFINITE = CborHead.INDEFINITE;

    private final byte[] buffer;
    private final int offset;
    private final int length;
    // Listed once: a walk and the views of a record read the same containers, and a listing skips every child.
    private List<CborSpan> children;
    private List<Map.Entry<CborSpan, CborSpan>> entries;
    private CborSpan untagged;

    private CborSpan(byte[] buffer, int offset, int length) {
        this.buffer = buffer;
        this.offset = offset;
        this.length = length;
    }

    /**
     * Walks the buffer, which must hold exactly one data item.
     *
     * @param buffer CBOR bytes
     * @return the span of the whole buffer
     * @throws CborRuntimeException if the item is malformed or followed by trailing bytes
     */
    public static CborSpan of(byte[] buffer) {
        return of(buffer, 0, Objects.requireNonNull(buffer, "buffer").length);
    }

    /**
     * Walks the one data item that starts at {@code offset}. Bytes after the item are not read.
     *
     * @param buffer CBOR bytes
     * @param offset start of the item
     * @return the span of that item, sharing the buffer
     * @throws CborRuntimeException if the item is malformed or truncated
     */
    public static CborSpan at(byte[] buffer, int offset) {
        Objects.requireNonNull(buffer, "buffer");
        return new CborSpan(buffer, offset, skip(buffer, offset, buffer.length) - offset);
    }

    /**
     * Walks to an item of nested arrays and no further: only the heads of the arrays on the path, the items before each
     * index and the item itself are read, so what follows may be malformed or missing. Tags on the arrays are looked
     * through. For {@code 82 07 85 ...}, {@code arrayItem(bytes, 0)} is the {@code 07} and
     * {@code arrayItem(bytes, 1, 0)} the first item of the second array.
     *
     * @param buffer CBOR bytes, with an array at the start
     * @param path   the index in each array on the way down
     * @return the span of that item, sharing the buffer
     * @throws CborRuntimeException if what is read is malformed or truncated, a step on the path is not an array, or an
     *                              array has no such index
     */
    public static CborSpan arrayItem(byte[] buffer, int... path) {
        Objects.requireNonNull(buffer, "buffer");
        CborHead head = new CborHead();
        int pos = 0;
        for (int index : path) {
            head.read(buffer, pos, buffer.length);
            while (head.major == MAJOR_TAG)
                head.read(buffer, head.end, buffer.length);
            if (head.major != MAJOR_ARRAY)
                throw CborHead.error("expected a CBOR array", head.start);
            if (index < 0 || (!head.indefinite && Long.compareUnsigned(index, head.argument) >= 0))
                throw CborHead.error("no item " + index + " in the CBOR array", head.start);
            pos = head.end;
            for (int i = 0; i < index; i++)
                pos = skip(buffer, pos, buffer.length);
        }
        return at(buffer, pos);
    }

    /**
     * Walks the window {@code [offset, offset + length)}, which must hold exactly one data item.
     *
     * @param buffer CBOR bytes
     * @param offset start of the item
     * @param length length of the item
     * @return the span over that window, sharing the buffer
     * @throws CborRuntimeException if the item is malformed or does not end exactly at {@code offset + length}
     */
    public static CborSpan of(byte[] buffer, int offset, int length) {
        Objects.requireNonNull(buffer, "buffer");
        Objects.checkFromIndexSize(offset, length, buffer.length);
        int end = skip(buffer, offset, offset + length);
        if (end != offset + length)
            throw CborHead.error("trailing bytes after the CBOR item", end);
        return new CborSpan(buffer, offset, length);
    }

    /**
     * The iterative walker: returns the offset just after the one data item that starts at {@code offset}, reading no
     * further than {@code limit}. Bytes after the item are not read.
     *
     * @param buffer CBOR bytes
     * @param offset start of the item
     * @param limit  end of the readable region (exclusive)
     * @return the end offset (exclusive) of the item
     * @throws CborRuntimeException if the item is malformed or truncated at {@code limit}
     */
    public static int skip(byte[] buffer, int offset, int limit) {
        Objects.requireNonNull(buffer, "buffer");
        Objects.checkFromToIndex(offset, limit, buffer.length);

        CborHead head = new CborHead();
        int definiteEnd = skipDefinite(buffer, offset, limit, head);
        if (definiteEnd >= 0)
            return definiteEnd;

        // Frame d: remaining[d] items still to read (INDEFINITE until BREAK), kind[d] its FRAME_* kind.
        // Each push consumes at least one input byte, so the stack never exceeds the input length.
        int[] remaining = new int[16];
        int[] kind = new int[16];
        int depth = 0;
        remaining[0] = 1;
        kind[0] = FRAME_ROOT;

        int pos = offset;
        boolean afterTag = false;
        while (true) {
            while (remaining[depth] == 0) {
                if (depth == 0)
                    return pos;
                depth--;
            }
            if (pos >= limit)
                throw CborHead.error("truncated CBOR: expected a data item", pos);

            int frame = kind[depth];
            if ((buffer[pos] & 0xff) == BREAK) {
                if (remaining[depth] != INDEFINITE || afterTag)
                    throw CborHead.error("unexpected BREAK", pos);
                if ((frame & FRAME_ODD) != 0)
                    throw CborHead.error("BREAK after a map key without its value", pos);
                pos++;
                depth--;
                continue;
            }

            head.read(buffer, pos, limit);
            int frameKind = frame & ~FRAME_ODD;
            if (frameKind == FRAME_BYTE_CHUNKS || frameKind == FRAME_TEXT_CHUNKS) {
                int chunkMajor = frameKind == FRAME_BYTE_CHUNKS ? MAJOR_BYTES : MAJOR_TEXT;
                if (head.major != chunkMajor || head.indefinite)
                    throw CborHead.error("indefinite-length string chunk must be a definite string of the same major"
                            + " type", pos);
            }
            pos = head.end;

            // A tag is a prefix of the item that follows it; the tagged item counts once, when it is read.
            if (head.major == MAJOR_TAG) {
                afterTag = true;
                continue;
            }
            afterTag = false;
            if (remaining[depth] > 0)
                remaining[depth]--;
            if (frameKind == FRAME_MAP && remaining[depth] == INDEFINITE)
                kind[depth] = frame ^ FRAME_ODD;

            int pushKind;
            int pushCount;
            switch (head.major) {
                case MAJOR_BYTES:
                case MAJOR_TEXT:
                    if (!head.indefinite) {
                        pos = CborHead.checkedStringEnd(head, pos, limit);
                        continue;
                    }
                    pushKind = head.major == MAJOR_BYTES ? FRAME_BYTE_CHUNKS : FRAME_TEXT_CHUNKS;
                    pushCount = INDEFINITE;
                    break;
                case MAJOR_ARRAY:
                    pushKind = FRAME_ARRAY;
                    pushCount = head.indefinite ? INDEFINITE : CborHead.checkedCount(head, 1, pos, limit);
                    break;
                case MAJOR_MAP:
                    pushKind = FRAME_MAP;
                    pushCount = head.indefinite ? INDEFINITE : CborHead.checkedCount(head, 2, pos, limit);
                    break;
                default:
                    continue; // integers and simple values are complete after their head
            }
            if (pushCount == 0)
                continue;
            if (++depth == remaining.length) {
                remaining = Arrays.copyOf(remaining, depth * 2);
                kind = Arrays.copyOf(kind, depth * 2);
            }
            remaining[depth] = pushCount;
            kind[depth] = pushKind;
        }
    }

    // Without an indefinite length no frames are needed, only the count of items still to read, and most items have
    // none. Returns the end of the item, or -1 to leave it to the walker with frames, which also reports a truncation
    // or a misplaced BREAK. A head it rejects, it rejects as the walker does, having read the same heads.
    private static int skipDefinite(byte[] buffer, int offset, int limit, CborHead head) {
        long pending = 1;
        int pos = offset;
        while (pending > 0) {
            if (pos >= limit || (buffer[pos] & 0xff) == BREAK)
                return -1;
            head.read(buffer, pos, limit);
            pos = head.end;
            switch (head.major) {
                case MAJOR_TAG:
                    continue;
                case MAJOR_BYTES:
                case MAJOR_TEXT:
                    if (head.indefinite)
                        return -1;
                    pos = CborHead.checkedStringEnd(head, pos, limit);
                    break;
                case MAJOR_ARRAY:
                case MAJOR_MAP:
                    if (head.indefinite)
                        return -1;
                    pending += CborHead.checkedCount(head, head.major == MAJOR_ARRAY ? 1 : 2, pos, limit);
                    break;
                default:
                    break;
            }
            pending--;
        }
        return pos;
    }

    /**
     * @return the buffer this span points into (not a copy)
     */
    public byte[] buffer() {
        return buffer;
    }

    /**
     * @return start of the item in {@link #buffer()}
     */
    public int offset() {
        return offset;
    }

    /**
     * @return encoded length of the item, including any tags
     */
    public int length() {
        return length;
    }

    /**
     * Number of bytes before the item's content: the heads of any tags plus the item's own head. For a definite array
     * {@code 82} this is 1, for the non-minimal {@code 98 04} it is 2, for {@code 9f} it is 1, for a byte string it is
     * the length of the head without the payload.
     *
     * @return header length in bytes
     */
    public int headerLength() {
        return itemHead().end - offset;
    }

    /**
     * @return a copy of the item's original encoding
     */
    public byte[] bytes() {
        return Arrays.copyOfRange(buffer, offset, offset + length);
    }

    /**
     * @return the CBOR major type (0-7) of the item, looking through any tags
     */
    public int majorType() {
        // a span is well-formed, so an untagged item's first byte says its major type
        return isTagged() ? itemHead().major : (buffer[offset] & 0xff) >>> 5;
    }

    /**
     * @return the outermost tag number, or -1 if the item is not tagged. Tag numbers above {@link Long#MAX_VALUE} are
     * returned as negative values (unsigned 64-bit).
     */
    public long tag() {
        return isTagged() ? head(offset).argument : -1;
    }

    /**
     * @return whether the item has a tag, which {@link #tag()} cannot tell for the tag number 2^64 - 1
     */
    public boolean isTagged() {
        return (buffer[offset] & 0xff) >>> 5 == MAJOR_TAG;
    }

    /**
     * @return the payload of the outermost tag
     * @throws CborRuntimeException if the item is not tagged
     */
    public CborSpan untag() {
        CborHead head = head(offset);
        if (head.major != MAJOR_TAG)
            throw CborHead.error("CBOR item is not tagged", offset);
        return untagged(head);
    }

    /**
     * Strips the outermost tag when it equals {@code tag}, at any argument width; otherwise returns this span.
     * For example {@code untagIf(258)} accepts a CBOR set with or without its optional tag.
     *
     * @param tag tag number
     * @return the payload, or this span
     */
    public CborSpan untagIf(long tag) {
        if (!isTagged())
            return this;
        CborHead head = head(offset);
        return head.major == MAJOR_TAG && head.argument == tag ? untagged(head) : this;
    }

    private CborSpan untagged(CborHead head) {
        if (untagged == null)
            untagged = new CborSpan(buffer, head.end, offset + length - head.end);
        return untagged;
    }

    /**
     * @return true if the item (looking through tags) is an indefinite-length string, array or map
     */
    public boolean isIndefinite() {
        // a well-formed untagged item has additional information 31 only if it is an indefinite string or container
        return isTagged() ? itemHead().indefinite : (buffer[offset] & 0x1f) == 31;
    }

    /**
     * @return number of array items or map pairs
     * @throws CborRuntimeException if the item is not an untagged array or map
     */
    public int size() {
        CborHead head = untaggedHead();
        if (head.major != MAJOR_ARRAY && head.major != MAJOR_MAP)
            throw CborHead.error("expected a CBOR array or map", offset);
        if (!head.indefinite)
            return (int) head.argument;
        return head.major == MAJOR_ARRAY ? children(MAJOR_ARRAY).size() : children(MAJOR_MAP).size() / 2;
    }

    /**
     * @param index array index
     * @return the array item at {@code index}
     * @throws CborRuntimeException      if the item is not an untagged array
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public CborSpan get(int index) {
        CborHead head = untaggedHead();
        if (head.major != MAJOR_ARRAY)
            throw CborHead.error("expected a CBOR array", offset);
        if (index < 0 || (!head.indefinite && index >= head.argument))
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for CBOR array");
        int end = offset + length;
        int pos = head.end;
        for (int i = 0; i < index; i++) {
            if ((buffer[pos] & 0xff) == BREAK)
                throw new IndexOutOfBoundsException("Index " + index + " out of bounds for length " + i);
            pos = skip(buffer, pos, end);
        }
        if ((buffer[pos] & 0xff) == BREAK)
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for length " + index);
        return new CborSpan(buffer, pos, skip(buffer, pos, end) - pos);
    }

    /**
     * @return the array items in encoded order
     * @throws CborRuntimeException if the item is not an untagged array
     */
    public List<CborSpan> items() {
        return children(MAJOR_ARRAY);
    }

    /**
     * @return the map entries in encoded order; duplicate keys are kept
     * @throws CborRuntimeException if the item is not an untagged map
     */
    public List<Map.Entry<CborSpan, CborSpan>> entries() {
        List<CborSpan> children = children(MAJOR_MAP);
        if (entries != null)
            return entries;
        List<Map.Entry<CborSpan, CborSpan>> listed = new ArrayList<>(children.size() / 2);
        for (int i = 0; i < children.size(); i += 2)
            listed.add(new AbstractMap.SimpleImmutableEntry<>(children.get(i), children.get(i + 1)));
        entries = Collections.unmodifiableList(listed);
        return entries;
    }

    /**
     * Looks up a field of a CDDL record, a map keyed by unsigned integers. Keys are compared by value, so a non-minimal
     * key such as {@code 18 00} matches 0. Keys that are not unsigned integers are skipped.
     *
     * @param key unsigned integer key
     * @return the value, or empty if the key is absent
     * @throws CborRuntimeException if the item is not an untagged map, or if any unsigned integer key occurs more than
     *                              once (a record with a duplicate key is rejected as a whole, as the Cardano ledger does)
     */
    public Optional<CborSpan> field(long key) {
        List<CborSpan> children = children(MAJOR_MAP);
        Set<Long> seen = null;
        long seenBelow64 = 0;
        CborSpan value = null;
        for (int i = 0; i < children.size(); i += 2) {
            CborSpan keySpan = children.get(i);
            CborHead head = head(keySpan.offset);
            if (head.major != MAJOR_UNSIGNED)
                continue;
            boolean below64 = Long.compareUnsigned(head.argument, Long.SIZE) < 0;
            if (!below64 && seen == null)
                seen = new HashSet<>();
            if (below64 ? (seenBelow64 & (1L << head.argument)) != 0 : !seen.add(head.argument))
                throw CborHead.error("duplicate key " + Long.toUnsignedString(head.argument) + " in CBOR record",
                        keySpan.offset);
            if (below64)
                seenBelow64 |= 1L << head.argument;
            if (head.argument == key)
                value = children.get(i + 1);
        }
        return Optional.ofNullable(value);
    }

    /**
     * @return the value of an untagged unsigned or negative integer
     * @throws CborRuntimeException if the item is not an integer or does not fit a {@code long}
     */
    public long asLong() {
        CborHead head = untaggedHead();
        if ((head.major != MAJOR_UNSIGNED && head.major != MAJOR_NEGATIVE))
            throw CborHead.error("expected a CBOR integer", offset);
        if (head.argument < 0)
            throw CborHead.error("CBOR integer does not fit a long", offset);
        return head.major == MAJOR_UNSIGNED ? head.argument : -1 - head.argument;
    }

    /**
     * @return the value of an unsigned or negative integer, or of a bignum (tag 2 or 3 on a byte string, which may be
     * chunked)
     * @throws CborRuntimeException if the item is neither
     */
    public BigInteger asBigInteger() {
        CborHead head = head(offset);
        if (head.major == MAJOR_TAG && (head.argument == TAG_POSITIVE_BIGNUM || head.argument == TAG_NEGATIVE_BIGNUM)) {
            return bignum(head.argument == TAG_POSITIVE_BIGNUM, untag().byteString());
        }
        if (head.major != MAJOR_UNSIGNED && head.major != MAJOR_NEGATIVE)
            throw CborHead.error("expected a CBOR integer or bignum", offset);
        BigInteger argument = head.argument >= 0
                ? BigInteger.valueOf(head.argument)
                : new BigInteger(Long.toUnsignedString(head.argument));
        return head.major == MAJOR_UNSIGNED ? argument : BigInteger.ONE.negate().subtract(argument);
    }

    /** @return the value of a bignum: its content is an unsigned magnitude {@code n}, and tag 3 means {@code -1 - n} */
    static BigInteger bignum(boolean positive, byte[] content) {
        BigInteger magnitude = new BigInteger(1, content);
        return positive ? magnitude : BigInteger.ONE.negate().subtract(magnitude);
    }

    /**
     * @return the value of an untagged {@code true} or {@code false}
     * @throws CborRuntimeException if the item is not a boolean
     */
    public boolean asBoolean() {
        int initial = buffer[offset] & 0xff;
        if (length != 1 || (initial != SIMPLE_FALSE && initial != SIMPLE_TRUE))
            throw CborHead.error("expected a CBOR boolean", offset);
        return initial == SIMPLE_TRUE;
    }

    /**
     * @return true if the item is an untagged {@code null}
     */
    public boolean isNull() {
        return length == 1 && (buffer[offset] & 0xff) == SIMPLE_NULL;
    }

    /**
     * @return the payload of an untagged byte string; the chunks of an indefinite-length string are concatenated
     * @throws CborRuntimeException if the item is not a byte string
     */
    public byte[] byteString() {
        return stringPayload(MAJOR_BYTES);
    }

    /**
     * @return the UTF-8 decoded value of an untagged text string; chunks are concatenated
     * @throws CborRuntimeException if the item is not a text string
     */
    public String text() {
        return new String(stringPayload(MAJOR_TEXT), StandardCharsets.UTF_8);
    }

    /**
     * Returns the CBOR embedded in a tag-24 byte string ({@code #6.24(bytes .cbor any)}), as used by inline datums and
     * script references. For a definite byte string the span points into this span's buffer; for a chunked byte string
     * the chunks are concatenated into a new buffer.
     *
     * @return the embedded data item
     * @throws CborRuntimeException if the item is not tag 24 on a byte string, or the payload is not exactly one
     *                              well-formed data item
     */
    public CborSpan embedded() {
        CborHead tagHead = head(offset);
        if (tagHead.major != MAJOR_TAG || tagHead.argument != TAG_EMBEDDED_CBOR)
            throw CborHead.error("expected tag 24 (embedded CBOR)", offset);
        CborHead bytesHead = head(tagHead.end);
        if (bytesHead.major != MAJOR_BYTES)
            throw CborHead.error("tag 24 must wrap a byte string", tagHead.end);
        if (bytesHead.indefinite)
            return of(untag().byteString());
        return of(buffer, bytesHead.end, (int) bytesHead.argument);
    }

    /**
     * Copies this item, replacing the given child spans and keeping every other byte as encoded. Each replacement must
     * be one complete data item if the enclosing container's count is to stay valid; this method only checks ranges.
     *
     * @param targets      spans inside this span, in ascending order and not overlapping
     * @param replacements the bytes to write in place of each target
     * @return the new encoding
     */
    public byte[] replacing(List<CborSpan> targets, List<byte[]> replacements) {
        if (targets.size() != replacements.size())
            throw new IllegalArgumentException("targets and replacements differ in size");
        ByteArrayOutputStream out = new ByteArrayOutputStream(length);
        int pos = offset;
        for (int i = 0; i < targets.size(); i++) {
            CborSpan target = targets.get(i);
            if (target.buffer != buffer || target.offset < pos || target.offset + target.length > offset + length)
                throw new IllegalArgumentException("target " + i + " is not an ordered, non-overlapping span inside this span");
            out.write(buffer, pos, target.offset - pos);
            byte[] replacement = replacements.get(i);
            out.write(replacement, 0, replacement.length);
            pos = target.offset + target.length;
        }
        out.write(buffer, pos, offset + length - pos);
        return out.toByteArray();
    }

    @Override
    public String toString() {
        int shown = Math.min(length, 64);
        String hex = HexUtil.encodeHexString(Arrays.copyOfRange(buffer, offset, offset + shown));
        return "CborSpan[offset=" + offset + ", length=" + length + ", " + hex + (shown < length ? "..." : "") + "]";
    }

    private List<CborSpan> children(int major) {
        CborHead head = untaggedHead();
        if (head.major != major)
            throw CborHead.error(major == MAJOR_ARRAY ? "expected a CBOR array" : "expected a CBOR map", offset);
        if (this.children != null)
            return this.children;
        int end = offset + length;
        int pos = head.end;
        List<CborSpan> children = new ArrayList<>(head.indefinite ? 10
                : (head.major == MAJOR_ARRAY ? 1 : 2) * (int) head.argument);
        // The span was walked when it was created, so the children are well-formed; an indefinite container ends
        // at its BREAK, a definite one at the end of the span.
        while (pos < end && (!head.indefinite || (buffer[pos] & 0xff) != BREAK)) {
            int next = skip(buffer, pos, end);
            children.add(new CborSpan(buffer, pos, next - pos));
            pos = next;
        }
        this.children = Collections.unmodifiableList(children);
        return this.children;
    }

    private byte[] stringPayload(int major) {
        CborHead head = untaggedHead();
        if (head.major != major)
            throw CborHead.error(major == MAJOR_BYTES ? "expected a CBOR byte string" : "expected a CBOR text string",
                    offset);
        if (!head.indefinite)
            return Arrays.copyOfRange(buffer, head.end, head.end + (int) head.argument);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int pos = head.end;
        while ((buffer[pos] & 0xff) != BREAK) {
            CborHead chunk = head(pos);
            out.write(buffer, chunk.end, (int) chunk.argument);
            pos = chunk.end + (int) chunk.argument;
        }
        return out.toByteArray();
    }

    private CborHead head(int pos) {
        CborHead head = new CborHead();
        head.read(buffer, pos, offset + length);
        return head;
    }

    private CborHead itemHead() {
        CborHead head = head(offset);
        while (head.major == MAJOR_TAG)
            head.read(buffer, head.end, offset + length);
        return head;
    }

    private CborHead untaggedHead() {
        CborHead head = head(offset);
        if (head.major == MAJOR_TAG)
            throw CborHead.error("CBOR item is tagged; call untag() or untagIf() first", offset);
        return head;
    }
}

