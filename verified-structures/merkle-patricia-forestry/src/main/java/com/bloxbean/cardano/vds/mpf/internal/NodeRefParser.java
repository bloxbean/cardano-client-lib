package com.bloxbean.cardano.vds.mpf.internal;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parses encoded MPT nodes to extract child hashes without needing internal types.
 *
 * <p>This utility is used by GC strategies (both RocksDB and RDBMS) to traverse
 * the trie graph and identify reachable nodes.</p>
 *
 * @since 0.8.0
 */
public final class NodeRefParser {

    private NodeRefParser() {}

    /**
     * Extracts child node hashes from a CBOR-encoded MPT node.
     *
     * <p>Node types:
     * <ul>
     *   <li>Branch node (17 items): first 16 are child hashes (32 bytes each)</li>
     *   <li>Short node (2 items): index 1 is either a child hash (extension) or value (leaf)</li>
     * </ul>
     *
     * @param enc the CBOR-encoded node bytes
     * @return list of child hashes (32-byte arrays), empty if leaf or no children
     */
    public static List<byte[]> childRefs(byte[] enc) {
        try {
            List<DataItem> items = new CborDecoder(new ByteArrayInputStream(enc)).decode();
            if (items.isEmpty()) return Collections.emptyList();
            DataItem di = items.get(0);
            if (!(di instanceof Array)) return Collections.emptyList();
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
