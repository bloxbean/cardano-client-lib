package com.bloxbean.cardano.statetrees.mpt;

import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.statetrees.api.HashFunction;
import com.bloxbean.cardano.statetrees.common.NibblePath;
import com.bloxbean.cardano.statetrees.common.nibbles.Nibbles;
import com.bloxbean.cardano.statetrees.mpt.commitment.CommitmentScheme;

import java.io.ByteArrayOutputStream;

/**
 * Immutable extension node in the Merkle Patricia Trie for path compression.
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
 * <p><b>Usage:</b></p>
 * <pre>
 * // Create with builder
 * ExtensionNode node = ExtensionNode.builder()
 *   .hp(hpEncodedPath)
 *   .child(childHash)
 *   .build();
 *
 * // Create with factory method
 * ExtensionNode node = ExtensionNode.of(hpEncodedPath, childHash);
 *
 * // Create modified copy
 * ExtensionNode updated = node.withChild(newChildHash);
 * </pre>
 *
 * @see com.bloxbean.cardano.statetrees.common.nibbles.Nibbles
 */
final class ExtensionNode extends Node {
    /**
     * Hex-Prefix encoded path with isLeaf=false flag.
     * Contains the compressed nibble sequence that this extension represents.
     */
    private final byte[] hp;

    /**
     * Hash of the single child node that this extension points to.
     * Can be a BranchNode, LeafNode, or another ExtensionNode.
     */
    private final byte[] child;

    /**
     * Private constructor for builder pattern and factory methods.
     * Use {@link #builder()} or {@link #of(byte[], byte[])} to create instances.
     *
     * @param hp    the HP-encoded path (defensive copy made)
     * @param child the child hash (defensive copy made)
     */
    private ExtensionNode(byte[] hp, byte[] child) {
        this.hp = hp != null ? hp.clone() : new byte[0];
        this.child = child != null ? child.clone() : new byte[0];
    }

    /**
     * Gets the HP-encoded path.
     *
     * @return defensive copy of the HP-encoded path
     */
    public byte[] getHp() {
        return hp.clone();
    }

    /**
     * Gets the child hash.
     *
     * @return defensive copy of the child hash
     */
    public byte[] getChild() {
        return child.clone();
    }

    /**
     * Factory method to create an extension node.
     *
     * @param hp    the HP-encoded path
     * @param child the child hash
     * @return new immutable ExtensionNode instance
     */
    public static ExtensionNode of(byte[] hp, byte[] child) {
        return new ExtensionNode(hp, child);
    }

    /**
     * Creates a builder for constructing ExtensionNode instances.
     *
     * @return new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new ExtensionNode with updated HP path, keeping the same child.
     *
     * @param newHp the new HP-encoded path
     * @return new ExtensionNode instance with updated HP path
     */
    public ExtensionNode withHp(byte[] newHp) {
        return new ExtensionNode(newHp, this.child);
    }

    /**
     * Creates a new ExtensionNode with updated child, keeping the same HP path.
     *
     * @param newChild the new child hash
     * @return new ExtensionNode instance with updated child
     */
    public ExtensionNode withChild(byte[] newChild) {
        return new ExtensionNode(this.hp, newChild);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    byte[] commit(HashFunction hashFn, CommitmentScheme commitments) {
        Nibbles.HP hpInfo = Nibbles.unpackHP(hp);
        NibblePath path = NibblePath.of(hpInfo.nibbles);
        byte[] childHash = child == null || child.length == 0 ? commitments.nullHash() : child;
        return commitments.commitExtension(path, childHash);
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
            cborArray.add(new ByteString(hp));
            cborArray.add(new ByteString(child));

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            new CborEncoder(outputStream).encode(cborArray);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode ExtensionNode", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T accept(NodeVisitor<T> visitor) {
        return visitor.visitExtension(this);
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

        byte[] hp = ((ByteString) cborArray.getDataItems().get(0)).getBytes();
        byte[] childData = ((ByteString) cborArray.getDataItems().get(1)).getBytes();
        byte[] child = childData.length == 0 ? null : childData;

        return new ExtensionNode(hp, child);
    }

    /**
     * Builder for constructing ExtensionNode instances with fluent API.
     */
    public static final class Builder {
        private byte[] hp;
        private byte[] child;

        /**
         * Private constructor - use {@link ExtensionNode#builder()} to create instances.
         */
        private Builder() {
        }

        /**
         * Sets the HP-encoded path.
         *
         * @param hp the HP-encoded path (defensive copy made)
         * @return this builder for method chaining
         */
        public Builder hp(byte[] hp) {
            this.hp = hp;
            return this;
        }

        /**
         * Sets the child hash.
         *
         * @param child the child hash (defensive copy made)
         * @return this builder for method chaining
         */
        public Builder child(byte[] child) {
            this.child = child;
            return this;
        }

        /**
         * Builds the immutable ExtensionNode instance.
         *
         * @return new ExtensionNode with configured values
         */
        public ExtensionNode build() {
            return new ExtensionNode(hp, child);
        }
    }
}
