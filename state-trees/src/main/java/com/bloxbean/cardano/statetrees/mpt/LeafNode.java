package com.bloxbean.cardano.statetrees.mpt;

import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;

import java.io.ByteArrayOutputStream;

/**
 * Immutable leaf node in the Merkle Patricia Trie storing key-value pairs.
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
 * <p><b>Usage:</b></p>
 * <pre>
 * // Create with builder
 * LeafNode node = LeafNode.builder()
 *   .hp(hpEncodedPath)
 *   .value(valueBytes)
 *   .build();
 *
 * // Create with factory method
 * LeafNode node = LeafNode.of(hpEncodedPath, valueBytes);
 *
 * // Create modified copy
 * LeafNode updated = node.withValue(newValueBytes);
 * </pre>
 *
 * @see com.bloxbean.cardano.statetrees.common.nibbles.Nibbles
 */
final class LeafNode extends Node {
  /**
   * Hex-Prefix encoded key suffix with isLeaf=true flag.
   * Contains the remaining nibbles of the key after traversing the trie path.
   */
  private final byte[] hp;

  /**
   * The actual value being stored for this key.
   * Can be any byte array representing the application data.
   */
  private final byte[] value;

  /**
   * Private constructor for builder pattern and factory methods.
   * Use {@link #builder()} or {@link #of(byte[], byte[])} to create instances.
   *
   * @param hp the HP-encoded key suffix (defensive copy made)
   * @param value the value data (defensive copy made)
   */
  private LeafNode(byte[] hp, byte[] value) {
    this.hp = hp != null ? hp.clone() : new byte[0];
    this.value = value != null ? value.clone() : new byte[0];
  }

  /**
   * Gets the HP-encoded key suffix.
   *
   * @return defensive copy of the HP-encoded key suffix
   */
  public byte[] getHp() {
    return hp.clone();
  }

  /**
   * Gets the value stored in this leaf node.
   *
   * @return defensive copy of the value data
   */
  public byte[] getValue() {
    return value.clone();
  }

  /**
   * Factory method to create a leaf node.
   *
   * @param hp the HP-encoded key suffix
   * @param value the value data
   * @return new immutable LeafNode instance
   */
  public static LeafNode of(byte[] hp, byte[] value) {
    return new LeafNode(hp, value);
  }

  /**
   * Creates a builder for constructing LeafNode instances.
   *
   * @return new Builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Creates a new LeafNode with updated value, keeping the same HP path.
   *
   * @param newValue the new value data
   * @return new LeafNode instance with updated value
   */
  public LeafNode withValue(byte[] newValue) {
    return new LeafNode(this.hp, newValue);
  }

  /**
   * Creates a new LeafNode with updated HP path, keeping the same value.
   *
   * @param newHp the new HP-encoded key suffix
   * @return new LeafNode instance with updated HP path
   */
  public LeafNode withHp(byte[] newHp) {
    return new LeafNode(newHp, this.value);
  }

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
      cborArray.add(new ByteString(hp));
      cborArray.add(new ByteString(value));

      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      new CborEncoder(outputStream).encode(cborArray);
      return outputStream.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException("Failed to encode LeafNode", e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> T accept(NodeVisitor<T> visitor) {
    return visitor.visitLeaf(this);
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

    byte[] hp = ((ByteString) cborArray.getDataItems().get(0)).getBytes();
    byte[] value = ((ByteString) cborArray.getDataItems().get(1)).getBytes();

    return new LeafNode(hp, value);
  }

  /**
   * Builder for constructing LeafNode instances with fluent API.
   */
  public static final class Builder {
    private byte[] hp;
    private byte[] value;

    /**
     * Private constructor - use {@link LeafNode#builder()} to create instances.
     */
    private Builder() {
    }

    /**
     * Sets the HP-encoded key suffix.
     *
     * @param hp the HP-encoded key suffix (defensive copy made)
     * @return this builder for method chaining
     */
    public Builder hp(byte[] hp) {
      this.hp = hp;
      return this;
    }

    /**
     * Sets the value data.
     *
     * @param value the value data (defensive copy made)
     * @return this builder for method chaining
     */
    public Builder value(byte[] value) {
      this.value = value;
      return this;
    }

    /**
     * Builds the immutable LeafNode instance.
     *
     * @return new LeafNode with configured values
     */
    public LeafNode build() {
      return new LeafNode(hp, value);
    }
  }
}
