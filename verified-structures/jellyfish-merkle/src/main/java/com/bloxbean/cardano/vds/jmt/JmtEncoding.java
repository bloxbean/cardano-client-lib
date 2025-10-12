package com.bloxbean.cardano.vds.jmt;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;

import java.io.ByteArrayInputStream;
import java.util.List;

/**
 * CBOR encoding helpers for JMT nodes.
 */
public final class JmtEncoding {

    private JmtEncoding() {
        throw new AssertionError("Utility class");
    }

    public static JmtNode decode(byte[] encoded) {
        if (encoded == null) throw new IllegalArgumentException("encoded bytes cannot be null");
        try {
            List<DataItem> items = new CborDecoder(new ByteArrayInputStream(encoded)).decode();
            if (items.isEmpty()) throw new IllegalArgumentException("Empty CBOR payload");
            DataItem di = items.get(0);
            if (!(di instanceof Array)) {
                throw new IllegalArgumentException("Expected CBOR array, got " + di.getMajorType());
            }
            Array array = (Array) di;
            if (array.getDataItems().isEmpty()) {
                throw new IllegalArgumentException("Node array missing tag");
            }
            DataItem tagItem = array.getDataItems().get(0);
            if (!(tagItem instanceof ByteString)) {
                throw new IllegalArgumentException("Node tag must be ByteString");
            }
            byte[] tagBytes = ((ByteString) tagItem).getBytes();
            if (tagBytes.length != 1) {
                throw new IllegalArgumentException("Node tag must be single byte, got " + tagBytes.length);
            }
            NodeTag tag = NodeTag.fromByte(tagBytes[0]);
            switch (tag) {
                case INTERNAL:
                    return JmtInternalNode.decode(array);
                case LEAF:
                    return JmtLeafNode.decode(array);
                case EXTENSION:
                    return JmtExtensionNode.decode(array);
                default:
                    throw new IllegalArgumentException("Unhandled node tag: " + tag);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode JMT node", e);
        }
    }
}

