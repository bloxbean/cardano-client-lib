package com.bloxbean.cardano.statetrees.mpt;

import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;

import java.io.ByteArrayOutputStream;

/**
 * Branch node in the Merkle Patricia Trie providing 16-way branching.
 *
 * <p>Branch nodes enable the hexary (base-16) structure of the trie by providing
 * 16 slots for child pointers, one for each possible nibble value (0-F). This
 * design allows efficient traversal based on the hexadecimal representation of keys.</p>
 *
 * <p><b>Structure:</b></p>
 * <ul>
 *   <li>16 child pointers (each can be null or point to another node)</li>
 *   <li>Optional value field (for keys that end at this branch)</li>
 *   <li>CBOR encoding: 17-element array [child0...child15, value]</li>
 * </ul>
 *
 * <p><b>Optimization:</b> Branch nodes with only one child are automatically
 * compressed into extension nodes during deletion operations to maintain
 * space efficiency.</p>
 */
final class BranchNode extends Node {
  /**
   * Array of 16 child node hashes, one for each hexadecimal nibble (0-F).
   * Null entries indicate no child for that nibble value.
   */
  final byte[][] children = new byte[16][];

  /**
   * Optional value stored at this branch node.
   * Non-null when a key terminates at this exact position in the trie.
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
   * <p>Encodes as a 17-element CBOR array: [child0, child1, ..., child15, value].
   * Empty byte arrays are used for null children and null values.</p>
   */
  @Override
  byte[] encode() {
    try {
      Array cborArray = new Array();
      // Add 16 child hashes (empty bytes for null children)
      for (int i = 0; i < 16; i++) {
        cborArray.add(new ByteString(children[i] == null ? new byte[0] : children[i]));
      }
      // Add value (empty bytes for null value)
      cborArray.add(new ByteString(value == null ? new byte[0] : value));

      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      new CborEncoder(outputStream).encode(cborArray);
      return outputStream.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException("Failed to encode BranchNode", e);
    }
  }

  /**
   * Decodes a CBOR array into a BranchNode.
   *
   * @param cborArray the 17-element CBOR array to decode
   * @return the decoded BranchNode instance
   * @throws IllegalArgumentException if the array doesn't have exactly 17 elements
   */
  static BranchNode decode(Array cborArray) {
    if (cborArray.getDataItems().size() != 17) {
      throw new IllegalArgumentException("Branch node must have exactly 17 items, got " +
        cborArray.getDataItems().size());
    }

    BranchNode branchNode = new BranchNode();

    // Decode 16 child hashes
    for (int i = 0; i < 16; i++) {
      ByteString childBytes = (ByteString) cborArray.getDataItems().get(i);
      byte[] childData = childBytes.getBytes();
      branchNode.children[i] = childData.length == 0 ? null : childData;
    }

    // Decode value
    ByteString valueBytes = (ByteString) cborArray.getDataItems().get(16);
    byte[] valueData = valueBytes.getBytes();
    branchNode.value = valueData.length == 0 ? null : valueData;

    return branchNode;
  }

  /**
   * Counts the number of non-null children in this branch node.
   *
   * <p>Used during deletion operations to determine if the branch node
   * should be compressed or eliminated.</p>
   *
   * @return the count of non-null child pointers
   */
  int childCountNonNull() {
    int count = 0;
    for (int i = 0; i < 16; i++) {
      if (children[i] != null) count++;
    }
    return count;
  }

  /**
   * Finds the index of the first non-null child.
   *
   * <p>Used during compression operations when a branch node has only
   * one child and needs to be converted to an extension node.</p>
   *
   * @return the index (0-15) of the first non-null child, or -1 if all children are null
   */
  int firstChildIndex() {
    for (int i = 0; i < 16; i++) {
      if (children[i] != null) return i;
    }
    return -1;
  }
}
