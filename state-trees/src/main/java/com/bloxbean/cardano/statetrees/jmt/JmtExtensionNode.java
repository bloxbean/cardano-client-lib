package com.bloxbean.cardano.statetrees.jmt;

import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Explicit extension node carrying an HP-encoded path segment.
 */
public final class JmtExtensionNode implements JmtNode {

    private final byte[] hpBytes;
    private final byte[] childHash;

    private JmtExtensionNode(byte[] hpBytes, byte[] childHash) {
        this.hpBytes = Arrays.copyOf(hpBytes, hpBytes.length);
        this.childHash = Arrays.copyOf(childHash, childHash.length);
    }

    public static JmtExtensionNode of(byte[] hpBytes, byte[] childHash) {
        if (hpBytes == null || childHash == null) {
            throw new IllegalArgumentException("Extension node fields cannot be null");
        }
        return new JmtExtensionNode(hpBytes, childHash);
    }

    public byte[] hpBytes() {
        return Arrays.copyOf(hpBytes, hpBytes.length);
    }

    public byte[] childHash() {
        return Arrays.copyOf(childHash, childHash.length);
    }

    @Override
    public NodeTag tag() {
        return NodeTag.EXTENSION;
    }

    @Override
    public byte[] encode() {
        try {
            Array arr = new Array();
            arr.add(new ByteString(new byte[]{(byte) tag().tag()}));
            arr.add(new ByteString(hpBytes));
            arr.add(new ByteString(childHash));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new CborEncoder(baos).encode(arr);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode JMT extension node", e);
        }
    }

    static JmtExtensionNode decode(Array array) {
        if (array.getDataItems().size() != 3) {
            throw new IllegalArgumentException("Extension node must have [tag, hpBytes, childHash]");
        }
        byte[] hp = ((ByteString) array.getDataItems().get(1)).getBytes();
        byte[] child = ((ByteString) array.getDataItems().get(2)).getBytes();
        return new JmtExtensionNode(hp, child);
    }
}

