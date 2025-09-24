package com.bloxbean.cardano.statetrees.mpt;

import com.bloxbean.cardano.statetrees.common.NodeHash;
import com.bloxbean.cardano.statetrees.common.nibbles.Nibbles;

/**
 * Visitor implementation for PUT operations using the new immutable node infrastructure.
 *
 * <p>This visitor handles the logic for inserting or updating key-value pairs in the trie
 * using type-safe operations on immutable nodes. Each node type has specific logic for
 * handling key insertion based on the trie's structure.</p>
 */
final class PutOperationVisitor implements NodeVisitor<NodeHash> {
    private final NodePersistence persistence;
    private final int[] keyNibbles;
    private final int position;
    private final byte[] value;

    /**
     * Creates a new put operation visitor.
     *
     * @param persistence the node persistence layer
     * @param keyNibbles  the full key as nibbles
     * @param position    the current position in the key
     * @param value       the value to insert
     */
    public PutOperationVisitor(NodePersistence persistence, int[] keyNibbles, int position, byte[] value) {
        this.persistence = persistence;
        this.keyNibbles = keyNibbles;
        this.position = position;
        this.value = value;
    }

    @Override
    public NodeHash visitLeaf(LeafNode leaf) {
        int[] leafNibbles = Nibbles.unpackHP(leaf.getHp()).nibbles;
        int[] remainingKey = slice(keyNibbles, position, keyNibbles.length);
        int common = Nibbles.commonPrefixLen(remainingKey, leafNibbles);

        if (common == leafNibbles.length && position + common == keyNibbles.length) {
            // Exact match - update the leaf value
            LeafNode updatedLeaf = leaf.withValue(value);
            return persistence.persist(updatedLeaf);
        }

        // Need to split the leaf - delegate to NodeSplitter
        return NodeSplitter.splitLeafNode(persistence, leaf, remainingKey, value, common);
    }

    @Override
    public NodeHash visitBranch(BranchNode branch) {
        if (position == keyNibbles.length) {
            // Key ends at this branch - set the value
            BranchNode updatedBranch = branch.withValue(value);
            return persistence.persist(updatedBranch);
        }

        // Navigate to the appropriate child
        int childIndex = keyNibbles[position];
        byte[] childHash = branch.getChild(childIndex);

        // Recursively put in the child subtree
        NodeHash newChildHash = putAt(childHash, keyNibbles, position + 1, value);

        BranchNode updatedBranch = branch.withChild(childIndex, newChildHash.toBytes());
        return persistence.persist(updatedBranch);
    }

    @Override
    public NodeHash visitExtension(ExtensionNode extension) {
        int[] extensionNibbles = Nibbles.unpackHP(extension.getHp()).nibbles;
        int[] remainingKey = slice(keyNibbles, position, keyNibbles.length);
        int common = Nibbles.commonPrefixLen(remainingKey, extensionNibbles);

        if (common == extensionNibbles.length) {
            // Full extension path matches - continue with the child
            NodeHash newChildHash = putAt(extension.getChild(), keyNibbles, position + common, value);
            ExtensionNode updatedExtension = extension.withChild(newChildHash.toBytes());
            return persistence.persist(updatedExtension);
        }

        // Need to split the extension - delegate to NodeSplitter
        return NodeSplitter.splitExtensionNode(persistence, extension, remainingKey, value, common);
    }

    /**
     * Helper method for recursive put operations.
     */
    private NodeHash putAt(byte[] nodeHash, int[] keyNibbles, int position, byte[] value) {
        if (nodeHash == null) {
            // Create new leaf node
            int[] remainingNibbles = slice(keyNibbles, position, keyNibbles.length);
            byte[] hp = Nibbles.packHP(true, remainingNibbles);
            LeafNode leaf = LeafNode.of(hp, value);
            return persistence.persist(leaf);
        }

        Node node = persistence.load(NodeHash.of(nodeHash));
        if (node == null) {
            // Missing node - create new leaf
            int[] remainingNibbles = slice(keyNibbles, position, keyNibbles.length);
            byte[] hp = Nibbles.packHP(true, remainingNibbles);
            LeafNode leaf = LeafNode.of(hp, value);
            return persistence.persist(leaf);
        }

        PutOperationVisitor putVisitor = new PutOperationVisitor(persistence, keyNibbles, position, value);
        return node.accept(putVisitor);
    }

    /**
     * Utility method to slice an array.
     */
    private static int[] slice(int[] array, int from, int to) {
        int len = Math.max(0, to - from);
        int[] out = new int[len];
        for (int i = 0; i < len; i++) {
            out[i] = array[from + i];
        }
        return out;
    }
}
