package com.bloxbean.cardano.statetrees.mpt;

import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;

import java.io.ByteArrayOutputStream;

/**
 * Extension node in the Merkle Patricia Trie for path compression.
 *
 * <p>Extension nodes optimize trie storage by compressing long paths with no branching
 * into a single node. Instead of having multiple nodes each storing one nibble, an
 * extension node stores a sequence of nibbles leading to a single child node.</p>
 *
 * <p><b>Structure:</b></p>
 * <ul>
 *   <li>HP-encoded path: compressed nibble sequence (with isLeaf=false)</li>
 *   <li>Child pointer: hash of the single child node</li>
 *   <li>CBOR encoding: 2-element array [HP-encoded path, child hash]</li>
 * </ul>
 *
 * <p><b>Path Compression Example:</b></p>
 * <pre>
 * Without extension: Root -> [a] -> [b] -> [c] -> [d] -> Leaf("value")
 * With extension:    Root -> Extension([a,b,c,d]) -> Leaf("value")
 * </pre>
 *
 * @see com.bloxbean.cardano.statetrees.common.nibbles.Nibbles
 */
final class ExtensionNode extends Node {
  /**
   * Hex-Prefix encoded path with isLeaf=false flag.
   * Contains the compressed nibble sequence that this extension represents.
   */
  byte[] hp;

  /**
   * Hash of the single child node that this extension points to.
   * Can be a BranchNode, LeafNode, or another ExtensionNode.
   */
  byte[] child;

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
   * <p>Encodes as a 2-element CBOR array: [HP-encoded path, child hash].
   * The HP encoding indicates this is an extension (not leaf) node.</p>
   */
  @Override
  byte[] encode() {
    try {
      Array cborArray = new Array();
      cborArray.add(new ByteString(hp == null ? new byte[0] : hp));
      cborArray.add(new ByteString(child == null ? new byte[0] : child));

      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      new CborEncoder(outputStream).encode(cborArray);
      return outputStream.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException("Failed to encode ExtensionNode", e);
    }
  }

  /**
   * Decodes a CBOR array into an ExtensionNode.
   *
   * @param cborArray the 2-element CBOR array to decode
   * @return the decoded ExtensionNode instance
   * @throws IllegalArgumentException if the array doesn't have exactly 2 elements
   */
  static ExtensionNode decode(Array cborArray) {
    if (cborArray.getDataItems().size() != 2) {
      throw new IllegalArgumentException("Extension node must have exactly 2 items, got " +
        cborArray.getDataItems().size());
    }

    ExtensionNode extensionNode = new ExtensionNode();
    extensionNode.hp = ((ByteString) cborArray.getDataItems().get(0)).getBytes();

    byte[] childData = ((ByteString) cborArray.getDataItems().get(1)).getBytes();
    extensionNode.child = childData.length == 0 ? null : childData;

    return extensionNode;
  }
}
