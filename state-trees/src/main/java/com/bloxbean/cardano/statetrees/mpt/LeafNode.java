package com.bloxbean.cardano.statetrees.mpt;

import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;

import java.io.ByteArrayOutputStream;

/**
 * Leaf node in the Merkle Patricia Trie storing key-value pairs.
 *
 * <p>Leaf nodes represent the terminal points of trie paths where actual data values
 * are stored. They contain the remaining portion of a key (after following the path
 * through the trie) and the associated value.</p>
 *
 * <p><b>Structure:</b></p>
 * <ul>
 *   <li>HP-encoded key suffix: remaining nibbles with isLeaf=true flag</li>
 *   <li>Value: the actual data being stored</li>
 *   <li>CBOR encoding: 2-element array [HP-encoded key suffix, value]</li>
 * </ul>
 *
 * <p><b>Key Path Example:</b></p>
 * <pre>
 * Full key: "hello" (hex: 68656c6c6f, nibbles: [6,8,6,5,6,c,6,c,6,f])
 * Path in trie: Root -> Branch[6] -> Extension([8,6,5]) -> Leaf([6,c,6,c,6,f], "world")
 * </pre>
 *
 * @see com.bloxbean.cardano.statetrees.common.nibbles.Nibbles
 */
final class LeafNode extends Node {
  /**
   * Hex-Prefix encoded key suffix with isLeaf=true flag.
   * Contains the remaining nibbles of the key after traversing the trie path.
   */
  byte[] hp;

  /**
   * The actual value being stored for this key.
   * Can be any byte array representing the application data.
   */
  byte[] value;

  /**
   * {@inheritDoc}
   */
  @Override
  byte[] hash() {
    return Blake2b256.digest(encode());
  }

  /**
   * {@inheritDoc}
   *
   * <p>Encodes as a 2-element CBOR array: [HP-encoded key suffix, value].
   * The HP encoding indicates this is a leaf (not extension) node.</p>
   */
  @Override
  byte[] encode() {
    try {
      Array cborArray = new Array();
      cborArray.add(new ByteString(hp == null ? new byte[0] : hp));
      cborArray.add(new ByteString(value == null ? new byte[0] : value));

      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      new CborEncoder(outputStream).encode(cborArray);
      return outputStream.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException("Failed to encode LeafNode", e);
    }
  }

  /**
   * Decodes a CBOR array into a LeafNode.
   *
   * @param cborArray the 2-element CBOR array to decode
   * @return the decoded LeafNode instance
   * @throws IllegalArgumentException if the array doesn't have exactly 2 elements
   */
  static LeafNode decode(Array cborArray) {
    if (cborArray.getDataItems().size() != 2) {
      throw new IllegalArgumentException("Leaf node must have exactly 2 items, got " +
        cborArray.getDataItems().size());
    }

    LeafNode leafNode = new LeafNode();
    leafNode.hp = ((ByteString) cborArray.getDataItems().get(0)).getBytes();

    byte[] valueData = ((ByteString) cborArray.getDataItems().get(1)).getBytes();
    leafNode.value = valueData;

    return leafNode;
  }
}
