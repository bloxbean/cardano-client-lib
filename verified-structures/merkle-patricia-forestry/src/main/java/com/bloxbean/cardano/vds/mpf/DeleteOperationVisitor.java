package com.bloxbean.cardano.vds.mpf;

import com.bloxbean.cardano.vds.core.NodeHash;
import com.bloxbean.cardano.vds.core.nibbles.Nibbles;

import java.util.Arrays;

/**
 * Visitor implementation for DELETE operations on MPT nodes.
 *
 * <p>This visitor traverses the trie to find and delete a key. Each visit method
 * returns a signal to its parent about what happened:</p>
 *
 * <h3>Return Value Semantics:</h3>
 * <table border="1">
 *   <tr><th>Return Value</th><th>Meaning</th><th>Parent Action</th></tr>
 *   <tr><td>{@code null}</td><td>Node was deleted</td><td>Remove child reference</td></tr>
 *   <tr><td>Same hash</td><td>Key not found, unchanged</td><td>Keep as-is</td></tr>
 *   <tr><td>New hash</td><td>Subtree modified</td><td>Update child reference</td></tr>
 * </table>
 *
 * <h3>Why {@code persistence.persist(node)} for "unchanged"?</h3>
 * <p>The {@code persist()} method returns the node's hash (creating it if needed).
 * When key is not found, we return the unchanged node's hash so the parent can
 * detect "no change" by comparing hashes. This is NOT saving new data - it's
 * returning an identifier for the existing node.</p>
 *
 * <p>The visitor also handles node compression and merging operations that occur
 * after deletion to maintain trie invariants (e.g., a branch with only one child
 * and no value should become an extension node).</p>
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

    /**
     * Handles deletion at a leaf node.
     *
     * <p>Compares the leaf's stored key suffix with the target key suffix.
     * If they match exactly, the key is found and we signal deletion by returning {@code null}.</p>
     *
     * @param node the leaf node to check
     * @return {@code null} if key found (delete this leaf),
     *         or the node's hash if key not found (keep unchanged)
     */
    @Override
    public NodeHash visitLeaf(LeafNode node) {
        int[] nibbles = Nibbles.unpackHP(node.getHp()).nibbles;
        int[] targetNibbles = slice(keyNibbles, position, keyNibbles.length);

        if (nibbles.length == targetNibbles.length &&
                Nibbles.commonPrefixLen(nibbles, targetNibbles) == nibbles.length) {
            // Key found - delete this leaf
            return null;
        }

        // Key not found, return unchanged node
        return persistence.persist(node);
    }

    /**
     * Handles deletion from a branch node (16-way branching point with optional value).
     *
     * <p><b>Two scenarios:</b></p>
     * <ol>
     *   <li>Key ends at this branch (position == keyNibbles.length) - delete the branch's value</li>
     *   <li>Key continues through a child - recursively delete from child subtree</li>
     * </ol>
     *
     * <p>After deletion, the branch may need compression if it becomes degenerate
     * (e.g., single child with no value should become an extension).</p>
     *
     * @param node the branch node to process
     * @return {@code null} if branch becomes empty, new hash if modified,
     *         or same hash if key not found
     */
    @Override
    public NodeHash visitBranch(BranchNode node) {
        BranchNode updated;

        if (position == keyNibbles.length) {
            // Delete value at this branch
            if (node.getValue() == null) {
                // No value to delete - unchanged
                return persistence.persist(node);
            }
            updated = node.withValue(null);
        } else {
            // Delete from child
            int childIndex = keyNibbles[position];
            byte[] childHash = node.getChild(childIndex);

            if (childHash == null) {
                // Child doesn't exist - key not found - unchanged
                return persistence.persist(node);
            }

            Node childNode = persistence.load(NodeHash.of(childHash));
            if (childNode == null) {
                // Child node missing - key not found - unchanged
                return persistence.persist(node);
            }

            DeleteOperationVisitor childVisitor = new DeleteOperationVisitor(persistence, keyNibbles, position + 1);
            NodeHash newChildHash = childNode.accept(childVisitor);

            // Check if anything changed by comparing hashes
            if (newChildHash != null && Arrays.equals(newChildHash.toBytes(), childHash)) {
                // Child hash unchanged - key not found - unchanged
                return persistence.persist(node);
            }

            // Key was deleted (hash changed or became null)
            updated = node.withChild(childIndex, newChildHash != null ? newChildHash.toBytes() : null);
        }

        // Compress if needed
        return compressBranch(updated);
    }

    /**
     * Handles deletion through an extension node (path compression node).
     *
     * <p>Extensions store a path prefix. If the target key doesn't match this prefix,
     * the key cannot exist in this subtree. If it matches, we continue to the child.</p>
     *
     * <p><b>Post-deletion merging:</b> After child modification, we may need to merge
     * this extension with its child (e.g., Extension + Extension = longer Extension,
     * or Extension + Leaf = Leaf with longer path).</p>
     *
     * @param node the extension node to process
     * @return {@code null} if extension should be removed, new hash if modified,
     *         or same hash if key not found
     */
    @Override
    public NodeHash visitExtension(ExtensionNode node) {
        int[] enNibs = Nibbles.unpackHP(node.getHp()).nibbles;
        int[] targetNibbles = slice(keyNibbles, position, keyNibbles.length);
        int common = Nibbles.commonPrefixLen(targetNibbles, enNibs);

        if (common < enNibs.length) {
            // Key doesn't match this extension path - key not found - unchanged
            return persistence.persist(node);
        }

        // Delete from child
        byte[] childHash = node.getChild();
        Node childNode = persistence.load(NodeHash.of(childHash));
        if (childNode == null) {
            // Child not found - key not found - unchanged
            return persistence.persist(node);
        }

        DeleteOperationVisitor childVisitor = new DeleteOperationVisitor(persistence, keyNibbles, position + enNibs.length);
        NodeHash newChildHash = childNode.accept(childVisitor);

        // Check if anything changed by comparing hashes
        if (newChildHash != null && Arrays.equals(newChildHash.toBytes(), childHash)) {
            // Child hash unchanged - key not found - unchanged
            return persistence.persist(node);
        }

        // Key was deleted
        if (newChildHash == null) {
            // Child removed, remove this extension too
            return null;
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
            // Merge extension + leaf into leaf (preserve key)
            LeafNode childLeaf = (LeafNode) newChild;
            int[] mergedNibs = concat(Nibbles.unpackHP(node.getHp()).nibbles,
                    Nibbles.unpackHP(childLeaf.getHp()).nibbles);
            byte[] hp = Nibbles.packHP(true, mergedNibs);
            LeafNode merged = LeafNode.of(hp, childLeaf.getValue(), childLeaf.getKey());
            return persistence.persist(merged);
        } else {
            // Update extension with new child
            ExtensionNode updated = node.withChild(newChildHash.toBytes());
            return persistence.persist(updated);
        }
    }

    /**
     * Compresses a branch node after deletion to maintain trie invariants.
     *
     * <p><b>MPT Invariant:</b> A branch with only one child and no value is wasteful
     * and should be converted to an extension node.</p>
     *
     * <table border="1">
     *   <tr><th>Children</th><th>Value</th><th>Action</th></tr>
     *   <tr><td>0</td><td>null</td><td>Delete branch (return null)</td></tr>
     *   <tr><td>0</td><td>exists</td><td>Convert to leaf with empty path</td></tr>
     *   <tr><td>1</td><td>null</td><td>Convert to extension pointing to child</td></tr>
     *   <tr><td>1</td><td>exists</td><td>Keep as branch (valid)</td></tr>
     *   <tr><td>2+</td><td>any</td><td>Keep as branch (valid)</td></tr>
     * </table>
     *
     * <p>When converting a branch with a single child to an extension, we may also
     * merge with the child if it's an extension or leaf to avoid unnecessary node chains.</p>
     *
     * @param branch the branch node to potentially compress
     * @return the hash of the compressed/unchanged node, or {@code null} if deleted
     */
    private NodeHash compressBranch(BranchNode branch) {
        int childCnt = branch.childCountNonNull();

        if (childCnt == 0 && branch.getValue() == null) {
            // Empty branch, delete it
            return null;
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
                // Child not found, keep as-is
                return persistence.persist(branch);
            }

            if (child instanceof ExtensionNode) {
                ExtensionNode childExt = (ExtensionNode) child;
                int[] merged = concat(new int[]{firstChildIdx}, Nibbles.unpackHP(childExt.getHp()).nibbles);
                byte[] hp = Nibbles.packHP(false, merged);
                ExtensionNode extension = ExtensionNode.of(hp, childExt.getChild());
                return persistence.persist(extension);
            } else if (child instanceof LeafNode) {
                // Branch with single leaf child becomes leaf (preserve key)
                LeafNode childLeaf = (LeafNode) child;
                int[] merged = concat(new int[]{firstChildIdx}, Nibbles.unpackHP(childLeaf.getHp()).nibbles);
                byte[] hp = Nibbles.packHP(true, merged);
                LeafNode leaf = LeafNode.of(hp, childLeaf.getValue(), childLeaf.getKey());
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
