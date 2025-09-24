package com.bloxbean.cardano.statetrees.api;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Classic (node-encoding) MPT proof: an ordered list of CBOR-encoded nodes (root → terminal).
 */
public final class ClassicProof {
    private final List<byte[]> nodes;

    public ClassicProof(List<byte[]> nodes) {
        Objects.requireNonNull(nodes, "nodes");
        List<byte[]> copy = new ArrayList<>(nodes.size());
        for (byte[] n : nodes) copy.add(n == null ? null : n.clone());
        this.nodes = Collections.unmodifiableList(copy);
    }

    public List<byte[]> nodes() {
        return nodes;
    }

    /**
     * Decodes a Classic proof from wire: CBOR array of ByteStrings, each node's CBOR.
     */
    public static ClassicProof fromWire(byte[] wire) {
        Objects.requireNonNull(wire, "wire");
        try {
            List<DataItem> items = new CborDecoder(new ByteArrayInputStream(wire)).decode();
            if (items.isEmpty() || !(items.get(0) instanceof Array)) {
                throw new IllegalArgumentException("Classic proof wire must be an array of ByteStrings");
            }
            Array arr = (Array) items.get(0);
            List<byte[]> nodes = new ArrayList<>(arr.getDataItems().size());
            for (DataItem di : arr.getDataItems()) {
                if (!(di instanceof ByteString)) {
                    throw new IllegalArgumentException("Classic step must be a ByteString containing node CBOR");
                }
                nodes.add(((ByteString) di).getBytes());
            }
            return new ClassicProof(nodes);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decode Classic proof", e);
        }
    }
}

