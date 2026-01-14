package com.bloxbean.cardano.vds.mpt;

import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.NibblePath;
import com.bloxbean.cardano.vds.mpt.commitment.CommitmentScheme;

import java.io.ByteArrayOutputStream;

/**
 * Immutable branch node in the Merkle Patricia Trie providing 16-way branching.
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
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * // Create with builder
 * BranchNode node = BranchNode.builder()
 *   .child(5, childHash)
 *   .child(10, anotherChildHash)
 *   .value(valueBytes)
 *   .build();
 *
 * // Create with factory method
 * BranchNode node = BranchNode.withValue(valueBytes);
 *
 * // Create modified copy
 * BranchNode updated = node.withChild(3, newChildHash);
 * </pre>
 */
final class BranchNode extends Node {
    /**
     * Array of 16 child node hashes, one for each hexadecimal nibble (0-F).
     * Null entries indicate no child for that nibble value.
     */
    private final byte[][] children = new byte[16][];

    /**
     * Optional value stored at this branch node.
     * Non-null when a key terminates at this exact position in the trie.
     */
    private final byte[] value;

    /**
     * Private constructor for builder pattern and factory methods.
     * Use {@link #builder()} or factory methods to create instances.
     *
     * @param children array of 16 child hashes (defensive copy made)
     * @param value    the value data (defensive copy made)
     */
    private BranchNode(byte[][] children, byte[] value) {
        // Deep copy the children array
        if (children != null) {
            for (int i = 0; i < 16; i++) {
                this.children[i] = children[i] != null ? children[i].clone() : null;
            }
        }
        this.value = value != null ? value.clone() : null;
    }

    /**
     * Gets a child hash at the specified index.
     *
     * @param index the child index (0-15)
     * @return defensive copy of the child hash, or null if no child at index
     * @throws IndexOutOfBoundsException if index is not in range 0-15
     */
    public byte[] getChild(int index) {
        if (index < 0 || index >= 16) {
            throw new IndexOutOfBoundsException("Child index must be 0-15, got " + index);
        }
        return children[index] != null ? children[index].clone() : null;
    }

    /**
     * Gets all children as a defensive copy.
     *
     * @return defensive deep copy of the children array
     */
    public byte[][] getChildren() {
        byte[][] copy = new byte[16][];
        for (int i = 0; i < 16; i++) {
            copy[i] = children[i] != null ? children[i].clone() : null;
        }
        return copy;
    }

    /**
     * Gets the value stored in this branch node.
     *
     * @return defensive copy of the value data, or null if no value
     */
    public byte[] getValue() {
        return value != null ? value.clone() : null;
    }

    /**
     * Factory method to create an empty branch node.
     *
     * @return new immutable BranchNode with no children or value
     */
    public static BranchNode empty() {
        return new BranchNode(null, null);
    }

    /**
     * Factory method to create a branch node with only a value.
     *
     * @param value the value data
     * @return new immutable BranchNode with the specified value
     */
    public static BranchNode ofValue(byte[] value) {
        return new BranchNode(null, value);
    }

    /**
     * Creates a builder for constructing BranchNode instances.
     *
     * @return new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a new BranchNode with an updated child at the specified index.
     *
     * @param index     the child index (0-15)
     * @param childHash the new child hash (null to remove)
     * @return new BranchNode instance with updated child
     * @throws IndexOutOfBoundsException if index is not in range 0-15
     */
    public BranchNode withChild(int index, byte[] childHash) {
        if (index < 0 || index >= 16) {
            throw new IndexOutOfBoundsException("Child index must be 0-15, got " + index);
        }
        byte[][] newChildren = getChildren();
        newChildren[index] = childHash != null ? childHash.clone() : null;
        return new BranchNode(newChildren, this.value);
    }

    /**
     * Creates a new BranchNode with updated value, keeping the same children.
     *
     * @param newValue the new value data (null to remove)
     * @return new BranchNode instance with updated value
     */
    public BranchNode withValue(byte[] newValue) {
        return new BranchNode(this.children, newValue);
    }

    @Override
    byte[] commit(HashFunction hashFn, CommitmentScheme commitments) {
        byte[][] childHashes = new byte[16][];
        for (int i = 0; i < 16; i++) {
            byte[] child = children[i];
            childHashes[i] = child == null || child.length == 0 ? null : child;
        }

        byte[] valueHash = value == null ? null : hashFn.digest(value);

        return commitments.commitBranch(NibblePath.EMPTY, childHashes, valueHash);
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
     * {@inheritDoc}
     */
    @Override
    public <T> T accept(NodeVisitor<T> visitor) {
        return visitor.visitBranch(this);
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

        byte[][] children = new byte[16][];

        // Decode 16 child hashes
        for (int i = 0; i < 16; i++) {
            ByteString childBytes = (ByteString) cborArray.getDataItems().get(i);
            byte[] childData = childBytes.getBytes();
            children[i] = childData.length == 0 ? null : childData;
        }

        // Decode value
        ByteString valueBytes = (ByteString) cborArray.getDataItems().get(16);
        byte[] valueData = valueBytes.getBytes();
        byte[] value = valueData.length == 0 ? null : valueData;

        return new BranchNode(children, value);
    }

    /**
     * Counts the number of non-null children in this branch node.
     *
     * <p>Used during deletion operations to determine if the branch node
     * should be compressed or eliminated.</p>
     *
     * @return the count of non-null child pointers
     */
    public int childCountNonNull() {
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
    public int firstChildIndex() {
        for (int i = 0; i < 16; i++) {
            if (children[i] != null) return i;
        }
        return -1;
    }

    /**
     * Builder for constructing BranchNode instances with fluent API.
     */
    public static final class Builder {
        private final byte[][] children = new byte[16][];
        private byte[] value;

        /**
         * Private constructor - use {@link BranchNode#builder()} to create instances.
         */
        private Builder() {
        }

        /**
         * Sets a child hash at the specified index.
         *
         * @param index     the child index (0-15)
         * @param childHash the child hash (defensive copy made)
         * @return this builder for method chaining
         * @throws IndexOutOfBoundsException if index is not in range 0-15
         */
        public Builder child(int index, byte[] childHash) {
            if (index < 0 || index >= 16) {
                throw new IndexOutOfBoundsException("Child index must be 0-15, got " + index);
            }
            this.children[index] = childHash;
            return this;
        }

        /**
         * Sets multiple children from an array.
         *
         * @param children array of 16 child hashes (defensive copy made)
         * @return this builder for method chaining
         * @throws IllegalArgumentException if array doesn't have exactly 16 elements
         */
        public Builder children(byte[][] children) {
            if (children != null && children.length != 16) {
                throw new IllegalArgumentException("Children array must have exactly 16 elements, got " + children.length);
            }
            if (children != null) {
                for (int i = 0; i < 16; i++) {
                    this.children[i] = children[i];
                }
            }
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
         * Builds the immutable BranchNode instance.
         *
         * @return new BranchNode with configured values
         */
        public BranchNode build() {
            return new BranchNode(children, value);
        }
    }
}
