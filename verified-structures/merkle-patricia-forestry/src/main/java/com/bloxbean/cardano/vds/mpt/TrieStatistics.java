package com.bloxbean.cardano.vds.mpt;

/**
 * Statistics about a Merkle Patricia Trie structure.
 *
 * <p>This class provides lightweight metrics about the trie without requiring
 * full entry enumeration. Use {@link MpfTrie#getStatistics()} to obtain
 * statistics.</p>
 *
 * <p><b>Example usage:</b></p>
 * <pre>{@code
 * MpfTrie trie = new MpfTrie(store);
 * trie.put("key1".getBytes(), "value1".getBytes());
 * trie.put("key2".getBytes(), "value2".getBytes());
 *
 * TrieStatistics stats = trie.getStatistics();
 * System.out.println("Entries: " + stats.getEntryCount());
 * System.out.println("Total nodes: " + stats.totalNodes());
 * System.out.println("Max depth: " + stats.getMaxDepth());
 * }</pre>
 *
 * @since 0.8.0
 */
public final class TrieStatistics {

    private static final TrieStatistics EMPTY = new TrieStatistics(0, 0, 0, 0);

    private final int entryCount;
    private final int branchCount;
    private final int extensionCount;
    private final int maxDepth;

    /**
     * Creates a new TrieStatistics instance.
     *
     * @param entryCount     the number of key-value pairs (leaf nodes)
     * @param branchCount    the number of branch nodes
     * @param extensionCount the number of extension nodes
     * @param maxDepth       the maximum depth reached during traversal
     */
    public TrieStatistics(int entryCount, int branchCount, int extensionCount, int maxDepth) {
        this.entryCount = entryCount;
        this.branchCount = branchCount;
        this.extensionCount = extensionCount;
        this.maxDepth = maxDepth;
    }

    /**
     * Returns an empty statistics instance for empty tries.
     *
     * @return statistics with all counts set to zero
     */
    public static TrieStatistics empty() {
        return EMPTY;
    }

    /**
     * Returns the number of entries (key-value pairs) in the trie.
     *
     * <p>This corresponds to the number of leaf nodes, as each leaf
     * stores exactly one key-value pair.</p>
     *
     * @return the entry count
     */
    public int getEntryCount() {
        return entryCount;
    }

    /**
     * Returns the number of branch nodes in the trie.
     *
     * <p>Branch nodes are 16-way nodes that split the key space.
     * More branch nodes generally indicate a more spread-out key distribution.</p>
     *
     * @return the branch node count
     */
    public int getBranchCount() {
        return branchCount;
    }

    /**
     * Returns the number of extension nodes in the trie.
     *
     * <p>Extension nodes compress common prefixes. More extension nodes
     * indicate keys with shared prefixes.</p>
     *
     * @return the extension node count
     */
    public int getExtensionCount() {
        return extensionCount;
    }

    /**
     * Returns the maximum depth reached during traversal.
     *
     * <p>This is measured in traversal steps (nibbles), not bytes.
     * For MpfTrie with Blake2b-256 hashed keys, the maximum theoretical
     * depth is 64 (32 bytes = 64 nibbles).</p>
     *
     * @return the maximum depth
     */
    public int getMaxDepth() {
        return maxDepth;
    }

    /**
     * Returns the total number of nodes in the trie.
     *
     * <p>This is the sum of entry (leaf), branch, and extension nodes.</p>
     *
     * @return the total node count
     */
    public int totalNodes() {
        return entryCount + branchCount + extensionCount;
    }

    @Override
    public String toString() {
        return "TrieStatistics{" +
                "entryCount=" + entryCount +
                ", branchCount=" + branchCount +
                ", extensionCount=" + extensionCount +
                ", maxDepth=" + maxDepth +
                ", totalNodes=" + totalNodes() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrieStatistics that = (TrieStatistics) o;
        return entryCount == that.entryCount &&
                branchCount == that.branchCount &&
                extensionCount == that.extensionCount &&
                maxDepth == that.maxDepth;
    }

    @Override
    public int hashCode() {
        int result = entryCount;
        result = 31 * result + branchCount;
        result = 31 * result + extensionCount;
        result = 31 * result + maxDepth;
        return result;
    }
}
