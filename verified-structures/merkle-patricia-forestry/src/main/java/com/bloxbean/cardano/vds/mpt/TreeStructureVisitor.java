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
 * <p>The visitor supports an optional node count limit. When a limit is specified,
 * the visitor will stop expanding nodes after reaching the limit and return
 * {@link TreeNode.TruncatedTreeNode} instances for unexpanded subtrees.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * // Unlimited traversal
 * TreeStructureVisitor visitor = new TreeStructureVisitor(persistence, rootHash);
 * TreeNode structure = rootNode.accept(visitor);
 *
 * // Limited traversal (max 100 nodes)
 * TreeStructureVisitor visitor = new TreeStructureVisitor(persistence, rootHash, 100);
 * TreeNode structure = rootNode.accept(visitor);
 * }</pre>
 *
 * @since 0.8.0
 */
final class TreeStructureVisitor implements NodeVisitor<TreeNode> {
    private final NodePersistence persistence;
    private final byte[] nodeHash;
    private final int maxNodes;
    private final int[] nodeCount;

    /**
     * Creates a new tree structure visitor with unlimited traversal.
     *
     * @param persistence the node persistence layer for loading child nodes
     * @param nodeHash    the hash of the current node being visited
     */
    public TreeStructureVisitor(NodePersistence persistence, byte[] nodeHash) {
        this(persistence, nodeHash, -1, new int[]{0});
    }

    /**
     * Creates a new tree structure visitor with a node count limit.
     *
     * @param persistence the node persistence layer for loading child nodes
     * @param nodeHash    the hash of the current node being visited
     * @param maxNodes    maximum number of nodes to visit (-1 for unlimited)
     */
    public TreeStructureVisitor(NodePersistence persistence, byte[] nodeHash, int maxNodes) {
        this(persistence, nodeHash, maxNodes, new int[]{0});
    }

    /**
     * Private constructor with shared counter for recursive calls.
     *
     * @param persistence the node persistence layer for loading child nodes
     * @param nodeHash    the hash of the current node being visited
     * @param maxNodes    maximum number of nodes to visit (-1 for unlimited)
     * @param nodeCount   shared counter array (single element) for tracking visited nodes
     */
    private TreeStructureVisitor(NodePersistence persistence, byte[] nodeHash, int maxNodes, int[] nodeCount) {
        this.persistence = persistence;
        this.nodeHash = nodeHash;
        this.maxNodes = maxNodes;
        this.nodeCount = nodeCount;
    }

    /**
     * Checks if the node limit has been reached.
     *
     * @return true if limit is enabled and reached, false otherwise
     */
    private boolean limitReached() {
        return maxNodes > 0 && nodeCount[0] >= maxNodes;
    }

    /**
     * Increments the node count.
     */
    private void incrementCount() {
        nodeCount[0]++;
    }

    @Override
    public TreeNode visitLeaf(LeafNode leaf) {
        incrementCount();

        Nibbles.HP hp = Nibbles.unpackHP(leaf.getHp());
        int[] path = hp.nibbles;

        String valueHex = HexUtil.encodeHexString(leaf.getValue());
        byte[] originalKey = leaf.getOriginalKey();
        String originalKeyHex = originalKey != null ? HexUtil.encodeHexString(originalKey) : null;

        return new TreeNode.LeafTreeNode(path, valueHex, originalKeyHex);
    }

    @Override
    public TreeNode visitBranch(BranchNode branch) {
        incrementCount();

        String hash = HexUtil.encodeHexString(nodeHash);

        // If limit reached, return truncated node
        if (limitReached()) {
            int childCount = branch.childCountNonNull();
            return new TreeNode.TruncatedTreeNode(hash, "branch", childCount);
        }

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
                // Check if limit reached before processing this child
                if (limitReached()) {
                    // Return truncated placeholder for this child
                    Node childNode = persistence.load(NodeHash.of(childHash));
                    if (childNode != null) {
                        String childHashHex = HexUtil.encodeHexString(childHash);
                        children.put(nibbleKey, createTruncatedNode(childNode, childHashHex));
                    } else {
                        children.put(nibbleKey, null);
                    }
                } else {
                    Node childNode = persistence.load(NodeHash.of(childHash));
                    if (childNode != null) {
                        TreeStructureVisitor childVisitor = new TreeStructureVisitor(persistence, childHash, maxNodes, nodeCount);
                        TreeNode childTreeNode = childNode.accept(childVisitor);
                        children.put(nibbleKey, childTreeNode);
                    } else {
                        // Missing node - represent as null
                        children.put(nibbleKey, null);
                    }
                }
            }
        }

        return new TreeNode.BranchTreeNode(hash, valueHex, children);
    }

    @Override
    public TreeNode visitExtension(ExtensionNode extension) {
        incrementCount();

        Nibbles.HP hp = Nibbles.unpackHP(extension.getHp());
        int[] path = hp.nibbles;
        String hash = HexUtil.encodeHexString(nodeHash);

        // If limit reached, return truncated node
        if (limitReached()) {
            return new TreeNode.TruncatedTreeNode(hash, "extension", 1);
        }

        // Get child
        byte[] childHash = extension.getChild();
        TreeNode childTreeNode = null;

        if (childHash != null && childHash.length > 0) {
            // Check if limit reached before processing child
            if (limitReached()) {
                Node childNode = persistence.load(NodeHash.of(childHash));
                if (childNode != null) {
                    String childHashHex = HexUtil.encodeHexString(childHash);
                    childTreeNode = createTruncatedNode(childNode, childHashHex);
                }
            } else {
                Node childNode = persistence.load(NodeHash.of(childHash));
                if (childNode != null) {
                    TreeStructureVisitor childVisitor = new TreeStructureVisitor(persistence, childHash, maxNodes, nodeCount);
                    childTreeNode = childNode.accept(childVisitor);
                }
            }
        }

        return new TreeNode.ExtensionTreeNode(hash, path, childTreeNode);
    }

    /**
     * Creates a truncated node representation for a node that won't be fully expanded.
     *
     * @param node the node to create a truncated representation for
     * @param hash the hash of the node as hex string
     * @return a TruncatedTreeNode representing the unexpanded node
     */
    private TreeNode createTruncatedNode(Node node, String hash) {
        if (node instanceof BranchNode) {
            BranchNode branch = (BranchNode) node;
            return new TreeNode.TruncatedTreeNode(hash, "branch", branch.childCountNonNull());
        } else if (node instanceof ExtensionNode) {
            return new TreeNode.TruncatedTreeNode(hash, "extension", 1);
        } else if (node instanceof LeafNode) {
            return new TreeNode.TruncatedTreeNode(hash, "leaf", 0);
        }
        return new TreeNode.TruncatedTreeNode(hash, "unknown", 0);
    }
}
