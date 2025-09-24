package com.bloxbean.cardano.statetrees.smt;

import com.bloxbean.cardano.statetrees.api.NodeStore;
import com.bloxbean.cardano.statetrees.common.NodeHash;

import java.util.Objects;

/**
 * High-level abstraction for SMT node persistence operations.
 *
 * <p>This class provides a clean interface for storing and retrieving SMT nodes,
 * abstracting away the low-level details of hash computation, encoding, and storage.
 * It serves as a bridge between the high-level SMT operations and the underlying
 * storage layer, mirroring the functionality of MPT's NodePersistence.</p>
 *
 * <p><b>Key Responsibilities:</b></p>
 * <ul>
 *   <li>SMT node encoding and hash computation</li>
 *   <li>Storage operations with type safety</li>
 *   <li>SMT node decoding and validation</li>
 *   <li>Error handling and logging</li>
 * </ul>
 *
 * <p><b>Benefits:</b></p>
 * <ul>
 *   <li>Type safety with NodeHash wrapper</li>
 *   <li>Centralized SMT persistence logic</li>
 *   <li>Easier testing and mocking</li>
 *   <li>Better error handling</li>
 * </ul>
 *
 * @since 0.8.0
 */
final class SmtPersistence {
    private final NodeStore store;

    /**
     * Creates a new SmtPersistence instance.
     *
     * @param store the underlying node storage implementation
     * @throws NullPointerException if store is null
     */
    SmtPersistence(NodeStore store) {
        this.store = Objects.requireNonNull(store, "NodeStore cannot be null");
    }

    /**
     * Persists an SMT node to storage and returns its hash.
     *
     * <p>This method performs the following operations:</p>
     * <ol>
     *   <li>Encodes the node to CBOR format</li>
     *   <li>Computes the Blake2b-256 hash of the encoded data</li>
     *   <li>Stores the encoded data using the hash as key</li>
     *   <li>Returns a type-safe NodeHash wrapper</li>
     * </ol>
     *
     * @param node the SMT node to persist, must not be null
     * @return the hash of the persisted node
     * @throws NullPointerException if node is null
     * @throws RuntimeException     if encoding or storage fails
     */
    NodeHash persist(SmtNode node) {
        Objects.requireNonNull(node, "node");
        byte[] encoded = node.encode();
        byte[] hash = node.hash();
        store.put(hash, encoded);
        return NodeHash.of(hash);
    }

    /**
     * Loads an SMT node from storage by its hash.
     *
     * <p>This method retrieves the encoded node data from storage and
     * decodes it back to an SmtNode instance. Returns null if the node
     * is not found in storage.</p>
     *
     * @param hash the hash of the node to load, must not be null
     * @return the decoded SMT node instance, or null if not found
     * @throws NullPointerException if hash is null
     * @throws RuntimeException     if decoding fails
     */
    SmtNode load(NodeHash hash) {
        Objects.requireNonNull(hash, "hash");
        byte[] enc = store.get(hash.getBytes());
        if (enc == null) return null;
        return SmtEncoding.decode(enc);
    }
}
