package com.bloxbean.cardano.vds.mpt;

import com.bloxbean.cardano.vds.core.NodeHash;
import com.bloxbean.cardano.vds.core.nibbles.Nibbles;

/**
 * Visitor that collects statistics about the trie structure.
 *
 * <p>This visitor traverses the entire tree structure, counting nodes and tracking
 * maximum depth without allocating entry objects. It is more efficient than
 * {@link EntriesCollectorVisitor} when only counts are needed.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * StatisticsVisitor visitor = new StatisticsVisitor(persistence);
 * rootNode.accept(visitor);
 * TrieStatistics stats = visitor.getStatistics();
 * }</pre>
 *
 * @since 0.8.0
 */
final class StatisticsVisitor implements NodeVisitor<Void> {

    private final NodePersistence persistence;
    private int entryCount = 0;
    private int branchCount = 0;
    private int extensionCount = 0;
    private int currentDepth = 0;
    private int maxDepth = 0;

    /**
     * Creates a new statistics visitor.
     *
     * @param persistence the node persistence layer for loading child nodes
     */
    public StatisticsVisitor(NodePersistence persistence) {
        this.persistence = persistence;
    }

    @Override
    public Void visitLeaf(LeafNode leaf) {
        entryCount++;
        // Leaf contributes its HP-encoded path length to depth
        int leafPathLength = Nibbles.unpackHP(leaf.getHp()).nibbles.length;
        int leafDepth = currentDepth + leafPathLength;
        if (leafDepth > maxDepth) {
            maxDepth = leafDepth;
        }
        return null;
    }

    @Override
    public Void visitBranch(BranchNode branch) {
        branchCount++;
        // Branch adds 1 to depth for each child traversed
        currentDepth++;
        if (currentDepth > maxDepth) {
            maxDepth = currentDepth;
        }

        // Traverse all non-null children (0-15)
        for (int i = 0; i < 16; i++) {
            byte[] childHash = branch.getChild(i);
            if (childHash != null && childHash.length > 0) {
                Node childNode = persistence.load(NodeHash.of(childHash));
                if (childNode != null) {
                    childNode.accept(this);
                }
            }
        }

        currentDepth--;
        return null;
    }

    @Override
    public Void visitExtension(ExtensionNode extension) {
        extensionCount++;
        // Extension contributes its HP-encoded path length to depth
        int extPathLength = Nibbles.unpackHP(extension.getHp()).nibbles.length;
        currentDepth += extPathLength;
        if (currentDepth > maxDepth) {
            maxDepth = currentDepth;
        }

        // Traverse child
        byte[] childHash = extension.getChild();
        if (childHash != null && childHash.length > 0) {
            Node childNode = persistence.load(NodeHash.of(childHash));
            if (childNode != null) {
                childNode.accept(this);
            }
        }

        currentDepth -= extPathLength;
        return null;
    }

    /**
     * Returns the collected statistics.
     *
     * @return statistics about the traversed trie
     */
    public TrieStatistics getStatistics() {
        return new TrieStatistics(entryCount, branchCount, extensionCount, maxDepth);
    }
}
