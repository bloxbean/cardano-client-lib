package com.bloxbean.cardano.vds.mpf;

import com.bloxbean.cardano.vds.core.api.HashFunction;
import com.bloxbean.cardano.vds.core.api.NodeStore;
import com.bloxbean.cardano.vds.core.NodeHash;
import com.bloxbean.cardano.vds.mpf.commitment.CommitmentScheme;

import java.util.Objects;

/**
 * High-level abstraction for node persistence operations.
 *
 * <p>This class provides a clean interface for storing and retrieving trie nodes,
 * abstracting away the low-level details of hash computation, encoding, and storage.
 * It serves as a bridge between the high-level trie operations and the underlying
 * storage layer.</p>
 *
 * <p><b>Key Responsibilities:</b></p>
 * <ul>
 *   <li>Node encoding and hash computation</li>
 *   <li>Storage operations with type safety</li>
 *   <li>Node decoding and validation</li>
 *   <li>Error handling and logging</li>
 * </ul>
 *
 * <p><b>Benefits:</b></p>
 * <ul>
 *   <li>Type safety with NodeHash wrapper</li>
 *   <li>Centralized persistence logic</li>
 *   <li>Easier testing and mocking</li>
 *   <li>Better error handling</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * CommitmentScheme commitments = new MpfCommitmentScheme(hashFn);
 * NodePersistence persistence = new NodePersistence(nodeStore, commitments, hashFn);
 *
 * // Persist a node
 * NodeHash hash = persistence.persist(leafNode);
 *
 * // Load a node back
 * Node loadedNode = persistence.load(hash);
 * }</pre>
 *
 * <p><b>Performance:</b> This abstraction adds no runtime overhead compared to
 * direct storage operations, as it simply delegates to the underlying store
 * with better type safety and organization.</p>
 *
 * @since 0.8.0
 */
public final class NodePersistence {
    private final NodeStore store;
    private final CommitmentScheme commitments;
    private final HashFunction hashFn;

    /**
     * Creates a new NodePersistence instance.
     *
     * @param store the underlying node storage implementation
     * @throws NullPointerException if store is null
     */
    public NodePersistence(NodeStore store, CommitmentScheme commitments, HashFunction hashFn) {
        this.store = Objects.requireNonNull(store, "NodeStore cannot be null");
        this.commitments = Objects.requireNonNull(commitments, "CommitmentScheme cannot be null");
        this.hashFn = Objects.requireNonNull(hashFn, "HashFunction cannot be null");
    }

    /**
     * Persists a node to storage and returns its hash.
     *
     * <p>This method performs the following operations:</p>
     * <ol>
     *   <li>Encodes the node to CBOR format</li>
     *   <li>Computes the Blake2b-256 hash of the encoded data</li>
     *   <li>Stores the encoded data using the hash as key</li>
     *   <li>Returns a type-safe NodeHash wrapper</li>
     * </ol>
     *
     * @param node the node to persist, must not be null
     * @return the hash of the persisted node
     * @throws NullPointerException if node is null
     * @throws RuntimeException     if encoding or storage fails
     */
    public NodeHash persist(Node node) {
        Objects.requireNonNull(node, "Node cannot be null");

        try {
            byte[] encoded = node.encode();
            byte[] hash = computeCommit(node);

            store.put(hash, encoded);

            return NodeHash.of(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to persist node: " + node.getClass().getSimpleName(), e);
        }
    }

    /**
     * Loads a node from storage by its hash.
     *
     * <p>This method retrieves the encoded node data from storage and
     * decodes it back to a Node instance. Returns null if the node
     * is not found in storage.</p>
     *
     * @param hash the hash of the node to load, must not be null
     * @return the decoded node instance, or null if not found
     * @throws NullPointerException if hash is null
     * @throws RuntimeException     if decoding fails
     */
    public Node load(NodeHash hash) {
        Objects.requireNonNull(hash, "NodeHash cannot be null");

        try {
            byte[] encoded = store.get(hash.getBytes());

            if (encoded == null) {
                return null;
            }

            return TrieEncoding.decode(encoded);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load node with hash: " + hash, e);
        }
    }

    /**
     * Checks if a node with the given hash exists in storage.
     *
     * @param hash the hash to check for, must not be null
     * @return true if the node exists, false otherwise
     * @throws NullPointerException if hash is null
     */
    public boolean exists(NodeHash hash) {
        Objects.requireNonNull(hash, "NodeHash cannot be null");

        try {
            byte[] data = store.get(hash.getBytes());
            return data != null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to check existence for hash: " + hash, e);
        }
    }

    /**
     * Removes a node from storage.
     *
     * <p>This method removes the node data associated with the given hash
     * from storage. No error is thrown if the node doesn't exist.</p>
     *
     * @param hash the hash of the node to remove, must not be null
     * @throws NullPointerException if hash is null
     * @throws RuntimeException     if deletion fails
     */
    public void delete(NodeHash hash) {
        Objects.requireNonNull(hash, "NodeHash cannot be null");

        try {
            store.delete(hash.getBytes());
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete node with hash: " + hash, e);
        }
    }

    /**
     * Persists a node only if it doesn't already exist in storage.
     *
     * <p>This method is useful for avoiding redundant storage operations
     * when the same node might be persisted multiple times.</p>
     *
     * @param node the node to persist if not already stored
     * @return the hash of the node (whether newly persisted or existing)
     * @throws NullPointerException if node is null
     * @throws RuntimeException     if operations fail
     */
    public NodeHash persistIfAbsent(Node node) {
        Objects.requireNonNull(node, "Node cannot be null");

        // Compute hash first to check existence
        byte[] hash = computeCommit(node);
        NodeHash nodeHash = NodeHash.of(hash);

        if (!exists(nodeHash)) {
            // Node doesn't exist, persist it
            try {
                byte[] encoded = node.encode();
                store.put(hash, encoded);
            } catch (Exception e) {
                throw new RuntimeException("Failed to persist node: " + node.getClass().getSimpleName(), e);
            }
        }

        return nodeHash;
    }

    /**
     * Returns the underlying NodeStore for advanced operations.
     *
     * <p>This method provides access to the raw storage layer for cases
     * where direct access is needed. Use with caution as it bypasses
     * the type safety and error handling of this class.</p>
     *
     * @return the underlying NodeStore instance
     */
    public NodeStore getUnderlyingStore() {
        return store;
    }

    /**
     * Computes the MPF commitment for the given node, flattening extension prefixes
     * into the child node's commitment so that the result matches MPF off-chain roots.
     */
    private byte[] computeCommit(Node node) {
        if (node instanceof LeafNode) {
            return node.commit(hashFn, commitments);
        }
        if (node instanceof BranchNode) {
            return node.commit(hashFn, commitments);
        }
        if (node instanceof ExtensionNode) {
            return computeExtensionCommit((ExtensionNode) node);
        }
        throw new IllegalStateException("Unknown node type: " + node.getClass());
    }

    /**
     * Flattens extension path into the commitment of the underlying child node.
     * <p>
     * For a child branch, computes commitBranch(prefix, childHashes, branchValueHash).
     * For a child leaf, computes commitLeaf(prefix + leafSuffix, valueHash).
     * Chains through multiple extensions if present.
     */
    private byte[] computeExtensionCommit(ExtensionNode ext) {
        com.bloxbean.cardano.vds.core.nibbles.Nibbles.HP hpInfo = com.bloxbean.cardano.vds.core.nibbles.Nibbles.unpackHP(ext.getHp());
        com.bloxbean.cardano.vds.core.NibblePath accPrefix = com.bloxbean.cardano.vds.core.NibblePath.of(hpInfo.nibbles);
        byte[] childHash = ext.getChild();
        if (childHash == null || childHash.length == 0) {
            // No child; fall back to simple extension commitment
            return ext.commit(hashFn, commitments);
        }

        Node current = load(NodeHash.of(childHash));
        while (current instanceof ExtensionNode) {
            ExtensionNode nextExt = (ExtensionNode) current;
            com.bloxbean.cardano.vds.core.nibbles.Nibbles.HP hpInfo2 = com.bloxbean.cardano.vds.core.nibbles.Nibbles.unpackHP(nextExt.getHp());
            accPrefix = accPrefix.concat(com.bloxbean.cardano.vds.core.NibblePath.of(hpInfo2.nibbles));
            byte[] nextChild = nextExt.getChild();
            if (nextChild == null || nextChild.length == 0) {
                // orphan extension; commit as-is
                return commitments.commitExtension(accPrefix, commitments.nullHash());
            }
            current = load(NodeHash.of(nextChild));
        }

        if (current instanceof BranchNode) {
            BranchNode br = (BranchNode) current;
            byte[][] children = br.getChildren();
            byte[][] sanitized = new byte[16][];
            for (int i = 0; i < 16; i++) {
                byte[] ch = (i < children.length) ? children[i] : null;
                sanitized[i] = ch == null || ch.length == 0 ? null : ch;
            }
            byte[] value = br.getValue();
            byte[] valueHash = value == null ? null : hashFn.digest(value);
            return commitments.commitBranch(accPrefix, sanitized, valueHash);
        } else if (current instanceof LeafNode) {
            LeafNode leaf = (LeafNode) current;
            com.bloxbean.cardano.vds.core.nibbles.Nibbles.HP leafHp = com.bloxbean.cardano.vds.core.nibbles.Nibbles.unpackHP(leaf.getHp());
            com.bloxbean.cardano.vds.core.NibblePath suffix = accPrefix.concat(com.bloxbean.cardano.vds.core.NibblePath.of(leafHp.nibbles));
            byte[] valueHash = hashFn.digest(leaf.getValue());
            return commitments.commitLeaf(suffix, valueHash);
        } else if (current == null) {
            // Missing child; fallback to simple extension hash
            return ext.commit(hashFn, commitments);
        } else {
            throw new IllegalStateException("Unsupported child type under extension: " + current.getClass());
        }
    }

    byte[] computeExtensionCommitForProof(ExtensionNode ext) {
        return computeExtensionCommit(ext);
    }
}
