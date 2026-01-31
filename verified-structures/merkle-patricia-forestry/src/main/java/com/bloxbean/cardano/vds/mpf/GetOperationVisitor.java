package com.bloxbean.cardano.vds.mpf;

import com.bloxbean.cardano.vds.core.NodeHash;
import com.bloxbean.cardano.vds.core.nibbles.Nibbles;

/**
 * Visitor implementation for GET operations using the new immutable node infrastructure.
 *
 * <p>This visitor handles the logic for retrieving values from the trie by traversing
 * the appropriate path through the node structure. Each node type has specific logic
 * for key lookup based on the trie's hexary structure.</p>
 */
final class GetOperationVisitor implements NodeVisitor<byte[]> {
    private final NodePersistence persistence;
    private final int[] keyNibbles;
    private final int position;

    /**
     * Creates a new get operation visitor.
     *
     * @param persistence the node persistence layer
     * @param keyNibbles  the full key as nibbles
     * @param position    the current position in the key
     */
    public GetOperationVisitor(NodePersistence persistence, int[] keyNibbles, int position) {
        this.persistence = persistence;
        this.keyNibbles = keyNibbles;
        this.position = position;
    }

    @Override
    public byte[] visitLeaf(LeafNode leaf) {
        int[] leafNibbles = Nibbles.unpackHP(leaf.getHp()).nibbles;
        int[] remainingKey = slice(keyNibbles, position, keyNibbles.length);

        // Check if the remaining key matches the leaf's path exactly
        if (leafNibbles.length == remainingKey.length) {
            for (int i = 0; i < leafNibbles.length; i++) {
                if (leafNibbles[i] != remainingKey[i]) {
                    return null; // Path mismatch
                }
            }
            return leaf.getValue(); // Exact match found
        }

        return null; // Length mismatch
    }

    @Override
    public byte[] visitBranch(BranchNode branch) {
        if (position == keyNibbles.length) {
            // Key ends at this branch - return the branch's value
            return branch.getValue();
        }

        // Navigate to the appropriate child
        int childIndex = keyNibbles[position];
        byte[] childHash = branch.getChild(childIndex);

        if (childHash == null) {
            return null; // No child at this index
        }

        // Recursively search in the child subtree
        return getAt(childHash, keyNibbles, position + 1);
    }

    @Override
    public byte[] visitExtension(ExtensionNode extension) {
        int[] extensionNibbles = Nibbles.unpackHP(extension.getHp()).nibbles;
        int[] remainingKey = slice(keyNibbles, position, keyNibbles.length);

        // Check if the remaining key starts with the extension's path
        if (remainingKey.length < extensionNibbles.length) {
            return null; // Key is shorter than extension path
        }

        for (int i = 0; i < extensionNibbles.length; i++) {
            if (remainingKey[i] != extensionNibbles[i]) {
                return null; // Path mismatch
            }
        }

        // Extension path matches - continue with the child
        return getAt(extension.getChild(), keyNibbles, position + extensionNibbles.length);
    }

    /**
     * Helper method for recursive get operations.
     */
    private byte[] getAt(byte[] nodeHash, int[] keyNibbles, int position) {
        if (nodeHash == null) {
            return null; // No node at this path
        }

        Node node = persistence.load(NodeHash.of(nodeHash));
        if (node == null) {
            return null; // Missing node
        }

        GetOperationVisitor getVisitor = new GetOperationVisitor(persistence, keyNibbles, position);
        return node.accept(getVisitor);
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
