package com.bloxbean.cardano.statetrees.jmt;

import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Leaf node holding hashed key/value pairs.
 */
public final class JmtLeafNode implements JmtNode {

    private final byte[] keyHash;
    private final byte[] valueHash;

    private JmtLeafNode(byte[] keyHash, byte[] valueHash) {
        this.keyHash = Arrays.copyOf(keyHash, keyHash.length);
        this.valueHash = Arrays.copyOf(valueHash, valueHash.length);
    }

    public static JmtLeafNode of(byte[] keyHash, byte[] valueHash) {
        if (keyHash == null || valueHash == null) {
            throw new IllegalArgumentException("Leaf hashes cannot be null");
        }
        if (keyHash.length != valueHash.length) {
            throw new IllegalArgumentException("keyHash and valueHash must be same length");
        }
        return new JmtLeafNode(keyHash, valueHash);
    }

    public byte[] keyHash() {
        return Arrays.copyOf(keyHash, keyHash.length);
    }

    public byte[] valueHash() {
        return Arrays.copyOf(valueHash, valueHash.length);
    }

    @Override
    public NodeTag tag() {
        return NodeTag.LEAF;
    }

    @Override
    public byte[] encode() {
        try {
            Array arr = new Array();
            arr.add(new ByteString(new byte[]{(byte) tag().tag()}));
            arr.add(new ByteString(keyHash));
            arr.add(new ByteString(valueHash));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new CborEncoder(baos).encode(arr);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode JMT leaf node", e);
        }
    }

    static JmtLeafNode decode(Array array) {
        if (array.getDataItems().size() != 3) {
            throw new IllegalArgumentException("Leaf node must contain [tag, keyHash, valueHash]");
        }
        byte[] key = ((ByteString) array.getDataItems().get(1)).getBytes();
        byte[] value = ((ByteString) array.getDataItems().get(2)).getBytes();
        return new JmtLeafNode(key, value);
    }
}

