package com.bloxbean.cardano.statetrees.smt;

import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;

import java.io.ByteArrayOutputStream;

/**
 * SMT leaf node storing the 32-byte key hash and raw value bytes.
 *
 * <p>Leaf nodes represent the actual key-value pairs stored in the SMT.
 * They always appear at the leaf level of the tree and contain:</p>
 * <ul>
 *   <li>keyHash: The 32-byte Blake2b-256 hash of the original key</li>
 *   <li>value: The raw value bytes as provided by the user</li>
 * </ul>
 *
 * <p>Leaf nodes are immutable - creating a new value requires creating
 * a new leaf node instance using {@link #withValue(byte[])}.</p>
 *
 * @since 0.8.0
 */
final class SmtLeafNode extends SmtNode {
    private final byte[] keyHash;   // 32 bytes
    private final byte[] value;     // raw value bytes

    /**
     * Private constructor for creating leaf nodes.
     *
     * @param keyHash the 32-byte key hash
     * @param value the raw value bytes
     */
    private SmtLeafNode(byte[] keyHash, byte[] value) {
        this.keyHash = keyHash == null ? new byte[0] : keyHash.clone();
        this.value = value == null ? new byte[0] : value.clone();
    }

    /**
     * Creates a new SMT leaf node with the given key hash and value.
     *
     * @param keyHash the 32-byte Blake2b-256 hash of the key
     * @param value the raw value bytes to store
     * @return a new SmtLeafNode instance
     */
    public static SmtLeafNode of(byte[] keyHash, byte[] value) {
        return new SmtLeafNode(keyHash, value);
    }

    /**
     * Returns a copy of the key hash.
     *
     * @return the 32-byte key hash (defensive copy)
     */
    public byte[] getKeyHash() { 
        return keyHash.clone(); 
    }

    /**
     * Returns a copy of the value bytes.
     *
     * @return the raw value bytes (defensive copy)
     */
    public byte[] getValue() { 
        return value.clone(); 
    }

    /**
     * Creates a new leaf node with the same key hash but different value.
     *
     * @param newValue the new value bytes
     * @return a new SmtLeafNode with updated value
     */
    public SmtLeafNode withValue(byte[] newValue) {
        return new SmtLeafNode(this.keyHash, newValue);
    }

  @Override
  byte[] hash() {
    return Blake2b256.digest(encode());
  }

  @Override
  byte[] encode() {
    try {
      Array arr = new Array();
      arr.add(new ByteString(new byte[] { 1 })); // tag for leaf node
      arr.add(new ByteString(keyHash));
      arr.add(new ByteString(value));
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      new CborEncoder(baos).encode(arr);
      return baos.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException("Failed to encode SmtLeafNode", e);
    }
  }

  @Override
  public <T> T accept(SmtNodeVisitor<T> visitor) {
    return visitor.visitLeaf(this);
  }

    /**
     * Decodes a CBOR array into a SmtLeafNode.
     *
     * @param cborArray the CBOR array containing [tag=1, keyHash, value]
     * @return the decoded leaf node
     */
    static SmtLeafNode decode(Array cborArray) {
        // cborArray: [ tag=1, keyHash, value ]
        byte[] k = ((ByteString) cborArray.getDataItems().get(1)).getBytes();
        byte[] v = ((ByteString) cborArray.getDataItems().get(2)).getBytes();
        return new SmtLeafNode(k, v);
    }
}
