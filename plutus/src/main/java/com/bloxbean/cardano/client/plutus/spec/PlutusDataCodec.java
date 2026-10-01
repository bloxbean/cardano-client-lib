package com.bloxbean.cardano.client.plutus.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.Number;
import co.nstant.in.cbor.model.Special;
import co.nstant.in.cbor.model.Tag;
import co.nstant.in.cbor.model.UnicodeString;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.common.cbor.custom.EncodedKeyMap;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

/**
 * Converts between {@link DataItem} trees and {@link PlutusData} models, and compares and hashes models, without
 * recursion: lists, maps and constructors are frames on an explicit stack, so data of any nesting depth (up to what fits
 * in a transaction, and beyond) works on any thread. Bytes are converted through
 * {@link com.bloxbean.cardano.client.common.cbor.CborSerializationUtil}, which is iterative too.
 * <p>
 * The models are the same as the recursive code produced: constructor tags 121-127, 1280-1400 and 102
 * {@code [alternative, fields]}; bignums (tags 2/3); an indefinite list is {@code chunked}. Like cbor-java's map, a
 * Plutus data map keeps one entry per key (the first position, the last value), although on chain a map is a list of
 * pairs; use the original bytes where every entry matters.
 */
final class PlutusDataCodec {
    private static final long GENERAL_FORM_TAG = 102;

    private PlutusDataCodec() {
    }

    // ---- DataItem -> PlutusData

    // Data nested at most this deep converts recursively, which is faster for the small data that is most of what is on
    // chain. The recursion is bounded, so it is stack-safe; deeper data converts on the iterative path.
    private static final int SHALLOW_DEPTH = 32;

    static PlutusData decode(DataItem item) throws CborDeserializationException {
        if (item == null)
            return null;
        PlutusData shallow = decodeShallow(item, SHALLOW_DEPTH);
        if (shallow != null)
            return shallow;
        DecodeFrame top = open(item, null);
        return top == null ? leaf(item) : run(top);
    }

    /**
     * Decodes an array as a list, ignoring any tag on it.
     */
    static ListPlutusData decodeList(Array array) throws CborDeserializationException {
        PlutusData shallow = decodeShallowList(array.getDataItems(), null, SHALLOW_DEPTH);
        if (shallow != null)
            return (ListPlutusData) shallow;
        return (ListPlutusData) run(new ListFrame(null, array.getDataItems(), null));
    }

    /**
     * Decodes a tagged array as a constructor.
     */
    static ConstrPlutusData decodeConstr(DataItem item) throws CborDeserializationException {
        PlutusData shallow = decodeShallow(item, SHALLOW_DEPTH);
        if (shallow != null)
            return (ConstrPlutusData) shallow;
        return (ConstrPlutusData) run(openConstr(item, null));
    }

    // The recursive conversion: null when the data nests deeper than depth (or holds a null item), and the iterative path
    // then converts it from the start, with the same result and errors.
    private static PlutusData decodeShallow(DataItem item, int depth) throws CborDeserializationException {
        if (item instanceof Array) {
            if (depth == 0)
                return null;
            Tag tag = item.getTag();
            List<DataItem> items = ((Array) item).getDataItems();
            if (tag == null)
                return decodeShallowList(items, null, depth - 1);
            long alternative = constrAlternative(tag, items);
            return decodeShallowList(constrFields(tag, items), alternative, depth - 1);
        }
        if (item instanceof Map) {
            if (depth == 0)
                return null;
            MapPlutusData map = new MapPlutusData();
            EntryIterator entries = new EntryIterator((Map) item);
            while (entries.hasNext()) {
                PlutusData key = decodeShallow(entries.next(), depth - 1);
                PlutusData value = key == null ? null : decodeShallow(entries.next(), depth - 1);
                if (value == null)
                    return null;
                map.put(key, value);
            }
            return map;
        }
        return leaf(item);
    }

    // A list, or a constructor's fields when alternative is set; a BREAK ends an indefinite list and marks it chunked.
    private static PlutusData decodeShallowList(List<DataItem> items, Long alternative, int depth)
            throws CborDeserializationException {
        List<PlutusData> list = new ArrayList<>(items.size());
        boolean chunked = false;
        for (int i = 0, size = items.size(); i < size; i++) {
            DataItem child = items.get(i);
            if (child == Special.BREAK) {
                chunked = true;
                break;
            }
            PlutusData data = decodeShallow(child, depth);
            if (data == null)
                return null;
            list.add(data);
        }
        ListPlutusData fields = new ListPlutusData(list, chunked);
        return alternative == null ? fields : new ConstrPlutusData(alternative, fields);
    }

    private static PlutusData run(DecodeFrame top) throws CborDeserializationException {
        while (true) {
            if (top.hasNext()) {
                DataItem child = top.next();
                DecodeFrame opened = open(child, top);
                if (opened != null)
                    top = opened;
                else
                    top.add(leaf(child));
            } else {
                PlutusData done = top.result();
                if (top.parent == null)
                    return done;
                top = top.parent;
                top.add(done);
            }
        }
    }

    // A frame for a list, map or constructor; null for a leaf.
    private static DecodeFrame open(DataItem item, DecodeFrame parent) throws CborDeserializationException {
        if (item instanceof Array) {
            if (item.getTag() == null)
                return new ListFrame(parent, ((Array) item).getDataItems(), null);
            return openConstr(item, parent);
        }
        if (item instanceof Map)
            return new MapFrame(parent, (Map) item);
        return null;
    }

    private static DecodeFrame openConstr(DataItem item, DecodeFrame parent) throws CborDeserializationException {
        Tag tag = item.getTag();
        List<DataItem> items = ((Array) item).getDataItems();
        long alternative = constrAlternative(tag, items);
        return new ListFrame(parent, constrFields(tag, items), alternative);
    }

    // A constructor is 121-127 or 1280-1400 [fields], or 102 [alternative, [fields]].
    private static long constrAlternative(Tag tag, List<DataItem> items) throws CborDeserializationException {
        if (tag.getValue() == GENERAL_FORM_TAG) {
            if (items.size() != 2)
                throw new CborDeserializationException("Cbor deserialization failed. Expected 2 DataItem, found : " + items.size());
            return ((UnsignedInteger) items.get(0)).getValue().longValue();
        }
        Long alternative = compactTagToAlternative(tag.getValue());
        if (alternative == null)
            throw new CborDeserializationException("Cbor deserialization failed. Unknown constructor tag " + tag.getValue());
        return alternative;
    }

    private static List<DataItem> constrFields(Tag tag, List<DataItem> items) {
        return tag.getValue() == GENERAL_FORM_TAG ? ((Array) items.get(1)).getDataItems() : items;
    }

    private static PlutusData leaf(DataItem item) throws CborDeserializationException {
        if (item == null)
            return null;
        if (item instanceof Number)
            return BigIntPlutusData.deserialize((Number) item);
        if (item instanceof ByteString) {
            Tag tag = item.getTag();
            if (tag != null && (tag.getValue() == PlutusData.BIG_UINT_TAG || tag.getValue() == PlutusData.BIG_NINT_TAG))
                return BigIntPlutusData.deserialize((ByteString) item);
            return BytesPlutusData.deserialize((ByteString) item);
        }
        if (item instanceof UnicodeString)
            return BytesPlutusData.deserialize((UnicodeString) item);
        throw new CborDeserializationException("Cbor deserialization failed. Invalid type. " + item);
    }

    private abstract static class DecodeFrame {
        final DecodeFrame parent;

        DecodeFrame(DecodeFrame parent) {
            this.parent = parent;
        }

        abstract boolean hasNext();

        abstract DataItem next();

        abstract void add(PlutusData child) throws CborDeserializationException;

        abstract PlutusData result();
    }

    // A list, or a constructor's fields when alternative is set. A BREAK ends an indefinite list and marks it chunked.
    private static final class ListFrame extends DecodeFrame {
        private final List<DataItem> items;
        private final Long alternative;
        private final List<PlutusData> list;
        private int index;
        private boolean chunked;

        ListFrame(DecodeFrame parent, List<DataItem> items, Long alternative) {
            super(parent);
            this.items = items;
            this.alternative = alternative;
            this.list = new ArrayList<>(items.size());
        }

        @Override
        boolean hasNext() {
            if (chunked || index == items.size())
                return false;
            if (items.get(index) == Special.BREAK) {
                chunked = true;
                return false;
            }
            return true;
        }

        @Override
        DataItem next() {
            return items.get(index++);
        }

        @Override
        void add(PlutusData child) throws CborDeserializationException {
            if (child == null)
                throw new CborDeserializationException("Null value found during PlutusData de-serialization");
            list.add(child);
        }

        @Override
        PlutusData result() {
            ListPlutusData fields = new ListPlutusData(list, chunked);
            if (alternative == null)
                return fields;
            return new ConstrPlutusData(alternative, fields);
        }
    }

    private static final class MapFrame extends DecodeFrame {
        private final EntryIterator entries;
        private final MapPlutusData map = new MapPlutusData();
        private PlutusData key;
        private boolean keyDone;

        MapFrame(DecodeFrame parent, Map source) {
            super(parent);
            this.entries = new EntryIterator(source);
        }

        @Override
        boolean hasNext() {
            return entries.hasNext();
        }

        @Override
        DataItem next() {
            return entries.next();
        }

        @Override
        void add(PlutusData child) {
            if (!keyDone) {
                key = child;
                keyDone = true;
            } else {
                map.put(key, child);
                keyDone = false;
            }
        }

        @Override
        PlutusData result() {
            return map;
        }
    }

    // Keys and values of a cbor-java map in order: its key list and values share the insertion order, so they are
    // zipped without looking keys up; get(key) is the fallback when the two differ in size.
    private static final class EntryIterator {
        private final Map map;
        private final Iterator<DataItem> keys;
        private final Iterator<DataItem> values;
        private DataItem key;
        private boolean onValue;

        EntryIterator(Map map) {
            this.map = map;
            Collection<DataItem> keyList = map.getKeys();
            Collection<DataItem> valueList = map.getValues();
            this.keys = keyList.iterator();
            this.values = keyList.size() == valueList.size() ? valueList.iterator() : null;
        }

        boolean hasNext() {
            return onValue || keys.hasNext();
        }

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

    // ---- PlutusData -> DataItem

    static DataItem encode(PlutusData data) throws CborSerializationException {
        EncodeFrame top = open(data, null);
        if (top == null)
            return data.serialize();
        if (top.done())
            return top.result();
        while (true) {
            if (top.hasNext()) {
                PlutusData child = top.next();
                EncodeFrame opened = open(child, top);
                if (opened == null) {
                    top.add(child.serialize());
                } else if (opened.done()) {
                    top.add(opened.result());
                } else {
                    top = opened;
                }
            } else {
                DataItem done = top.result();
                if (top.parent == null)
                    return done;
                top = top.parent;
                top.add(done);
            }
        }
    }

    // A frame for a list, map or constructor of this library; null for anything else, which serializes itself.
    private static EncodeFrame open(PlutusData data, EncodeFrame parent) {
        if (data instanceof ListPlutusData)
            return new ListOut(parent, (ListPlutusData) data, null);
        if (data instanceof ConstrPlutusData)
            return new ListOut(parent, ((ConstrPlutusData) data).getData(), (ConstrPlutusData) data);
        if (data instanceof MapPlutusData)
            return new MapOut(parent, (MapPlutusData) data);
        return null;
    }

    private abstract static class EncodeFrame {
        final EncodeFrame parent;

        EncodeFrame(EncodeFrame parent) {
            this.parent = parent;
        }

        // complete without visiting children (null or empty)
        abstract boolean done();

        abstract boolean hasNext();

        abstract PlutusData next();

        abstract void add(DataItem child) throws CborSerializationException;

        abstract DataItem result() throws CborSerializationException;
    }

    // A list, or a constructor's fields when constr is set.
    private static final class ListOut extends EncodeFrame {
        private final ConstrPlutusData constr;
        private final List<PlutusData> items;
        private final Array array;
        private int index;

        ListOut(EncodeFrame parent, ListPlutusData list, ConstrPlutusData constr) {
            super(parent);
            this.constr = constr;
            this.items = list != null ? list.getPlutusDataList() : null;
            this.array = items != null ? new Array(items.size() + 1) : null;
            if (items != null && !items.isEmpty() && list.isChunked())
                array.setChunked(true);
        }

        @Override
        boolean done() {
            return array == null || items.isEmpty();
        }

        @Override
        boolean hasNext() {
            return index < items.size();
        }

        @Override
        PlutusData next() {
            return items.get(index++);
        }

        @Override
        void add(DataItem child) throws CborSerializationException {
            if (child == null)
                throw new CborSerializationException("Cbor Serialization failed for plutus data. NULL serialized value found in the list");
            array.add(child);
        }

        @Override
        DataItem result() throws CborSerializationException {
            if (array != null && array.isChunked())
                array.add(Special.BREAK);
            if (constr == null)
                return array;
            Long compactTag = alternativeToCompactTag(constr.getAlternative());
            if (compactTag != null) {
                if (array == null)
                    throw new CborSerializationException("Cbor serialization failed for constr data. NULL serialized fields");
                array.setTag(compactTag);
                return array;
            }
            Array general = new Array();
            general.add(new UnsignedInteger(constr.getAlternative()));
            general.add(array);
            general.setTag(GENERAL_FORM_TAG);
            return general;
        }
    }

    private static final class MapOut extends EncodeFrame {
        private final Iterator<java.util.Map.Entry<PlutusData, PlutusData>> entries;
        private final EncodedKeyMap map;
        private PlutusData value;
        private DataItem key;
        private boolean onValue;

        MapOut(EncodeFrame parent, MapPlutusData data) {
            super(parent);
            this.entries = data.getMap() != null ? data.getMap().entrySet().iterator() : null;
            this.map = data.getMap() != null ? new EncodedKeyMap() : null;
        }

        @Override
        boolean done() {
            return map == null || (!onValue && !entries.hasNext());
        }

        @Override
        boolean hasNext() {
            return onValue || entries.hasNext();
        }

        @Override
        PlutusData next() {
            if (onValue)
                return value;
            java.util.Map.Entry<PlutusData, PlutusData> entry = entries.next();
            value = entry.getValue();
            onValue = true;
            return entry.getKey();
        }

        @Override
        void add(DataItem child) throws CborSerializationException {
            if (key == null) {
                if (child == null)
                    throw new CborSerializationException("Cbor serialization failed for PlutusData.  NULL serialized value found for key");
                key = child;
                return;
            }
            if (child == null)
                throw new CborSerializationException("Cbor serialization failed for PlutusData.  NULL serialized value found for value");
            map.put(key, child);
            key = null;
            onValue = false;
        }

        @Override
        DataItem result() {
            return map;
        }
    }

    // ---- equals and hashCode

    /**
     * Value equality of lists, maps and constructors, as the generated equals was: lists by order and chunked flag, maps
     * by entries regardless of order, constructors by alternative and fields. Both sides are reduced to their
     * {@link #equalityKey(PlutusData) equality keys}, so comparing never recurses, not even through map keys.
     */
    static boolean equal(PlutusData data, Object other) {
        if (data == other)
            return true;
        if (other == null || other.getClass() != data.getClass())
            return false;
        return Arrays.equals(equalityKey(data), equalityKey((PlutusData) other));
    }

    /**
     * A hash consistent with {@link #equal(PlutusData, Object)}, computed in one walk with an explicit stack: lists in
     * order with their chunked flag, maps regardless of order, constructors by alternative and fields. Nothing is
     * encoded, so hashing a map key is linear in its size.
     */
    static int hash(PlutusData root) {
        HashFrame top = null;
        PlutusData data = root;
        while (true) {
            HashFrame opened = openHash(data, top);
            int leafHash = opened == null ? leafHash(data) : 0;
            if (opened != null) {
                top = opened;
            } else if (top == null) {
                return leafHash;
            } else {
                top.add(leafHash);
            }
            while (!top.hasNext()) {
                int done = top.result();
                top = top.parent;
                if (top == null)
                    return done;
                top.add(done);
            }
            data = top.next();
        }
    }

    private static HashFrame openHash(PlutusData data, HashFrame parent) {
        if (data instanceof ConstrPlutusData) {
            ConstrPlutusData constr = (ConstrPlutusData) data;
            ListPlutusData fields = constr.getData();
            return new HashFrame(parent, fields == null ? null : List.of((PlutusData) fields).iterator(), false,
                    31 * KEY_CONSTR + Long.hashCode(constr.getAlternative()));
        }
        if (data instanceof ListPlutusData) {
            ListPlutusData list = (ListPlutusData) data;
            List<PlutusData> items = list.getPlutusDataList();
            return new HashFrame(parent, items == null ? null : items.iterator(), false,
                    31 * KEY_LIST + (list.isChunked() ? 1 : 0) + (items == null ? 7 : 0));
        }
        if (data instanceof MapPlutusData) {
            java.util.Map<PlutusData, PlutusData> map = ((MapPlutusData) data).getMap();
            return new HashFrame(parent, map == null ? null : new MapItems(map).iterator(), true,
                    31 * KEY_MAP + (map == null ? 7 : 0));
        }
        return null;
    }

    private static int leafHash(PlutusData data) {
        if (data == null)
            return KEY_NULL;
        if (data instanceof BigIntPlutusData)
            return 31 * KEY_INT + java.util.Objects.hashCode(((BigIntPlutusData) data).getValue());
        if (data instanceof BytesPlutusData)
            return 31 * KEY_BYTES + Arrays.hashCode(((BytesPlutusData) data).getValue());
        return 31 * KEY_OTHER + Arrays.hashCode(equalityKey(data));
    }

    private static final class HashFrame {
        final HashFrame parent;
        private final Iterator<PlutusData> items;
        private final boolean pairs;
        private int hash;
        private int entries;
        private int keyHash;
        private boolean onValue;

        HashFrame(HashFrame parent, Iterator<PlutusData> items, boolean pairs, int seed) {
            this.parent = parent;
            this.items = items;
            this.pairs = pairs;
            this.hash = seed;
        }

        boolean hasNext() {
            return items != null && items.hasNext();
        }

        PlutusData next() {
            return items.next();
        }

        void add(int childHash) {
            if (!pairs) {
                hash = 31 * hash + childHash;
            } else if (!onValue) {
                keyHash = childHash;
                onValue = true;
            } else {
                entries += 31 * keyHash + childHash; // regardless of order, like AbstractMap
                onValue = false;
            }
        }

        int result() {
            return pairs ? 31 * hash + entries : hash;
        }
    }

    // A map's keys and values alternately.
    private static final class MapItems implements Iterable<PlutusData> {
        private final java.util.Map<PlutusData, PlutusData> map;

        MapItems(java.util.Map<PlutusData, PlutusData> map) {
            this.map = map;
        }

        @Override
        public Iterator<PlutusData> iterator() {
            Iterator<java.util.Map.Entry<PlutusData, PlutusData>> entries = map.entrySet().iterator();
            return new Iterator<>() {
                private PlutusData value;
                private boolean onValue;

                @Override
                public boolean hasNext() {
                    return onValue || entries.hasNext();
                }

                @Override
                public PlutusData next() {
                    if (onValue) {
                        onValue = false;
                        return value;
                    }
                    java.util.Map.Entry<PlutusData, PlutusData> entry = entries.next();
                    value = entry.getValue();
                    onValue = true;
                    return entry.getKey();
                }
            };
        }
    }

    /**
     * An encoding that two models share exactly when they are equal: a type byte per node, counts before children, the
     * chunked flag of lists, and map entries sorted by their key's encoding. Integers and byte strings are written by
     * value; any other implementation of {@link PlutusData} by its own serialization.
     */
    static byte[] equalityKey(PlutusData root) {
        Bytes out = new Bytes();
        KeyFrame top = writeKeyNode(root, out, null);
        while (top != null) {
            if (top.hasNext()) {
                PlutusData child = top.next();
                Bytes childOut = top.childBuffer();
                KeyFrame opened = writeKeyNode(child, childOut, top);
                if (opened != null)
                    top = opened;
                else
                    top.childDone(childOut);
            } else {
                KeyFrame done = top;
                done.finish();
                top = done.parent;
                if (top != null)
                    top.childDone(done.out);
            }
        }
        return out.toByteArray();
    }

    private static final int KEY_NULL = 0;
    private static final int KEY_INT = 1;
    private static final int KEY_BYTES = 2;
    private static final int KEY_LIST = 3;
    private static final int KEY_CONSTR = 4;
    private static final int KEY_MAP = 5;
    private static final int KEY_OTHER = 6;

    // Writes a leaf completely, or a container's head and returns a frame for its children.
    private static KeyFrame writeKeyNode(PlutusData data, Bytes out, KeyFrame parent) {
        if (data == null) {
            out.write(KEY_NULL);
            return null;
        }
        if (data instanceof BigIntPlutusData) {
            BigInteger value = ((BigIntPlutusData) data).getValue();
            out.write(KEY_INT);
            out.writeSized(value == null ? null : value.toByteArray());
            return null;
        }
        if (data instanceof BytesPlutusData) {
            out.write(KEY_BYTES);
            out.writeSized(((BytesPlutusData) data).getValue());
            return null;
        }
        if (data instanceof ConstrPlutusData) {
            ConstrPlutusData constr = (ConstrPlutusData) data;
            out.write(KEY_CONSTR);
            out.writeLong(constr.getAlternative());
            return listFrame(constr.getData(), out, parent);
        }
        if (data instanceof ListPlutusData)
            return listFrame((ListPlutusData) data, out, parent);
        if (data instanceof MapPlutusData) {
            java.util.Map<PlutusData, PlutusData> map = ((MapPlutusData) data).getMap();
            out.write(KEY_MAP);
            if (map == null) {
                out.writeInt(-1);
                return null;
            }
            out.writeInt(map.size());
            if (map.size() > 1)
                return new SortedEntriesFrame(parent, out, map);
            return map.isEmpty() ? null : new KeyListFrame(parent, out, new MapItems(map));
        }
        out.write(KEY_OTHER);
        try {
            out.writeSized(com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.serialize(data.serialize()));
        } catch (Exception e) {
            throw new com.bloxbean.cardano.client.exception.CborRuntimeException("Unable to serialize " + data.getClass(), e);
        }
        return null;
    }

    private static KeyFrame listFrame(ListPlutusData list, Bytes out, KeyFrame parent) {
        out.write(KEY_LIST);
        if (list == null) {
            out.write(KEY_NULL);
            return null;
        }
        out.write(list.isChunked() ? 1 : 0);
        List<PlutusData> items = list.getPlutusDataList();
        out.writeInt(items == null ? -1 : items.size());
        return items == null || items.isEmpty() ? null : new KeyListFrame(parent, out, items);
    }

    private abstract static class KeyFrame {
        final KeyFrame parent;
        final Bytes out;

        KeyFrame(KeyFrame parent, Bytes out) {
            this.parent = parent;
            this.out = out;
        }

        abstract boolean hasNext();

        abstract PlutusData next();

        Bytes childBuffer() {
            return out;
        }

        void childDone(Bytes child) {
        }

        void finish() {
        }
    }

    private static final class KeyListFrame extends KeyFrame {
        private final Iterator<PlutusData> items;

        KeyListFrame(KeyFrame parent, Bytes out, Iterable<PlutusData> items) {
            super(parent, out);
            this.items = items.iterator();
        }

        @Override
        boolean hasNext() {
            return items.hasNext();
        }

        @Override
        PlutusData next() {
            return items.next();
        }
    }

    // Each key and value is written into its own buffer; the entries are then written sorted by key bytes.
    private static final class SortedEntriesFrame extends KeyFrame {
        private final Iterator<java.util.Map.Entry<PlutusData, PlutusData>> entries;
        private final TreeMap<byte[], byte[]> sorted = new TreeMap<>(Arrays::compareUnsigned);
        private PlutusData value;
        private byte[] keyBytes;
        private boolean onValue;

        SortedEntriesFrame(KeyFrame parent, Bytes out, java.util.Map<PlutusData, PlutusData> map) {
            super(parent, out);
            this.entries = map.entrySet().iterator();
        }

        @Override
        boolean hasNext() {
            return onValue || entries.hasNext();
        }

        @Override
        PlutusData next() {
            if (onValue)
                return value;
            java.util.Map.Entry<PlutusData, PlutusData> entry = entries.next();
            value = entry.getValue();
            onValue = true;
            return entry.getKey();
        }

        @Override
        Bytes childBuffer() {
            return new Bytes();
        }

        @Override
        void childDone(Bytes child) {
            if (keyBytes == null) {
                keyBytes = child.toByteArray();
            } else {
                sorted.put(keyBytes, child.toByteArray());
                keyBytes = null;
                onValue = false;
            }
        }

        @Override
        void finish() {
            for (java.util.Map.Entry<byte[], byte[]> entry : sorted.entrySet()) {
                out.write(entry.getKey());
                out.write(entry.getValue());
            }
        }
    }

    private static final class Bytes {
        private byte[] bytes = new byte[32];
        private int size;

        void write(int b) {
            ensure(1);
            bytes[size++] = (byte) b;
        }

        void write(byte[] b) {
            ensure(b.length);
            System.arraycopy(b, 0, bytes, size, b.length);
            size += b.length;
        }

        void writeInt(int v) {
            for (int shift = 24; shift >= 0; shift -= 8)
                write(v >> shift);
        }

        void writeLong(long v) {
            for (int shift = 56; shift >= 0; shift -= 8)
                write((int) (v >> shift));
        }

        void writeSized(byte[] b) {
            if (b == null) {
                writeInt(-1);
                return;
            }
            writeInt(b.length);
            write(b);
        }

        byte[] toByteArray() {
            return Arrays.copyOf(bytes, size);
        }

        private void ensure(int more) {
            if (size + more > bytes.length)
                bytes = Arrays.copyOf(bytes, Math.max(bytes.length * 2, size + more));
        }
    }

    // ---- constructor tags

    // Alternatives 0-6 are tags 121-127, 7-127 are tags 1280-1400, anything else uses tag 102.
    static Long alternativeToCompactTag(long alternative) {
        if (alternative <= 6)
            return 121 + alternative;
        else if (alternative <= 127)
            return 1280 - 7 + alternative;
        return null;
    }

    static Long compactTagToAlternative(long tag) {
        if (tag >= 121 && tag <= 127)
            return tag - 121;
        else if (tag >= 1280 && tag <= 1400)
            return tag - 1280 + 7;
        return null;
    }
}
