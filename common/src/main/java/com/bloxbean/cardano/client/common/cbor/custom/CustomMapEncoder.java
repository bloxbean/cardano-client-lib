package com.bloxbean.cardano.client.common.cbor.custom;

import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.encoder.MapEncoder;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.MajorType;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.SimpleValue;
import com.google.common.primitives.UnsignedBytes;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.TreeMap;

/**
 * This class is exactly same as MapEncoder except
 *  - ignore canonical ordering if Map is of type SortedMap
 *  - so, if DataItem = SortedMap, ignore canonical encoding even if canonical = true
 */
public class CustomMapEncoder extends MapEncoder {
    public CustomMapEncoder(CborEncoder encoder, OutputStream outputStream) {
        super(encoder, outputStream);
    }

    @Override
    public void encode(Map map) throws CborException {
        Collection<DataItem> keys = map.getKeys();

        if (map.isChunked()) {
            encodeTypeChunked(MajorType.MAP);
        } else {
            encodeTypeAndLength(MajorType.MAP, keys.size());
        }

        if (keys.isEmpty()) {
            return;
        }

        if (map.isChunked()) {
            encodeNonCanonical(map);
            encoder.encode(SimpleValue.BREAK);
        } else {
            if (encoder.isCanonical() && !(map instanceof SortedMap)) { //If it's already a SortedMap, ignore canonical attr
                encodeCanonical(map);
            } else {
                encodeNonCanonical(map);
            }
        }
    }

    private void encodeNonCanonical(Map map) throws CborException {
        for (DataItem key : map.getKeys()) {
            encoder.encode(key);
            encoder.encode(map.get(key));
        }
    }

    private void encodeCanonical(Map map) throws CborException {
        /**
         * From https://tools.ietf.org/html/rfc7049#section-3.9
         *
         * Canonical CBOR
         *
         * The keys in every map must be sorted lowest value to highest. Sorting is
         * performed on the bytes of the representation of the key data items without
         * paying attention to the 3/5 bit splitting for major types. (Note that this
         * rule allows maps that have keys of different types, even though that is
         * probably a bad practice that could lead to errors in some canonicalization
         * implementations.) The sorting rules are:
         *
         * If two keys have different lengths, the shorter one sorts earlier;
         *
         * If two keys have the same length, the one with the lower value in (byte-wise)
         * lexical order sorts earlier.
         */

        TreeMap<byte[], byte[]> sortedMap = new TreeMap<>(UnsignedBytes.lexicographicalComparator());

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        CborEncoder e = new CustomCborEncoder(byteArrayOutputStream);
        for (DataItem key : map.getKeys()) {
            // Key
            e.encode(key);
            byte[] keyBytes = byteArrayOutputStream.toByteArray();
            byteArrayOutputStream.reset();
            // Value
            e.encode(map.get(key));
            byte[] valueBytes = byteArrayOutputStream.toByteArray();
            byteArrayOutputStream.reset();
            sortedMap.put(keyBytes, valueBytes);
        }
        for (java.util.Map.Entry<byte[], byte[]> entry : sortedMap.entrySet()) {
            write(entry.getKey());
            write(entry.getValue());
        }
    }
}
