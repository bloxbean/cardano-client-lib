package com.bloxbean.cardano.client.common.cbor.custom;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.MajorType;
import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.exception.CborRuntimeException;

import java.util.AbstractCollection;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * A cbor-java {@link Map} whose entries are looked up by the CBOR encoding of their keys instead of
 * {@link DataItem#hashCode()}, which recurses through nested keys. Keys of any nesting depth can therefore be stored and
 * found on any thread.
 * <p>
 * Untagged integers, strings and simple values are compared with their own {@code equals}; any other key by its
 * canonical encoding with every nested map sorted, built only when two keys' hashes meet (the hash is computed without
 * it, in time linear in the key's size). Two keys therefore match exactly when cbor-java considers them equal
 * (numbers by value, maps regardless of entry order, arrays by order, tags and chunked flags included). Like cbor-java's map, a repeated key keeps its first position and takes the last value, so this model is
 * lossy where Cardano keeps every entry (Plutus data and metadatum maps are lists of pairs); read such maps from the
 * original bytes with {@link com.bloxbean.cardano.client.common.cbor.CborSpan#entries()} when that matters.
 * <p>
 * {@code CborSerializationUtil.deserialize} returns maps of this type. {@code equals} accepts any cbor-java map, but a
 * plain cbor-java map compares its own private storage and so never equals a non-empty instance of this class; compare
 * with this map as the receiver, or compare encodings.
 */
public class EncodedKeyMap extends Map {
    private final LinkedHashMap<Key, DataItem> entries;

    public EncodedKeyMap() {
        this(16);
    }

    public EncodedKeyMap(int initialCapacity) {
        super(0);
        entries = new LinkedHashMap<>(initialCapacity);
    }

    @Override
    public Map put(DataItem key, DataItem value) {
        entries.put(new Key(key), value);
        return this;
    }

    @Override
    public DataItem get(DataItem key) {
        return entries.get(new Key(key));
    }

    @Override
    public DataItem remove(DataItem key) {
        return entries.remove(new Key(key));
    }

    /**
     * @return the keys in insertion order (a read-only view)
     */
    @Override
    public Collection<DataItem> getKeys() {
        return new AbstractCollection<>() {
            @Override
            public Iterator<DataItem> iterator() {
                Iterator<Key> keys = entries.keySet().iterator();
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return keys.hasNext();
                    }

                    @Override
                    public DataItem next() {
                        return keys.next().item;
                    }
                };
            }

            @Override
            public int size() {
                return entries.size();
            }
        };
    }

    /**
     * @return the values, in the same order as {@link #getKeys()} (a read-only view)
     */
    @Override
    public Collection<DataItem> getValues() {
        return Collections.unmodifiableCollection(entries.values());
    }

    // The entries with their keys' hash records, for CustomCborEncoder.keyHash.
    Iterator<java.util.Map.Entry<Key, DataItem>> keyedEntries() {
        return entries.entrySet().iterator();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (!(object instanceof Map))
            return false;
        Map other = (Map) object;
        if (!Objects.equals(getTag(), other.getTag()) || isChunked() != other.isChunked()
                || entries.size() != other.getKeys().size())
            return false;
        for (java.util.Map.Entry<Key, DataItem> entry : entries.entrySet()) {
            DataItem otherValue = other.get(entry.getKey().item);
            if (!Objects.equals(entry.getValue(), otherValue))
                return false;
        }
        return true;
    }

    // The same formula as cbor-java's Map, so equal maps of either kind hash alike.
    @Override
    public int hashCode() {
        int hash = super.hashCode();
        int entryHash = 0;
        for (java.util.Map.Entry<Key, DataItem> entry : entries.entrySet())
            entryHash += Objects.hashCode(entry.getKey().item) ^ Objects.hashCode(entry.getValue());
        return hash ^ entryHash;
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder(isChunked() ? "{_ " : "{ ");
        for (java.util.Map.Entry<Key, DataItem> entry : entries.entrySet())
            out.append(entry.getKey().item).append(": ").append(entry.getValue()).append(", ");
        if (out.toString().endsWith(", "))
            out.setLength(out.length() - 2);
        return out.append(" }").toString();
    }

    // An untagged leaf (integer, string, simple value) is compared with its own equals and hashCode, which do not recurse;
    // any other key (a container, or a tagged item) by its key encoding, which is only built when two keys' hashes meet.
    static final class Key {
        final DataItem item;
        final boolean leaf;
        final int hash;
        private byte[] encoded;

        Key(DataItem item) {
            this.item = item;
            this.leaf = isUntaggedLeaf(item);
            try {
                this.hash = leaf ? item.hashCode() : CustomCborEncoder.keyHash(item);
            } catch (CborException e) {
                throw new CborRuntimeException("Unable to encode map key " + item, e);
            }
        }

        private static boolean isUntaggedLeaf(DataItem item) {
            return item != null && !item.hasTag() && item.getMajorType() != MajorType.ARRAY
                    && item.getMajorType() != MajorType.MAP && item.getMajorType() != MajorType.TAG;
        }

        private byte[] encoded() {
            if (encoded == null) {
                try {
                    encoded = CustomCborEncoder.encodeKey(item);
                } catch (CborException e) {
                    throw new CborRuntimeException("Unable to encode map key " + item, e);
                }
            }
            return encoded;
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof Key))
                return false;
            Key other = (Key) object;
            // the same key object always matches, as in cbor-java's HashMap, even a NaN float
            if (item == other.item)
                return true;
            if (leaf || other.leaf)
                return leaf && other.leaf && item.equals(other.item);
            return hash == other.hash && Arrays.equals(encoded(), other.encoded());
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
