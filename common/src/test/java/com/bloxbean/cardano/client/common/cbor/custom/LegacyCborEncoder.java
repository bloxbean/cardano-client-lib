package com.bloxbean.cardano.client.common.cbor.custom;

import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.encoder.MapEncoder;
import co.nstant.in.cbor.model.*;
import com.google.common.primitives.UnsignedBytes;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.TreeMap;

/**
 * The recursive encoder that {@link CustomCborEncoder} was before it became iterative (with the CustomMapEncoder it
 * used), kept as a test oracle: the iterative encoder must produce the same bytes.
 */
public class LegacyCborEncoder extends CborEncoder {
    private final LegacyMapEncoder mapEncoder;
    private final CustomByteStringEncoder byteStringEncoder;

    public LegacyCborEncoder(OutputStream outputStream) {
        super(outputStream);
        this.mapEncoder = new LegacyMapEncoder(this, outputStream);
        this.byteStringEncoder = new CustomByteStringEncoder(this, outputStream);
    }

    public static byte[] encode(DataItem item, boolean canonical) throws CborException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LegacyCborEncoder encoder = new LegacyCborEncoder(out);
        if (!canonical)
            encoder.nonCanonical();
        encoder.encode(item);
        return out.toByteArray();
    }

    @Override
    public void encode(DataItem dataItem) throws CborException {
        if (dataItem == null) {
            dataItem = SimpleValue.NULL;
        }
        if (dataItem.getMajorType().equals(MajorType.MAP)) {
            if (dataItem.hasTag()) {
                encode(dataItem.getTag());
            }
            mapEncoder.encode((Map) dataItem);
        } else if (dataItem.getMajorType().equals(MajorType.BYTE_STRING)) {
            if (dataItem.hasTag()) {
                encode(dataItem.getTag());
            }
            byteStringEncoder.encode((ByteString) dataItem);
        } else {
            super.encode(dataItem);
        }
    }

    private static class LegacyMapEncoder extends MapEncoder {
        LegacyMapEncoder(CborEncoder encoder, OutputStream outputStream) {
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
                if (encoder.isCanonical() && !(map instanceof SortedMap)) {
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
            TreeMap<byte[], byte[]> sortedMap = new TreeMap<>(UnsignedBytes.lexicographicalComparator());
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            CborEncoder e = new LegacyCborEncoder(byteArrayOutputStream);
            for (DataItem key : map.getKeys()) {
                e.encode(key);
                byte[] keyBytes = byteArrayOutputStream.toByteArray();
                byteArrayOutputStream.reset();
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
}
