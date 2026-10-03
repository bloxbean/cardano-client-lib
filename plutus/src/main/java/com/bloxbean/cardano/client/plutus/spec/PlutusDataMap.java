package com.bloxbean.cardano.client.plutus.spec;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The default map of a {@link MapPlutusData}: insertion-ordered like {@link LinkedHashMap}, with the hash of each list,
 * map or constructor key computed once, when the key is put, by {@link PlutusDataCodec#hash(PlutusData)}. Hashing a map
 * then uses those stored hashes instead of walking the keys again, so data whose map keys are maps (whose keys are maps,
 * and so on) hashes and decodes in time and space linear in its size. Integer and byte string keys, whose own
 * {@code hashCode} and {@code equals} are cheap and do not recurse, are stored as they are. As in any hash map, a key
 * must not change while it is in the map.
 */
final class PlutusDataMap extends AbstractMap<PlutusData, PlutusData> {
    // a key as stored: the PlutusData itself (null, an integer or a byte string), or a Key with its hash
    private final LinkedHashMap<Object, PlutusData> entries = new LinkedHashMap<>();

    @Override
    public PlutusData put(PlutusData key, PlutusData value) {
        return entries.put(stored(key), value);
    }

    @Override
    public PlutusData get(Object key) {
        return isKey(key) ? entries.get(stored((PlutusData) key)) : null;
    }

    @Override
    public boolean containsKey(Object key) {
        return isKey(key) && entries.containsKey(stored((PlutusData) key));
    }

    @Override
    public PlutusData remove(Object key) {
        return isKey(key) ? entries.remove(stored((PlutusData) key)) : null;
    }

    @Override
    public int size() {
        return entries.size();
    }

    @Override
    public void clear() {
        entries.clear();
    }

    @Override
    public Set<Entry<PlutusData, PlutusData>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public Iterator<Entry<PlutusData, PlutusData>> iterator() {
                Iterator<Entry<Object, PlutusData>> iterator = entries.entrySet().iterator();
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return iterator.hasNext();
                    }

                    @Override
                    public Entry<PlutusData, PlutusData> next() {
                        return new EntryView(iterator.next());
                    }

                    @Override
                    public void remove() {
                        iterator.remove();
                    }
                };
            }

            @Override
            public int size() {
                return entries.size();
            }
        };
    }

    // The entries with their keys as stored, for PlutusDataCodec.hash (see hash(Object)).
    Iterator<Entry<Object, PlutusData>> keyedEntries() {
        return entries.entrySet().iterator();
    }

    // The structural hash of a key as stored: computed for an integer or byte string (a cheap leaf hash), stored for the
    // others.
    static int hash(Object storedKey) {
        return storedKey instanceof Key ? ((Key) storedKey).hash : PlutusDataCodec.hash((PlutusData) storedKey);
    }

    private static Object stored(PlutusData key) {
        if (key == null || key instanceof BigIntPlutusData || key instanceof BytesPlutusData)
            return key;
        return new Key(key);
    }

    private static PlutusData item(Object storedKey) {
        return storedKey instanceof Key ? ((Key) storedKey).item : (PlutusData) storedKey;
    }

    private static boolean isKey(Object key) {
        return key == null || key instanceof PlutusData;
    }

    // A list, map or constructor key with its hash, computed once.
    static final class Key {
        final PlutusData item;
        final int hash;

        Key(PlutusData item) {
            this.item = item;
            this.hash = PlutusDataCodec.hash(item);
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof Key))
                return false;
            Key other = (Key) object;
            return item == other.item || (hash == other.hash && Objects.equals(item, other.item));
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    // An entry as the Map interface shows it: its key is the PlutusData; setValue writes through.
    private static final class EntryView implements Entry<PlutusData, PlutusData> {
        private final Entry<Object, PlutusData> entry;

        EntryView(Entry<Object, PlutusData> entry) {
            this.entry = entry;
        }

        @Override
        public PlutusData getKey() {
            return item(entry.getKey());
        }

        @Override
        public PlutusData getValue() {
            return entry.getValue();
        }

        @Override
        public PlutusData setValue(PlutusData value) {
            return entry.setValue(value);
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof Entry))
                return false;
            Entry<?, ?> other = (Entry<?, ?>) object;
            return Objects.equals(getKey(), other.getKey()) && Objects.equals(getValue(), other.getValue());
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(getKey()) ^ Objects.hashCode(getValue());
        }
    }
}
