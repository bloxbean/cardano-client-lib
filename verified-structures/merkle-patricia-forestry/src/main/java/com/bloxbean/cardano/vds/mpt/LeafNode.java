package com.bloxbean.cardano.vds.mpt;

import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.NibblePath;
import com.bloxbean.cardano.vds.core.nibbles.Nibbles;
import com.bloxbean.cardano.vds.mpt.commitment.CommitmentScheme;

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
 *   <li>Key (optional): the original unhashed key</li>
 *   <li>CBOR encoding: 2 or 3-element array [HP-encoded key suffix, value, key?]</li>
 * </ul>
 *
 * <p><b>Key Storage:</b></p>
 * <p>The optional key field stores the original (unhashed) key. This field is NOT used
 * in commitment calculations, ensuring that the trie's root hash remains identical
 * whether keys are stored or not. This maintains full compatibility with Aiken
 * on-chain verification.</p>
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
 * @see com.bloxbean.cardano.vds.core.nibbles.Nibbles
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
     * Optional original (unhashed) key stored for debugging and introspection.
     * This field is NOT used in commitment calculations.
     * May be null if key storage was not enabled when this leaf was created.
     */
    private final byte[] key;

    /**
     * Private constructor for builder pattern and factory methods.
     * Use {@link #builder()} or {@link #of(byte[], byte[])} to create instances.
     *
     * @param hp    the HP-encoded key suffix (defensive copy made)
     * @param value the value data (defensive copy made)
     * @param key   the original (unhashed) key, or null (defensive copy made)
     */
    private LeafNode(byte[] hp, byte[] value, byte[] key) {
        this.hp = hp != null ? hp.clone() : new byte[0];
        this.value = value != null ? value.clone() : new byte[0];
        this.key = key != null ? key.clone() : null;
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
     * Gets the original (unhashed) key if stored.
     *
     * <p>This field is only populated when key storage is enabled.
     * It is NOT used in commitment calculations - the trie's root hash is
     * identical whether this field is present or not.</p>
     *
     * @return defensive copy of the key, or null if not stored
     */
    public byte[] getKey() {
        return key != null ? key.clone() : null;
    }

    /**
     * Factory method to create a leaf node without original key.
     *
     * @param hp    the HP-encoded key suffix
     * @param value the value data
     * @return new immutable LeafNode instance
     */
    public static LeafNode of(byte[] hp, byte[] value) {
        return new LeafNode(hp, value, null);
    }

    /**
     * Factory method to create a leaf node with original key.
     *
     * <p>The key is stored for debugging/introspection purposes but is
     * NOT used in commitment calculations. The trie's root hash is identical
     * whether keys are stored or not.</p>
     *
     * @param hp    the HP-encoded key suffix
     * @param value the value data
     * @param key   the original (unhashed) key, or null
     * @return new immutable LeafNode instance
     */
    public static LeafNode of(byte[] hp, byte[] value, byte[] key) {
        return new LeafNode(hp, value, key);
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
     * Creates a new LeafNode with updated value, keeping the same HP path and key.
     *
     * @param newValue the new value data
     * @return new LeafNode instance with updated value
     */
    public LeafNode withValue(byte[] newValue) {
        return new LeafNode(this.hp, newValue, this.key);
    }

    /**
     * Creates a new LeafNode with updated HP path, keeping the same value and key.
     *
     * @param newHp the new HP-encoded key suffix
     * @return new LeafNode instance with updated HP path
     */
    public LeafNode withHp(byte[] newHp) {
        return new LeafNode(newHp, this.value, this.key);
    }

    /**
     * Creates a new LeafNode with updated key, keeping the same HP and value.
     *
     * @param newKey the new key, or null to remove
     * @return new LeafNode instance with updated key
     */
    public LeafNode withKey(byte[] newKey) {
        return new LeafNode(this.hp, this.value, newKey);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    byte[] commit(HashFunction hashFn, CommitmentScheme commitments) {
        Nibbles.HP hpInfo = Nibbles.unpackHP(hp);
        NibblePath suffix = NibblePath.of(hpInfo.nibbles);
        byte[] valueHash = hashFn.digest(value);
        return commitments.commitLeaf(suffix, valueHash);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Encodes as a CBOR array:
     * <ul>
     *   <li>2-element array: [HP-encoded key suffix, value] - when no key</li>
     *   <li>3-element array: [HP-encoded key suffix, value, key] - when key present</li>
     * </ul>
     * The HP encoding indicates this is a leaf (not extension) node.
     * The optional 3rd element (key) is NOT used in commitment calculation.</p>
     */
    @Override
    byte[] encode() {
        try {
            Array cborArray = new Array();
            cborArray.add(new ByteString(hp));
            cborArray.add(new ByteString(value));
            if (key != null) {
                cborArray.add(new ByteString(key));
            }

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
     * <p>Supports both formats for backward compatibility:
     * <ul>
     *   <li>2-element array: [HP-encoded key suffix, value]</li>
     *   <li>3-element array: [HP-encoded key suffix, value, key]</li>
     * </ul>
     *
     * @param cborArray the 2 or 3-element CBOR array to decode
     * @return the decoded LeafNode instance
     * @throws IllegalArgumentException if the array doesn't have 2 or 3 elements
     */
    static LeafNode decode(Array cborArray) {
        int size = cborArray.getDataItems().size();
        if (size < 2 || size > 3) {
            throw new IllegalArgumentException("Leaf node must have 2 or 3 items, got " + size);
        }

        byte[] hp = ((ByteString) cborArray.getDataItems().get(0)).getBytes();
        byte[] value = ((ByteString) cborArray.getDataItems().get(1)).getBytes();
        byte[] key = null;

        if (size == 3) {
            key = ((ByteString) cborArray.getDataItems().get(2)).getBytes();
        }

        return new LeafNode(hp, value, key);
    }

    /**
     * Builder for constructing LeafNode instances with fluent API.
     */
    public static final class Builder {
        private byte[] hp;
        private byte[] value;
        private byte[] key;

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
         * Sets the original (unhashed) key.
         *
         * <p>This field is NOT used in commitment calculations - the trie's root hash
         * is identical whether keys are stored or not.</p>
         *
         * @param key the original (unhashed) key, or null
         * @return this builder for method chaining
         */
        public Builder key(byte[] key) {
            this.key = key;
            return this;
        }

        /**
         * Builds the immutable LeafNode instance.
         *
         * @return new LeafNode with configured values
         */
        public LeafNode build() {
            return new LeafNode(hp, value, key);
        }
    }
}
