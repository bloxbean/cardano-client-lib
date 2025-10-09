package com.bloxbean.cardano.statetrees.rocksdb.mpt.gc.internal;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses encoded MPT nodes to extract child hashes without needing internal types.
 */
public final class NodeRefParser {
    public static List<byte[]> childRefs(byte[] enc) {
        try {
            List<DataItem> items = new CborDecoder(new ByteArrayInputStream(enc)).decode();
            if (items.isEmpty()) return java.util.Collections.emptyList();
            DataItem di = items.get(0);
            if (!(di instanceof Array)) return java.util.Collections.emptyList();
            Array arr = (Array) di;
            int size = arr.getDataItems().size();
            List<byte[]> refs = new ArrayList<>();
            if (size == 17) {
                for (int i = 0; i < 16; i++) {
                    byte[] b = ((ByteString) arr.getDataItems().get(i)).getBytes();
                    if (b.length == 32) refs.add(b);
                }
            } else if (size == 2) {
                // Short node (extension or leaf). Index 1 is either a child hash (extension) or value (leaf).
                byte[] b = ((ByteString) arr.getDataItems().get(1)).getBytes();
                if (b.length == 32) refs.add(b);
            }
            return refs;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

