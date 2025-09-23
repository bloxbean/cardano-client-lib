package com.bloxbean.cardano.statetrees.jmt;

import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Radix-16 internal node following ADR-0004 encoding.
 */
public final class JmtInternalNode implements JmtNode {

    private final int bitmap;
    private final byte[][] childHashes;
    private final byte[] compressedPath;

    private JmtInternalNode(int bitmap, byte[][] childHashes, byte[] compressedPath) {
        this.bitmap = bitmap & 0xFFFF;
        this.childHashes = deepCopy(childHashes);
        this.compressedPath = compressedPath == null ? null : Arrays.copyOf(compressedPath, compressedPath.length);
    }

    public static JmtInternalNode of(int bitmap, byte[][] childHashes, byte[] compressedPath) {
        int expected = Integer.bitCount(bitmap & 0xFFFF);
        if (childHashes.length != expected) {
            throw new IllegalArgumentException("Child hash count " + childHashes.length + " != bitmap popcount " + expected);
        }
        for (byte[] child : childHashes) {
            if (child == null) {
                throw new IllegalArgumentException("Child hash cannot be null when present in bitmap");
            }
        }
        return new JmtInternalNode(bitmap, childHashes, compressedPath);
    }

    public int bitmap() {
        return bitmap;
    }

    public byte[][] childHashes() {
        return deepCopy(childHashes);
    }

    public byte[] compressedPath() {
        return compressedPath == null ? null : Arrays.copyOf(compressedPath, compressedPath.length);
    }

    @Override
    public NodeTag tag() {
        return NodeTag.INTERNAL;
    }

    @Override
    public byte[] encode() {
        try {
            Array arr = new Array();
            arr.add(new ByteString(new byte[]{(byte) tag().tag()}));
            arr.add(new UnsignedInteger(bitmap & 0xFFFF));
            for (byte[] child : childHashes) {
                arr.add(new ByteString(child));
            }
            if (compressedPath != null && compressedPath.length > 0) {
                arr.add(new ByteString(compressedPath));
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new CborEncoder(baos).encode(arr);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode JMT internal node", e);
        }
    }

    static JmtInternalNode decode(Array array) {
        if (array.getDataItems().size() < 2) {
            throw new IllegalArgumentException("Internal node array too small");
        }
        int bitmap = ((UnsignedInteger) array.getDataItems().get(1)).getValue().intValue();
        int childCount = Integer.bitCount(bitmap & 0xFFFF);
        if (array.getDataItems().size() < 2 + childCount) {
            throw new IllegalArgumentException("Internal node missing child hashes");
        }

        byte[][] childHashes = new byte[childCount][];
        for (int i = 0; i < childCount; i++) {
            byte[] child = ((ByteString) array.getDataItems().get(2 + i)).getBytes();
            childHashes[i] = Arrays.copyOf(child, child.length);
        }

        byte[] hp = null;
        if (array.getDataItems().size() == 2 + childCount + 1) {
            DataItem di = array.getDataItems().get(2 + childCount);
            if (di instanceof ByteString) {
                byte[] packed = ((ByteString) di).getBytes();
                hp = Arrays.copyOf(packed, packed.length);
            } else {
                throw new IllegalArgumentException("Expected ByteString for compressed path");
            }
        } else if (array.getDataItems().size() > 2 + childCount + 1) {
            throw new IllegalArgumentException("Unexpected additional fields in internal node");
        }

        return new JmtInternalNode(bitmap, childHashes, hp);
    }

    private static byte[][] deepCopy(byte[][] src) {
        byte[][] copy = new byte[src.length][];
        for (int i = 0; i < src.length; i++) {
            copy[i] = src[i] == null ? null : Arrays.copyOf(src[i], src[i].length);
        }
        return copy;
    }
}
