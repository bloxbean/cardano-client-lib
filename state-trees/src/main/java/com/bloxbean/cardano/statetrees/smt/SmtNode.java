package com.bloxbean.cardano.statetrees.smt;

/**
 * Abstract base class for all Sparse Merkle Tree node types.
 *
 * <p>The SMT uses two types of nodes:</p>
 * <ul>
 *   <li>{@link SmtLeafNode} - stores a key hash and value at the leaf level</li>
 *   <li>{@link SmtInternalNode} - provides binary branching with left/right children</li>
 * </ul>
 *
 * <p>All nodes are immutable once created and are identified by their content hash.
 * The hash serves as both the node's identity and ensures data integrity in the
 * distributed storage system.</p>
 *
 * <p>The visitor pattern is supported for type-safe operations on different node types:</p>
 * <pre>
 * T result = node.accept(new SmtNodeVisitor&lt;T&gt;() {
 *   public T visitLeaf(SmtLeafNode leaf) { ... }
 *   public T visitInternal(SmtInternalNode internal) { ... }
 * });
 * </pre>
 *
 * @since 0.8.0
 */
abstract class SmtNode {

    /**
     * Computes the cryptographic hash of this node.
     *
     * <p>The hash is computed from the CBOR-encoded node data using Blake2b-256.
     * This hash serves as the node's unique identifier and is used as the key
     * when storing the node in the persistent storage.</p>
     *
     * @return the 32-byte Blake2b-256 hash of this node
     */
    abstract byte[] hash();

    /**
     * Encodes this node to CBOR format for storage.
     *
     * <p>Each node type has a specific CBOR encoding format:</p>
     * <ul>
     *   <li>Leaf: 3-element array [tag=1, keyHash, value]</li>
     *   <li>Internal: 3-element array [tag=0, leftHash, rightHash]</li>
     * </ul>
     *
     * @return the CBOR-encoded byte representation of this node
     */
    abstract byte[] encode();

    /**
     * Accepts a visitor for type-safe operations on this node.
     *
     * <p>The visitor pattern allows performing operations on different node types
     * without casting and provides compile-time safety. Each concrete node type
     * implements this method to call the appropriate visitor method.</p>
     *
     * @param <T>     the return type of the visitor operation
     * @param visitor the visitor to accept
     * @return the result of the visitor operation
     */
    public abstract <T> T accept(SmtNodeVisitor<T> visitor);
}
