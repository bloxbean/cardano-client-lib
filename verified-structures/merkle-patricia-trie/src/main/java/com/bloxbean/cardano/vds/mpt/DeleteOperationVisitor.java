package com.bloxbean.cardano.vds.mpt;

import com.bloxbean.cardano.vds.core.NodeHash;
import com.bloxbean.cardano.vds.core.nibbles.Nibbles;

/**
 * Visitor implementation for DELETE operations on MPT nodes.
 *
 * <p>This visitor implements the deletion logic for the Merkle Patricia Trie,
 * handling node compression and merging operations that occur after deletion.</p>
 */
final class DeleteOperationVisitor implements NodeVisitor<NodeHash> {
    private final NodePersistence persistence;
    private final int[] keyNibbles;
    private final int position;

    /**
     * Creates a new delete operation visitor.
     *
     * @param persistence the node persistence layer
     * @param keyNibbles  the full key as nibbles
     * @param position    the current position in the key
     */
    public DeleteOperationVisitor(NodePersistence persistence, int[] keyNibbles, int position) {
        this.persistence = persistence;
        this.keyNibbles = keyNibbles;
        this.position = position;
    }

    @Override
    public NodeHash visitLeaf(LeafNode node) {
        int[] nibbles = Nibbles.unpackHP(node.getHp()).nibbles;
        int[] targetNibbles = slice(keyNibbles, position, keyNibbles.length);

        if (nibbles.length == targetNibbles.length &&
                Nibbles.commonPrefixLen(nibbles, targetNibbles) == nibbles.length) {
            return null; // Delete this leaf
        }

        // Key not found, return unchanged node
        return persistence.persist(node);
    }

    @Override
    public NodeHash visitBranch(BranchNode node) {
        BranchNode updated;

        if (position == keyNibbles.length) {
            // Delete value at this branch
            updated = node.withValue(null);
        } else {
            // Delete from child
            int childIndex = keyNibbles[position];
            byte[] childHash = node.getChild(childIndex);

            NodeHash newChildHash = null;
            if (childHash != null) {
                Node childNode = persistence.load(NodeHash.of(childHash));
                if (childNode != null) {
                    DeleteOperationVisitor childVisitor = new DeleteOperationVisitor(persistence, keyNibbles, position + 1);
                    newChildHash = childNode.accept(childVisitor);
                }
            }

            updated = node.withChild(childIndex, newChildHash != null ? newChildHash.toBytes() : null);
        }

        // Compress if needed
        return compressBranch(updated);
    }

    @Override
    public NodeHash visitExtension(ExtensionNode node) {
        int[] enNibs = Nibbles.unpackHP(node.getHp()).nibbles;
        int[] targetNibbles = slice(keyNibbles, position, keyNibbles.length);
        int common = Nibbles.commonPrefixLen(targetNibbles, enNibs);

        if (common < enNibs.length) {
            // Key doesn't match this extension path
            return persistence.persist(node);
        }

        // Delete from child
        Node childNode = persistence.load(NodeHash.of(node.getChild()));
        if (childNode == null) {
            return persistence.persist(node); // Child not found
        }

        DeleteOperationVisitor childVisitor = new DeleteOperationVisitor(persistence, keyNibbles, position + enNibs.length);
        NodeHash newChildHash = childNode.accept(childVisitor);

        if (newChildHash == null) {
            return null; // Child removed, remove this extension too
        }

        // Check if child can be merged
        Node newChild = persistence.load(newChildHash);
        if (newChild instanceof ExtensionNode) {
            // Merge extensions
            ExtensionNode childExt = (ExtensionNode) newChild;
            int[] mergedNibs = concat(Nibbles.unpackHP(node.getHp()).nibbles,
                    Nibbles.unpackHP(childExt.getHp()).nibbles);
            byte[] hp = Nibbles.packHP(false, mergedNibs);
            ExtensionNode merged = ExtensionNode.of(hp, childExt.getChild());
            return persistence.persist(merged);
        } else if (newChild instanceof LeafNode) {
            // Merge extension + leaf into leaf
            LeafNode childLeaf = (LeafNode) newChild;
            int[] mergedNibs = concat(Nibbles.unpackHP(node.getHp()).nibbles,
                    Nibbles.unpackHP(childLeaf.getHp()).nibbles);
            byte[] hp = Nibbles.packHP(true, mergedNibs);
            LeafNode merged = LeafNode.of(hp, childLeaf.getValue());
            return persistence.persist(merged);
        } else {
            // Update extension with new child
            ExtensionNode updated = node.withChild(newChildHash.toBytes());
            return persistence.persist(updated);
        }
    }

    /**
     * Compresses a branch node if it meets compression criteria.
     */
    private NodeHash compressBranch(BranchNode branch) {
        int childCnt = branch.childCountNonNull();

        if (childCnt == 0 && branch.getValue() == null) {
            return null; // Empty branch, delete it
        } else if (childCnt == 0 && branch.getValue() != null) {
            // Branch with only value becomes leaf
            byte[] hp = Nibbles.packHP(true, new int[0]);
            LeafNode leaf = LeafNode.of(hp, branch.getValue());
            return persistence.persist(leaf);
        } else if (childCnt == 1 && branch.getValue() == null) {
            // Branch with single child becomes extension
            int firstChildIdx = branch.firstChildIndex();
            byte[] childHash = branch.getChild(firstChildIdx);

            Node child = persistence.load(NodeHash.of(childHash));
            if (child == null) {
                return persistence.persist(branch); // Child not found, keep as-is
            }

            if (child instanceof ExtensionNode) {
                ExtensionNode childExt = (ExtensionNode) child;
                int[] merged = concat(new int[]{firstChildIdx}, Nibbles.unpackHP(childExt.getHp()).nibbles);
                byte[] hp = Nibbles.packHP(false, merged);
                ExtensionNode extension = ExtensionNode.of(hp, childExt.getChild());
                return persistence.persist(extension);
            } else if (child instanceof LeafNode) {
                LeafNode childLeaf = (LeafNode) child;
                int[] merged = concat(new int[]{firstChildIdx}, Nibbles.unpackHP(childLeaf.getHp()).nibbles);
                byte[] hp = Nibbles.packHP(true, merged);
                LeafNode leaf = LeafNode.of(hp, childLeaf.getValue());
                return persistence.persist(leaf);
            } else {
                // Child is branch; create extension of single nibble
                byte[] hp = Nibbles.packHP(false, new int[]{firstChildIdx});
                ExtensionNode extension = ExtensionNode.of(hp, childHash);
                return persistence.persist(extension);
            }
        } else {
            // Branch doesn't need compression
            return persistence.persist(branch);
        }
    }

    // Utility methods
    private static int[] slice(int[] array, int fromIndex, int toIndex) {
        int length = Math.max(0, toIndex - fromIndex);
        int[] result = new int[length];
        for (int i = 0; i < length; i++) {
            result[i] = array[fromIndex + i];
        }
        return result;
    }

    private static int[] concat(int[] first, int[] second) {
        int[] result = new int[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
