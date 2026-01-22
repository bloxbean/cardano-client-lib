package com.bloxbean.cardano.vds.mpt;

import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.vds.core.NodeHash;
import com.bloxbean.cardano.vds.core.nibbles.Nibbles;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Visitor that builds a {@link TreeNode} structure from the trie.
 *
 * <p>This visitor traverses the trie and constructs a hierarchical TreeNode
 * representation that can be serialized to JSON for visualization, debugging,
 * or custom rendering purposes.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * TreeStructureVisitor visitor = new TreeStructureVisitor(persistence, rootHash);
 * TreeNode structure = rootNode.accept(visitor);
 * String json = TreeNode.toJson(structure);
 * }</pre>
 *
 * @since 0.8.0
 */
final class TreeStructureVisitor implements NodeVisitor<TreeNode> {
    private final NodePersistence persistence;
    private final byte[] nodeHash;

    /**
     * Creates a new tree structure visitor.
     *
     * @param persistence the node persistence layer for loading child nodes
     * @param nodeHash    the hash of the current node being visited
     */
    public TreeStructureVisitor(NodePersistence persistence, byte[] nodeHash) {
        this.persistence = persistence;
        this.nodeHash = nodeHash;
    }

    @Override
    public TreeNode visitLeaf(LeafNode leaf) {
        Nibbles.HP hp = Nibbles.unpackHP(leaf.getHp());
        int[] path = hp.nibbles;

        String valueHex = HexUtil.encodeHexString(leaf.getValue());
        byte[] originalKey = leaf.getOriginalKey();
        String originalKeyHex = originalKey != null ? HexUtil.encodeHexString(originalKey) : null;

        return new TreeNode.LeafTreeNode(path, valueHex, originalKeyHex);
    }

    @Override
    public TreeNode visitBranch(BranchNode branch) {
        String hash = HexUtil.encodeHexString(nodeHash);

        byte[] branchValue = branch.getValue();
        String valueHex = branchValue != null ? HexUtil.encodeHexString(branchValue) : null;

        // Build children map
        Map<String, TreeNode> children = new LinkedHashMap<>();
        for (int i = 0; i < 16; i++) {
            String nibbleKey = Integer.toHexString(i);
            byte[] childHash = branch.getChild(i);

            if (childHash == null || childHash.length == 0) {
                children.put(nibbleKey, null);
            } else {
                Node childNode = persistence.load(NodeHash.of(childHash));
                if (childNode != null) {
                    TreeStructureVisitor childVisitor = new TreeStructureVisitor(persistence, childHash);
                    TreeNode childTreeNode = childNode.accept(childVisitor);
                    children.put(nibbleKey, childTreeNode);
                } else {
                    // Missing node - represent as null
                    children.put(nibbleKey, null);
                }
            }
        }

        return new TreeNode.BranchTreeNode(hash, valueHex, children);
    }

    @Override
    public TreeNode visitExtension(ExtensionNode extension) {
        Nibbles.HP hp = Nibbles.unpackHP(extension.getHp());
        int[] path = hp.nibbles;
        String hash = HexUtil.encodeHexString(nodeHash);

        // Get child
        byte[] childHash = extension.getChild();
        TreeNode childTreeNode = null;

        if (childHash != null && childHash.length > 0) {
            Node childNode = persistence.load(NodeHash.of(childHash));
            if (childNode != null) {
                TreeStructureVisitor childVisitor = new TreeStructureVisitor(persistence, childHash);
                childTreeNode = childNode.accept(childVisitor);
            }
        }

        return new TreeNode.ExtensionTreeNode(hash, path, childTreeNode);
    }
}
