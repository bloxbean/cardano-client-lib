package com.bloxbean.cardano.client.common.cbor.custom;

import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.encoder.HalfPrecisionFloatEncoder;
import co.nstant.in.cbor.model.*;
import co.nstant.in.cbor.model.Number;
import com.google.common.primitives.UnsignedBytes;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

/**
 * A custom CborEncoder impl to handle serialization of {@link SortedMap} when canonical cbor is set in CborEncoder level.
 * This class exists to handle the scenario where a Map's key is already sorted by the caller, so
 * encoder doesn't need to sort it again even if canonical = true.
 *
 * It is also used to handle chunked ByteStrings in PlutusData serialization/deserialization.
 * <p>
 * Containers are encoded with an explicit stack instead of recursion, so items of any nesting depth encode on any
 * thread. The output is the same as the cbor-java based encoder it replaces, byte for byte: maps that need canonical
 * ordering buffer their encoded entries and sort them by unsigned lexicographic order of the key bytes; everything else
 * streams.
 */
public class CustomCborEncoder extends CborEncoder {
    private static final BigInteger MINUS_ONE = BigInteger.valueOf(-1);
    private static final int BREAK = 0xff;

    private final OutputStream outputStream;
    private final boolean sortAllMaps;

    /**
     * Initialize a new encoder which writes the binary encoded data to an
     * {@link OutputStream}.
     *
     * @param outputStream the {@link OutputStream} to write the encoded data to
     */
    public CustomCborEncoder(OutputStream outputStream) {
        this(outputStream, false);
    }

    private CustomCborEncoder(OutputStream outputStream, boolean sortAllMaps) {
        super(outputStream);
        this.outputStream = outputStream;
        this.sortAllMaps = sortAllMaps;
    }

    /**
     * Encode a single {@link DataItem}.
     *
     * @param dataItem the {@link DataItem} to encode. If null, encoder encodes a
     *                 {@link SimpleValue} NULL value.
     * @throws CborException if {@link DataItem} could not be encoded or there was
     *                       an problem with the {@link OutputStream}.
     */
    @Override
    public void encode(DataItem dataItem) throws CborException {
        Buffer out = new Buffer();
        new Writer(isCanonical(), sortAllMaps).write(dataItem, out);
        try {
            outputStream.write(out.bytes, 0, out.size);
        } catch (IOException e) {
            throw new CborException(e);
        }
    }

    /**
     * Encodes a map key for {@link EncodedKeyMap}: canonical, with every map sorted (including indefinite maps and
     * {@link SortedMap}s), so two keys get the same bytes exactly when cbor-java considers them equal.
     */
    static byte[] encodeKey(DataItem key) throws CborException {
        Buffer out = new Buffer();
        new Writer(true, true).write(key, out);
        return Arrays.copyOf(out.bytes, out.size);
    }

    /**
     * A hash of {@link #encodeKey(DataItem)} computed without building it: the head bytes of every item (tags, type and
     * length) and the full encoding of leaves, combined in order for arrays and regardless of order for maps (the key
     * encoding sorts map entries). Two keys with the same key encoding have the same hash, and computing it never copies
     * a nested map's entries, so hashing a key is linear in its size whatever its nesting.
     */
    static int keyHash(DataItem key) throws CborException {
        return new Writer(true, true).hash(key);
    }

    /**
     * A growable byte buffer; unlike {@link java.io.ByteArrayOutputStream} it is not synchronized.
     */
    private static final class Buffer {
        byte[] bytes = new byte[64];
        int size;

        void write(int b) {
            ensure(1);
            bytes[size++] = (byte) b;
        }

        void write(byte[] b) {
            ensure(b.length);
            System.arraycopy(b, 0, bytes, size, b.length);
            size += b.length;
        }

        void write(Buffer b) {
            ensure(b.size);
            System.arraycopy(b.bytes, 0, bytes, size, b.size);
            size += b.size;
        }

        byte[] toByteArray() {
            return Arrays.copyOf(bytes, size);
        }

        private void ensure(int more) {
            if (size + more > bytes.length)
                bytes = Arrays.copyOf(bytes, Math.max(bytes.length * 2, size + more));
        }
    }

    /**
     * An open array or map: its remaining children, where they are written, and what to do when it is complete.
     */
    private abstract static class Frame {
        final Frame parent;
        final Buffer out;

        Frame(Frame parent, Buffer out) {
            this.parent = parent;
            this.out = out;
        }

        abstract boolean hasNext();

        abstract DataItem next();

        Buffer childBuffer() {
            return out;
        }

        void childDone(Buffer child) {
        }

        void finish() {
        }
    }

    private static final class ArrayFrame extends Frame {
        private final Iterator<DataItem> items;

        ArrayFrame(Frame parent, Buffer out, List<DataItem> items) {
            super(parent, out);
            this.items = items.iterator();
        }

        @Override
        boolean hasNext() {
            return items.hasNext();
        }

        @Override
        DataItem next() {
            return items.next();
        }
    }

    // Map entries as keys and values. cbor-java's Map keeps its key list and its values in the same insertion order, so
    // they are zipped without looking keys up (which would hash them recursively); get(key) is the fallback when a
    // put(key, null) quirk made the two differ in size.
    private abstract static class MapFrame extends Frame {
        private final Map map;
        private final Iterator<DataItem> keys;
        private final Iterator<DataItem> values;
        private DataItem key;
        boolean onValue;

        MapFrame(Frame parent, Buffer out, Map map) {
            super(parent, out);
            this.map = map;
            Collection<DataItem> keyList = map.getKeys();
            Collection<DataItem> valueList = map.getValues();
            this.keys = keyList.iterator();
            this.values = keyList.size() == valueList.size() ? valueList.iterator() : null;
        }

        @Override
        boolean hasNext() {
            return onValue || keys.hasNext();
        }

        @Override
        DataItem next() {
            if (onValue) {
                onValue = false;
                return values != null ? values.next() : map.get(key);
            }
            key = keys.next();
            onValue = true;
            return key;
        }
    }

    private static final class StreamingMapFrame extends MapFrame {
        private final boolean breakAtEnd;

        StreamingMapFrame(Frame parent, Buffer out, Map map, boolean breakAtEnd) {
            super(parent, out, map);
            this.breakAtEnd = breakAtEnd;
        }

        @Override
        void finish() {
            if (breakAtEnd)
                writeBreak(out);
        }
    }

    // Canonical order: each key and value is encoded into its own buffer, then the entries are written sorted by the
    // key bytes (the same TreeMap as before, so equal key bytes keep the last value).
    private static final class SortedMapFrame extends MapFrame {
        private final TreeMap<byte[], byte[]> sorted = new TreeMap<>(UnsignedBytes.lexicographicalComparator());
        private final Buffer scratch = new Buffer();
        private final boolean breakAtEnd;
        private byte[] keyBytes;

        SortedMapFrame(Frame parent, Buffer out, Map map, boolean breakAtEnd) {
            super(parent, out, map);
            this.breakAtEnd = breakAtEnd;
        }

        // Children are encoded one after the other, each fully before the next starts, so one buffer serves them all.
        @Override
        Buffer childBuffer() {
            scratch.size = 0;
            return scratch;
        }

        @Override
        void childDone(Buffer child) {
            // onValue is true once a key has been handed out and before its value is
            if (onValue) {
                keyBytes = child.toByteArray();
            } else {
                sorted.put(keyBytes, child.toByteArray());
            }
        }

        @Override
        void finish() {
            for (java.util.Map.Entry<byte[], byte[]> entry : sorted.entrySet()) {
                out.write(entry.getKey());
                out.write(entry.getValue());
            }
            if (breakAtEnd)
                writeBreak(out);
        }
    }

    // The BREAK closing a map was written with encoder.encode(SimpleValue.BREAK), which also writes any tags on it.
    private static void writeBreak(Buffer out) {
        Writer.writeTags(SimpleValue.BREAK, out);
        out.write(BREAK);
    }

    // A container being hashed: children in order for an array, key and value pairs regardless of order for a map.
    private static final class HashFrame {
        final HashFrame parent;
        private final Frame frame;
        private final Iterator<java.util.Map.Entry<EncodedKeyMap.Key, DataItem>> keyed;
        private final boolean map;
        private int hash;
        private int entries;
        private int keyHash;
        private boolean onValue;

        HashFrame(HashFrame parent, Frame frame, boolean map, int headHash,
                  Iterator<java.util.Map.Entry<EncodedKeyMap.Key, DataItem>> keyed) {
            this.parent = parent;
            this.frame = frame;
            this.map = map;
            this.hash = headHash;
            this.keyed = keyed;
        }

        boolean hasNext() {
            return keyed != null ? keyed.hasNext() : frame.hasNext();
        }

        // The next child to hash; for a map with recorded key hashes only the values are children.
        DataItem next(Writer writer) throws CborException {
            if (keyed == null)
                return frame.next();
            java.util.Map.Entry<EncodedKeyMap.Key, DataItem> entry = keyed.next();
            EncodedKeyMap.Key key = entry.getKey();
            keyHash = key.leaf ? writer.leafHash(key.item) : key.hash;
            onValue = true;
            return entry.getValue();
        }

        void add(int childHash) {
            if (!map) {
                hash = 31 * hash + childHash;
            } else if (!onValue) {
                keyHash = childHash;
                onValue = true;
            } else {
                entries += 31 * keyHash + childHash;
                onValue = false;
            }
        }

        int result() {
            return map ? 31 * hash + entries : hash;
        }
    }

    private static final class Writer {
        private final boolean canonical;
        private final boolean sortAllMaps;

        Writer(boolean canonical, boolean sortAllMaps) {
            this.canonical = canonical;
            this.sortAllMaps = sortAllMaps;
        }

        // The open containers are a chain of frames from the innermost (top) through parent.
        void write(DataItem root, Buffer out) throws CborException {
            Frame top = emit(root, out, null);
            while (top != null) {
                if (top.hasNext()) {
                    Frame opened = emit(top.next(), top.childBuffer(), top);
                    if (opened != null)
                        top = opened;
                } else {
                    Frame done = top;
                    done.finish();
                    top = done.parent;
                    if (top != null)
                        top.childDone(done.out);
                }
            }
        }

        int hash(DataItem root) throws CborException {
            Buffer scratch = new Buffer();
            HashFrame top = null;
            DataItem item = root;
            while (true) {
                // hash one item: a leaf completely, or a container's head and then its children
                scratch.size = 0;
                DataItem current = item == null ? SimpleValue.NULL : item;
                HashFrame opened = null;
                Frame frame = emit(current, scratch, null);
                if (frame != null) {
                    // an EncodedKeyMap already holds its keys' hashes, so nesting through map keys is not hashed again
                    Iterator<java.util.Map.Entry<EncodedKeyMap.Key, DataItem>> keyed =
                            current instanceof EncodedKeyMap ? ((EncodedKeyMap) current).keyedEntries() : null;
                    opened = new HashFrame(top, frame, current.getMajorType() == MajorType.MAP, hashBytes(scratch), keyed);
                }
                if (opened != null) {
                    top = opened;
                } else if (top == null) {
                    return hashBytes(scratch);
                } else {
                    top.add(hashBytes(scratch));
                }
                // close finished containers, then continue with the next child
                while (!top.hasNext()) {
                    int done = top.result();
                    top = top.parent;
                    if (top == null)
                        return done;
                    top.add(done);
                }
                item = top.next(this);
            }
        }

        // The key hash of an untagged leaf: the hash of its encoding.
        int leafHash(DataItem leaf) throws CborException {
            Buffer scratch = new Buffer();
            emit(leaf, scratch, null);
            return hashBytes(scratch);
        }

        private static int hashBytes(Buffer buffer) {
            int hash = 1;
            for (int i = 0; i < buffer.size; i++)
                hash = 31 * hash + buffer.bytes[i];
            return hash;
        }

        // Writes a leaf completely, or a container's head and returns a frame for its children.
        private Frame emit(DataItem item, Buffer out, Frame parent) throws CborException {
            if (item == null)
                item = SimpleValue.NULL;
            writeTags(item, out);
            Frame frame = null;
            switch (item.getMajorType()) {
                case ARRAY: {
                    Array array = (Array) item;
                    List<DataItem> items = array.getDataItems();
                    if (array.isChunked())
                        out.write(MajorType.ARRAY.getValue() << 5 | 31);
                    else
                        writeHead(out, MajorType.ARRAY, items.size());
                    if (!items.isEmpty())
                        frame = new ArrayFrame(parent, out, items);
                    break;
                }
                case MAP: {
                    Map map = (Map) item;
                    int size = map.getKeys().size();
                    if (map.isChunked())
                        out.write(MajorType.MAP.getValue() << 5 | 31);
                    else
                        writeHead(out, MajorType.MAP, size);
                    // as before, an empty map ends right after its head, even an indefinite one; a single entry needs
                    // no sorting, so it streams
                    if (size > 1 && (sortAllMaps || (canonical && !map.isChunked() && !(map instanceof SortedMap))))
                        frame = new SortedMapFrame(parent, out, map, map.isChunked());
                    else if (size > 0)
                        frame = new StreamingMapFrame(parent, out, map, map.isChunked());
                    break;
                }
                case BYTE_STRING:
                    writeByteString((ByteString) item, out);
                    break;
                case UNICODE_STRING:
                    writeUnicodeString((UnicodeString) item, out);
                    break;
                case UNSIGNED_INTEGER:
                    writeHead(out, MajorType.UNSIGNED_INTEGER, ((Number) item).getValue());
                    break;
                case NEGATIVE_INTEGER:
                    writeHead(out, MajorType.NEGATIVE_INTEGER, MINUS_ONE.subtract(((Number) item).getValue()).abs());
                    break;
                case SPECIAL:
                    writeSpecial((Special) item, out);
                    break;
                case TAG:
                    writeHead(out, MajorType.TAG, ((Tag) item).getValue());
                    break;
                default:
                    throw new CborException("Unknown major type");
            }
            if (frame == null && parent != null)
                parent.childDone(out);
            return frame;
        }

        // Tags are chained from the innermost (on the item) outwards; they are written outermost first.
        private static void writeTags(DataItem item, Buffer out) {
            Tag tag = item.getTag();
            if (tag == null)
                return;
            List<Tag> chain = new ArrayList<>();
            for (; tag != null; tag = tag.getTag())
                chain.add(tag);
            for (int i = chain.size() - 1; i >= 0; i--)
                writeHead(out, MajorType.TAG, chain.get(i).getValue());
        }

        // As CustomByteStringEncoder and cbor-java's ByteStringEncoder.
        private void writeByteString(ByteString byteString, Buffer out) throws CborException {
            if (byteString instanceof ChunkedByteString) {
                out.write(MajorType.BYTE_STRING.getValue() << 5 | 31);
                for (byte[] chunk : ((ChunkedByteString) byteString).getChunks()) {
                    writeHead(out, MajorType.BYTE_STRING, chunk.length);
                    out.write(chunk);
                }
                emitNull(SimpleValue.BREAK, out);
                return;
            }
            byte[] bytes = byteString.getBytes();
            if (byteString.isChunked()) {
                out.write(MajorType.BYTE_STRING.getValue() << 5 | 31);
                if (bytes != null) {
                    writeHead(out, MajorType.BYTE_STRING, bytes.length);
                    out.write(bytes);
                }
            } else if (bytes == null) {
                emitNull(SimpleValue.NULL, out);
            } else {
                writeHead(out, MajorType.BYTE_STRING, bytes.length);
                out.write(bytes);
            }
        }

        // As cbor-java's UnicodeStringEncoder.
        private void writeUnicodeString(UnicodeString unicodeString, Buffer out) throws CborException {
            String string = unicodeString.getString();
            if (unicodeString.isChunked()) {
                out.write(MajorType.UNICODE_STRING.getValue() << 5 | 31);
                if (string != null) {
                    byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
                    writeHead(out, MajorType.UNICODE_STRING, bytes.length);
                    out.write(bytes);
                }
            } else if (string == null) {
                emitNull(SimpleValue.NULL, out);
            } else {
                byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
                writeHead(out, MajorType.UNICODE_STRING, bytes.length);
                out.write(bytes);
            }
        }

        // A nested leaf written by the string encoders through encoder.encode(...): its tags, then the value.
        private void emitNull(Special special, Buffer out) throws CborException {
            writeTags(special, out);
            writeSpecial(special, out);
        }

        // As cbor-java's SpecialEncoder.
        private void writeSpecial(Special special, Buffer out) throws CborException {
            switch (special.getSpecialType()) {
                case BREAK:
                    out.write(BREAK);
                    break;
                case SIMPLE_VALUE: {
                    SimpleValue simpleValue = (SimpleValue) special;
                    switch (simpleValue.getSimpleValueType()) {
                        case FALSE:
                        case NULL:
                        case TRUE:
                        case UNDEFINED:
                            out.write((7 << 5) | simpleValue.getSimpleValueType().getValue());
                            break;
                        case UNALLOCATED:
                            out.write((7 << 5) | simpleValue.getValue());
                            break;
                        case RESERVED:
                            break;
                    }
                    break;
                }
                case UNALLOCATED:
                    throw new CborException("Unallocated special type");
                case IEEE_754_HALF_PRECISION_FLOAT: {
                    if (!(special instanceof HalfPrecisionFloat))
                        throw new CborException("Wrong data item type");
                    int bits = HalfPrecisionFloatEncoder.fromFloat(((HalfPrecisionFloat) special).getValue());
                    out.write((7 << 5) | 25);
                    out.write(bits >> 8);
                    out.write(bits);
                    break;
                }
                case IEEE_754_SINGLE_PRECISION_FLOAT: {
                    if (!(special instanceof SinglePrecisionFloat))
                        throw new CborException("Wrong data item type");
                    int bits = Float.floatToRawIntBits(((SinglePrecisionFloat) special).getValue());
                    out.write((7 << 5) | 26);
                    for (int shift = 24; shift >= 0; shift -= 8)
                        out.write(bits >> shift);
                    break;
                }
                case IEEE_754_DOUBLE_PRECISION_FLOAT: {
                    if (!(special instanceof DoublePrecisionFloat))
                        throw new CborException("Wrong data item type");
                    long bits = Double.doubleToRawLongBits(((DoublePrecisionFloat) special).getValue());
                    out.write((7 << 5) | 27);
                    for (int shift = 56; shift >= 0; shift -= 8)
                        out.write((int) (bits >> shift));
                    break;
                }
                case SIMPLE_VALUE_NEXT_BYTE:
                    if (!(special instanceof SimpleValue))
                        throw new CborException("Wrong data item type");
                    out.write((7 << 5) | 24);
                    out.write(((SimpleValue) special).getValue());
                    break;
            }
        }

        // As cbor-java's AbstractEncoder.encodeTypeAndLength(MajorType, long), including its signed comparisons.
        private static void writeHead(Buffer out, MajorType majorType, long length) {
            int symbol = majorType.getValue() << 5;
            if (length <= 23L) {
                out.write((int) (symbol | length));
            } else if (length <= 255L) {
                out.write(symbol | 24);
                out.write((int) length);
            } else if (length <= 65535L) {
                out.write(symbol | 25);
                out.write((int) (length >> 8));
                out.write((int) length);
            } else if (length <= 4294967295L) {
                out.write(symbol | 26);
                for (int shift = 24; shift >= 0; shift -= 8)
                    out.write((int) (length >> shift));
            } else {
                out.write(symbol | 27);
                for (int shift = 56; shift >= 0; shift -= 8)
                    out.write((int) (length >> shift));
            }
        }

        // As cbor-java's AbstractEncoder.encodeTypeAndLength(MajorType, BigInteger): values from 2^64 become a bignum.
        private void writeHead(Buffer out, MajorType majorType, BigInteger length) throws CborException {
            int symbol = majorType.getValue() << 5;
            if (length.signum() < 0) { // not a valid argument, written as cbor-java writes it
                out.write(symbol | length.intValue());
            } else if (length.bitLength() < 64) {
                writeHead(out, majorType, length.longValue());
            } else if (length.bitLength() == 64) { // 2^63 to 2^64 - 1
                long value = length.longValue();
                out.write(symbol | 27);
                for (int shift = 56; shift >= 0; shift -= 8)
                    out.write((int) (value >> shift));
            } else {
                writeHead(out, MajorType.TAG, majorType == MajorType.NEGATIVE_INTEGER ? 3 : 2);
                byte[] bytes = length.toByteArray();
                writeHead(out, MajorType.BYTE_STRING, bytes.length);
                out.write(bytes);
            }
        }
    }
}
