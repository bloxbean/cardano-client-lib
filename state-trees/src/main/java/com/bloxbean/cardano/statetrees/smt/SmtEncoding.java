package com.bloxbean.cardano.statetrees.smt;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;

import java.io.ByteArrayInputStream;
import java.util.List;

/**
 * CBOR encoding and decoding utilities for Sparse Merkle Tree nodes.
 *
 * <p>This class handles the serialization and deserialization of SMT nodes using
 * CBOR (Concise Binary Object Representation) format. The encoding scheme uses
 * type tags to distinguish between different node types:</p>
 *
 * <p><b>Node Encoding Formats:</b></p>
 * <ul>
 *   <li><b>Internal Node:</b> 3-element array [tag=0, leftHash, rightHash]</li>
 *   <li><b>Leaf Node:</b> 3-element array [tag=1, keyHash, value]</li>
 * </ul>
 *
 * <p><b>Node Type Detection:</b> The decoder uses the tag value in the first
 * array element to determine the correct node type:</p>
 * <ul>
 *   <li>tag=0 → SmtInternalNode</li>
 *   <li>tag=1 → SmtLeafNode</li>
 * </ul>
 *
 * <p><b>CBOR Benefits:</b></p>
 * <ul>
 *   <li>Deterministic encoding ensures consistent hashes</li>
 *   <li>Compact binary representation reduces storage overhead</li>
 *   <li>Self-describing format enables robust parsing</li>
 *   <li>Wide language support for interoperability</li>
 * </ul>
 *
 * @since 0.8.0
 */
final class SmtEncoding {
    
    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private SmtEncoding() { 
        throw new AssertionError("Utility class - do not instantiate"); 
    }

    /**
     * Decodes CBOR-encoded bytes into the appropriate SmtNode type.
     *
     * <p>This method analyzes the CBOR structure to determine the correct node type
     * based on the tag value in the first array element.</p>
     *
     * @param encodedBytes the CBOR-encoded node data
     * @return the decoded SmtNode instance (SmtInternalNode or SmtLeafNode)
     * @throws IllegalArgumentException if encodedBytes is null, empty, or has invalid format
     * @throws RuntimeException if CBOR decoding fails
     */
    static SmtNode decode(byte[] encodedBytes) {
    if (encodedBytes == null) throw new IllegalArgumentException("Cannot decode null bytes");
    try {
      List<DataItem> items = new CborDecoder(new ByteArrayInputStream(encodedBytes)).decode();
      if (items.isEmpty()) throw new IllegalArgumentException("Empty CBOR data");
      DataItem di = items.get(0);
      if (!(di instanceof Array)) throw new IllegalArgumentException("Expected CBOR array");
      Array arr = (Array) di;
      if (arr.getDataItems().isEmpty()) throw new IllegalArgumentException("Empty node array");
      byte[] tag = ((ByteString) arr.getDataItems().get(0)).getBytes();
      if (tag.length != 1) throw new IllegalArgumentException("Invalid node tag length: " + tag.length);
      if (tag[0] == 0) {
        // internal: [0, left, right]
        if (arr.getDataItems().size() != 3) throw new IllegalArgumentException("Invalid internal node");
        return SmtInternalNode.decode(arr);
      } else if (tag[0] == 1) {
        // leaf: [1, keyHash, valueHash]
        if (arr.getDataItems().size() != 3) throw new IllegalArgumentException("Invalid leaf node");
        return SmtLeafNode.decode(arr);
      } else {
        throw new IllegalArgumentException("Unknown node tag: " + tag[0]);
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to decode SMT node", e);
    }
  }
}
