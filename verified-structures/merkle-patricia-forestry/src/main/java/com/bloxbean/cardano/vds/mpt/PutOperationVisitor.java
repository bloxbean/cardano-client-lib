package com.bloxbean.cardano.vds.mpt;

import com.bloxbean.cardano.vds.core.NodeHash;
import com.bloxbean.cardano.vds.core.nibbles.Nibbles;

/**
 * Visitor implementation for PUT operations on MPT nodes.
 *
 * <p>This visitor traverses the trie to insert or update a key-value pair.
 * Unlike delete operations, put operations ALWAYS return a non-null hash
 * because we're adding/updating data, not removing it.</p>
 *
 * <h3>Return Value Semantics:</h3>
 * <table border="1">
 *   <tr><th>Scenario</th><th>Return Value</th><th>Description</th></tr>
 *   <tr><td>Key updated</td><td>New hash</td><td>Existing value replaced with new value</td></tr>
 *   <tr><td>Key inserted</td><td>New hash</td><td>New leaf node created for the key</td></tr>
 *   <tr><td>Structure split</td><td>New hash</td><td>Node split to accommodate diverging keys</td></tr>
 * </table>
 *
 * <h3>Key Storage:</h3>
 * <p>The visitor optionally stores original (unhashed) keys for debugging purposes.
 * Keys are NOT used in commitment calculations, ensuring the trie's root hash
 * is identical whether keys are stored or not. This enables debugging and
 * inspection of the trie without affecting cryptographic integrity.</p>
 *
 * <h3>Node Splitting:</h3>
 * <p>When inserting a key that diverges from an existing path, the visitor uses
 * {@link NodeSplitter} to create appropriate branch structures. This maintains
 * the MPT invariants while accommodating the new key-value pair.</p>
 *
 * @see DeleteOperationVisitor for the corresponding delete operation
 * @see NodeSplitter for node splitting logic
 */
final class PutOperationVisitor implements NodeVisitor<NodeHash> {
    private final NodePersistence persistence;
    private final int[] keyNibbles;
    private final int position;
    private final byte[] value;
    private final byte[] key;

    /**
     * Creates a new put operation visitor without key storage.
     *
     * @param persistence the node persistence layer
     * @param keyNibbles  the full key as nibbles
     * @param position    the current position in the key
     * @param value       the value to insert
     */
    public PutOperationVisitor(NodePersistence persistence, int[] keyNibbles, int position, byte[] value) {
        this(persistence, keyNibbles, position, value, null);
    }

    /**
     * Creates a new put operation visitor with optional key storage.
     *
     * @param persistence the node persistence layer
     * @param keyNibbles  the full key as nibbles
     * @param position    the current position in the key
     * @param value       the value to insert
     * @param key         the original (unhashed) key for debugging, or null
     */
    public PutOperationVisitor(NodePersistence persistence, int[] keyNibbles, int position, byte[] value, byte[] key) {
        this.persistence = persistence;
        this.keyNibbles = keyNibbles;
        this.position = position;
        this.value = value;
        this.key = key;
    }

    /**
     * Handles insertion at a leaf node.
     *
     * <p><b>Two scenarios:</b></p>
     * <ol>
     *   <li><b>Exact match:</b> Key matches leaf's stored suffix exactly - update the value in place.
     *       If a key is provided, it will be stored/updated on the leaf.</li>
     *   <li><b>Partial/no match:</b> Key diverges from leaf's suffix - split into branch structure
     *       using {@link NodeSplitter#splitLeafNode}.</li>
     * </ol>
     *
     * <p>When splitting, the {@link NodeSplitter} creates a branch node at the divergence point
     * with the existing leaf and new leaf as children. The key is passed through
     * to be stored on the new leaf.</p>
     *
     * @param leaf the leaf node to insert into
     * @return hash of the updated or split node structure
     */
    @Override
    public NodeHash visitLeaf(LeafNode leaf) {
        int[] leafNibbles = Nibbles.unpackHP(leaf.getHp()).nibbles;
        int[] remainingKey = slice(keyNibbles, position, keyNibbles.length);
        int common = Nibbles.commonPrefixLen(remainingKey, leafNibbles);

        if (common == leafNibbles.length && position + common == keyNibbles.length) {
            // Exact match - update the leaf value (and preserve/update key)
            LeafNode updatedLeaf = leaf.withValue(value);
            if (key != null) {
                updatedLeaf = updatedLeaf.withKey(key);
            }
            return persistence.persist(updatedLeaf);
        }

        // Need to split the leaf
        return NodeSplitter.splitLeafNode(persistence, leaf, remainingKey, value, common, key);
    }

    /**
     * Handles insertion at a branch node (16-way branching point with optional value).
     *
     * <p><b>Two scenarios:</b></p>
     * <ol>
     *   <li><b>Key ends here:</b> ({@code position == keyNibbles.length}) - store the value
     *       at this branch node directly. Branch nodes can hold a value when a key ends
     *       at the branching point.</li>
     *   <li><b>Key continues:</b> Navigate to the appropriate child slot (0-15 based on
     *       the next nibble) and recurse. If the child slot is empty, {@link #putAt}
     *       creates a new leaf node. If the child exists, we recursively visit it.</li>
     * </ol>
     *
     * @param branch the branch node to insert into
     * @return hash of the updated branch node
     */
    @Override
    public NodeHash visitBranch(BranchNode branch) {
        if (position == keyNibbles.length) {
            // Key ends at this branch - set/update the value
            BranchNode updatedBranch = branch.withValue(value);
            return persistence.persist(updatedBranch);
        }

        // Navigate to the appropriate child
        int childIndex = keyNibbles[position];
        byte[] childHash = branch.getChild(childIndex);

        // Recursively put in the child subtree
        NodeHash childResult = putAt(childHash, keyNibbles, position + 1, value, key);

        BranchNode updatedBranch = branch.withChild(childIndex, childResult.toBytes());
        return persistence.persist(updatedBranch);
    }

    /**
     * Handles insertion through an extension node (path compression node).
     *
     * <p>Extension nodes compress paths with no branching. When inserting, we check
     * if the remaining key matches the extension's path prefix.</p>
     *
     * <p><b>Two scenarios:</b></p>
     * <ol>
     *   <li><b>Full path match:</b> Key's remaining nibbles start with extension's full path -
     *       continue recursively into the extension's child via {@link #putAt}.</li>
     *   <li><b>Partial match:</b> Key diverges mid-path - split the extension using
     *       {@link NodeSplitter#splitExtensionNode} to create a branch at the divergence
     *       point with appropriate children.</li>
     * </ol>
     *
     * @param extension the extension node to insert through
     * @return hash of the updated or split node structure
     */
    @Override
    public NodeHash visitExtension(ExtensionNode extension) {
        int[] extensionNibbles = Nibbles.unpackHP(extension.getHp()).nibbles;
        int[] remainingKey = slice(keyNibbles, position, keyNibbles.length);
        int common = Nibbles.commonPrefixLen(remainingKey, extensionNibbles);

        if (common == extensionNibbles.length) {
            // Full extension path matches - continue with the child
            NodeHash childResult = putAt(extension.getChild(), keyNibbles, position + common, value, key);
            ExtensionNode updatedExtension = extension.withChild(childResult.toBytes());
            return persistence.persist(updatedExtension);
        }

        // Need to split the extension
        return NodeSplitter.splitExtensionNode(persistence, extension, remainingKey, value, common, key);
    }

    /**
     * Recursive helper that handles insertion at a child location.
     *
     * <p>This method handles three cases:</p>
     * <ol>
     *   <li><b>Null child hash:</b> No child exists at this slot - create a new leaf node
     *       with the remaining key suffix and the value.</li>
     *   <li><b>Missing node:</b> Hash exists but node not found in storage - create a new
     *       leaf (handles data corruption or sparse storage gracefully).</li>
     *   <li><b>Existing node:</b> Load the child node and recursively visit it using a
     *       new {@link PutOperationVisitor} at the updated position.</li>
     * </ol>
     *
     * @param nodeHash   the child's hash (may be null if slot is empty)
     * @param keyNibbles the full key as nibbles
     * @param position   current position in the key (after navigating to this child)
     * @param value      the value to insert
     * @param key        the original (unhashed) key for debugging, or null
     * @return hash of the new or updated subtree
     */
    private NodeHash putAt(byte[] nodeHash, int[] keyNibbles, int position, byte[] value, byte[] key) {
        if (nodeHash == null) {
            // Create new leaf node
            int[] remainingNibbles = slice(keyNibbles, position, keyNibbles.length);
            byte[] hp = Nibbles.packHP(true, remainingNibbles);
            LeafNode leaf = LeafNode.of(hp, value, key);
            return persistence.persist(leaf);
        }

        Node node = persistence.load(NodeHash.of(nodeHash));
        if (node == null) {
            // Missing node - create new leaf
            int[] remainingNibbles = slice(keyNibbles, position, keyNibbles.length);
            byte[] hp = Nibbles.packHP(true, remainingNibbles);
            LeafNode leaf = LeafNode.of(hp, value, key);
            return persistence.persist(leaf);
        }

        PutOperationVisitor putVisitor = new PutOperationVisitor(persistence, keyNibbles, position, value, key);
        return node.accept(putVisitor);
    }

    // Utility method
    private static int[] slice(int[] array, int from, int to) {
        int len = Math.max(0, to - from);
        int[] out = new int[len];
        for (int i = 0; i < len; i++) {
            out[i] = array[from + i];
        }
        return out;
    }
}
