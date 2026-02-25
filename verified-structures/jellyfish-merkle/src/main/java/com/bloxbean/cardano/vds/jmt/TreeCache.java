package com.bloxbean.cardano.vds.jmt;

import com.bloxbean.cardano.vds.core.NibblePath;
import com.bloxbean.cardano.vds.jmt.store.JmtStore;

import java.util.*;

/**
 * Batch-local cache for Jellyfish Merkle Tree updates following Diem's architecture.
 *
 * <p>TreeCache manages in-memory state for one or more tree update transactions, enabling:
 * <ul>
 *   <li>Multiple operations within a transaction to see each other's changes</li>
 *   <li>Multiple transactions to be batched together before commit</li>
 *   <li>Proper tracking of nodes that become stale</li>
 *   <li>Efficient batch commits to underlying storage</li>
 * </ul>
 *
 * <h2>Three-Tier Lookup Strategy</h2>
 * When looking up a node, TreeCache checks:
 * <ol>
 *   <li><b>Staged nodes</b> ({@code nodeCache}): Current transaction's modifications - highest priority</li>
 *   <li><b>Frozen nodes</b> ({@code frozenCache}): Earlier uncommitted transactions - second priority</li>
 *   <li><b>Storage</b> ({@code store}): Persistent committed state - fallback only</li>
 * </ol>
 *
 * <h2>Batch Lifecycle</h2>
 * <pre>{@code
 * // 1. Create cache for a new batch
 * TreeCache cache = new TreeCache(store, baseVersion);
 *
 * // 2. Apply operations (put/delete)
 * for (update : updates) {
 *     applyUpdate(update, cache);
 * }
 *
 * // 3. Freeze transaction (for multi-transaction batches)
 * cache.freeze();
 *
 * // 4. Convert to commit batch and write to storage
 * TreeUpdateBatch batch = cache.toBatch();
 * store.commitBatch(batch);
 * }</pre>
 *
 * <p>This design ensures that all operations within a batch see consistent state,
 * regardless of the underlying storage implementation (in-memory or RocksDB).</p>
 *
 * @see <a href="https://github.com/diem/diem/blob/main/storage/jellyfish-merkle/src/tree_cache/mod.rs">Diem TreeCache</a>
 */
public class TreeCache {

    // ===== Mutable Batch-Local State =====

    /**
     * Nodes staged for the current transaction (mutable).
     * Maps nibble path → node. These nodes are not yet frozen or persisted.
     */
    private final Map<NibblePath, NodeEntry> nodeCache;

    /**
     * Count of new leaf nodes in the current transaction.
     */
    private int numNewLeaves;

    /**
     * Stale node indexes for the current transaction (mutable).
     * These are nodes that existed in storage or frozen cache but were deleted/replaced.
     */
    private final Set<NodeKey> staleNodeIndexCache;

    /**
     * Count of stale leaf nodes in the current transaction.
     */
    private int numStaleLeaves;

    // ===== Frozen Immutable State =====

    /**
     * Accumulated state from earlier transactions in this batch (immutable after freeze).
     */
    private final FrozenTreeCache frozenCache;

    // ===== Version Tracking =====

    /**
     * NodeKey of the current root node.
     */
    private NodeKey rootNodeKey;

    /**
     * The version of the next transaction to be applied.
     */
    private long nextVersion;

    // ===== Storage Reference =====

    /**
     * The base version this cache was created from.
     */
    private final long baseVersion;

    /**
     * Reference to underlying persistent storage (read-only from TreeCache perspective).
     */
    private final JmtStore store;

    /**
     * Creates a new TreeCache for batching operations starting at the specified version.
     *
     * @param store       the underlying storage layer
     * @param nextVersion the version for the next transaction (0 for genesis)
     */
    public TreeCache(JmtStore store, long nextVersion) {
        this.store = Objects.requireNonNull(store, "store");
        this.nextVersion = nextVersion;
        this.baseVersion = nextVersion > 0 ? nextVersion - 1 : 0;
        this.nodeCache = new HashMap<>();
        this.staleNodeIndexCache = new HashSet<>();
        this.frozenCache = new FrozenTreeCache();
        this.numNewLeaves = 0;
        this.numStaleLeaves = 0;

        // Initialize root node key
        this.rootNodeKey = initializeRootNodeKey(nextVersion);
    }

    /**
     * Initializes the root node key based on the target version.
     *
     * <p>For genesis (version 0), we start with an empty tree (no nodes).
     * The first insert will create a leaf at the root.
     * For other versions, the root is from the previous version.
     *
     * @param version the target version
     * @return the root node key
     */
    private NodeKey initializeRootNodeKey(long version) {
        if (version == 0) {
            // Genesis case: start with empty tree, no null node
            // The root will be created by the first insert
            return NodeKey.of(NibblePath.EMPTY, 0);
        } else {
            // Root is from previous version
            return NodeKey.of(NibblePath.EMPTY, version - 1);
        }
    }

    // ===== Core Operations =====

    /**
     * Retrieves a node using the three-tier lookup strategy.
     *
     * <p>Lookup order:
     * <ol>
     *   <li>Check {@code nodeCache} (current transaction) - uses path only since all nodes are at nextVersion</li>
     *   <li>Check {@code frozenCache} (earlier transactions in batch) - uses full NodeKey for version-specific lookup</li>
     *   <li>Query underlying storage - uses version from NodeKey parameter</li>
     * </ol>
     *
     * @param nodeKey the full node key (path + version) to retrieve
     * @return the node entry if found, empty otherwise
     */
    public Optional<NodeEntry> getNode(NodeKey nodeKey) {
        NibblePath path = nodeKey.path();
        long version = nodeKey.version();

        // 1. Check batch-local mutable cache first (current transaction)
        // Current transaction nodes are always at nextVersion, so we can optimize by checking
        // if the requested version matches. If it does, check path-only cache.
        if (version == nextVersion) {
            NodeEntry staged = nodeCache.get(path);
            if (staged != null) {
                return Optional.of(staged);
            }
        }

        // 2. Check frozen cache (earlier uncommitted transactions)
        // Frozen cache must use full NodeKey since it contains nodes from multiple versions
        NodeEntry frozen = frozenCache.getNode(nodeKey);
        if (frozen != null) {
            return Optional.of(frozen);
        }

        // 3. Fall back to persistent storage
        Optional<JmtStore.NodeEntry> storeEntry = store.getNode(version, path);
        return storeEntry.map(e -> new NodeEntry(e.nodeKey(), e.node()));
    }

    /**
     * Stages a new node in the current transaction.
     *
     * @param nodeKey the key for the node
     * @param node    the node to stage
     * @throws IllegalStateException if a node with this key already exists in the current transaction
     */
    public void putNode(NodeKey nodeKey, JmtNode node) {
        NibblePath path = nodeKey.path();
        if (nodeCache.containsKey(path)) {
            throw new IllegalStateException("Node with key " + nodeKey + " already exists in current transaction");
        }

        boolean isLeaf = (node instanceof JmtLeafNode);
        if (isLeaf) {
            numNewLeaves++;
        }

        nodeCache.put(path, new NodeEntry(nodeKey, node));
    }

    /**
     * Deletes a node using smart two-phase deletion logic.
     *
     * <p><b>Case 1</b>: Node exists in current {@code nodeCache} (just created in this transaction)
     * <ul>
     *   <li>Simply remove from cache (undo the insertion)</li>
     *   <li>Decrement leaf counter if it's a leaf</li>
     *   <li>No stale tracking needed (never persisted)</li>
     * </ul>
     *
     * <p><b>Case 2</b>: Node NOT in {@code nodeCache} (exists in frozen cache or storage)
     * <ul>
     *   <li>Add to {@code staleNodeIndexCache} for garbage collection</li>
     *   <li>Increment stale leaf counter if it's a leaf</li>
     * </ul>
     *
     * @param nodeKey the key of the node to delete
     * @param isLeaf  whether this is a leaf node (for statistics)
     */
    public void deleteNode(NodeKey nodeKey, boolean isLeaf) {
        NibblePath path = nodeKey.path();

        // If node is in current batch cache, just remove it (undo insertion)
        if (nodeCache.remove(path) != null) {
            if (isLeaf) {
                numNewLeaves--;
            }
            return;
        }

        // Otherwise, node must be in storage or frozen cache -> mark as stale
        boolean isNew = staleNodeIndexCache.add(nodeKey);
        if (!isNew) {
            throw new IllegalStateException("Node " + nodeKey + " marked stale twice");
        }
        if (isLeaf) {
            numStaleLeaves++;
        }
    }

    /**
     * Returns the current root node key.
     */
    public NodeKey getRootNodeKey() {
        return rootNodeKey;
    }

    /**
     * Updates the root node key (typically after a tree modification).
     */
    public void setRootNodeKey(NodeKey newRootKey) {
        this.rootNodeKey = Objects.requireNonNull(newRootKey, "newRootKey");
    }

    /**
     * Returns the next version number that will be used.
     */
    public long nextVersion() {
        return nextVersion;
    }

    /**
     * Freezes the current transaction state, moving it to the frozen cache.
     *
     * <p>After freezing:
     * <ul>
     *   <li>Current {@code nodeCache} is moved to {@code frozenCache}</li>
     *   <li>Current {@code staleNodeIndexCache} is moved to frozen stale index with version stamp</li>
     *   <li>Statistics are captured</li>
     *   <li>Counters are reset for next transaction</li>
     *   <li>Version is incremented</li>
     * </ul>
     *
     * <p>This enables multiple transactions to be batched together before final commit.
     *
     * <p><b>Note:</b> Root hash computation is done by the caller (JellyfishMerkleTree)
     * which has access to the proper hash function and commitment scheme.
     *
     * @param rootHash the computed root hash for this transaction
     */
    public void freeze(byte[] rootHash) {
        // 1. Store root hash
        frozenCache.addRootHash(rootHash);

        // 2. Capture statistics for this transaction
        NodeStats stats = new NodeStats(
                nodeCache.size(),
                numNewLeaves,
                staleNodeIndexCache.size(),
                numStaleLeaves
        );
        frozenCache.addNodeStats(stats);

        // 3. Move batch-local nodes to frozen cache (drain)
        frozenCache.addNodes(nodeCache);
        nodeCache.clear();

        // 4. Move stale indices to frozen cache with version stamp
        for (NodeKey staleKey : staleNodeIndexCache) {
            frozenCache.addStaleNodeIndex(new StaleNodeIndex(nextVersion, staleKey));
        }
        staleNodeIndexCache.clear();

        // 5. Reset counters for next transaction
        numNewLeaves = 0;
        numStaleLeaves = 0;

        // 6. Increment version for next transaction
        nextVersion++;
    }

    /**
     * Converts this TreeCache to a TreeUpdateBatch for storage commit.
     *
     * <p>This consumes the frozen cache and returns all accumulated nodes,
     * stale indices, and metadata for atomic commit to storage.
     *
     * @return the update batch
     */
    public TreeUpdateBatch toBatch() {
        return frozenCache.toBatch();
    }

    /**
     * Returns all root hashes from frozen transactions.
     *
     * @return list of root hashes (one per frozen transaction)
     */
    public List<byte[]> getRootHashes() {
        return frozenCache.getRootHashes();
    }


    // ===== Supporting Data Structures =====

    /**
     * Node entry containing both the key and the node itself.
     */
    public static final class NodeEntry {
        private final NodeKey nodeKey;
        private final JmtNode node;

        public NodeEntry(NodeKey nodeKey, JmtNode node) {
            this.nodeKey = Objects.requireNonNull(nodeKey, "nodeKey");
            this.node = Objects.requireNonNull(node, "node");
        }

        public NodeKey nodeKey() {
            return nodeKey;
        }

        public JmtNode node() {
            return node;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof NodeEntry)) return false;
            NodeEntry that = (NodeEntry) o;
            return nodeKey.equals(that.nodeKey) && node.equals(that.node);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nodeKey, node);
        }
    }

    /**
     * Statistics for a single transaction's modifications.
     */
    public static final class NodeStats {
        private final int newNodes;
        private final int newLeaves;
        private final int staleNodes;
        private final int staleLeaves;

        public NodeStats(int newNodes, int newLeaves, int staleNodes, int staleLeaves) {
            this.newNodes = newNodes;
            this.newLeaves = newLeaves;
            this.staleNodes = staleNodes;
            this.staleLeaves = staleLeaves;
        }

        public int newNodes() {
            return newNodes;
        }

        public int newLeaves() {
            return newLeaves;
        }

        public int staleNodes() {
            return staleNodes;
        }

        public int staleLeaves() {
            return staleLeaves;
        }

        @Override
        public String toString() {
            return String.format("NodeStats{newNodes=%d, newLeaves=%d, staleNodes=%d, staleLeaves=%d}",
                    newNodes, newLeaves, staleNodes, staleLeaves);
        }
    }

    /**
     * Indicates a node becomes stale since a specific version.
     */
    public static final class StaleNodeIndex implements Comparable<StaleNodeIndex> {
        private final long staleSinceVersion;
        private final NodeKey nodeKey;

        public StaleNodeIndex(long staleSinceVersion, NodeKey nodeKey) {
            this.staleSinceVersion = staleSinceVersion;
            this.nodeKey = Objects.requireNonNull(nodeKey, "nodeKey");
        }

        public long staleSinceVersion() {
            return staleSinceVersion;
        }

        public NodeKey nodeKey() {
            return nodeKey;
        }

        @Override
        public int compareTo(StaleNodeIndex other) {
            int cmp = Long.compare(this.staleSinceVersion, other.staleSinceVersion);
            if (cmp != 0) return cmp;
            return this.nodeKey.compareTo(other.nodeKey);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof StaleNodeIndex)) return false;
            StaleNodeIndex that = (StaleNodeIndex) o;
            return staleSinceVersion == that.staleSinceVersion && nodeKey.equals(that.nodeKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(staleSinceVersion, nodeKey);
        }
    }

    /**
     * Accumulated state from multiple frozen transactions.
     *
     * <p>This structure holds all nodes, stale indices, and metadata from
     * transactions that have been frozen but not yet committed to storage.
     *
     * <p><b>CRITICAL</b>: This cache MUST key by full {@link NodeKey} (version + path),
     * not just path, to support multiple frozen transactions that modify the same path
     * at different versions. This follows Diem's MVCC semantics and prevents data corruption.
     */
    private static final class FrozenTreeCache {
        /**
         * Nodes from all frozen transactions keyed by (version, path).
         * Sorted by NodeKey natural ordering for deterministic serialization.
         * This ensures nodes at the same path but different versions coexist correctly.
         */
        private final TreeMap<NodeKey, NodeEntry> nodeCache;

        /**
         * Stale node indices from all frozen transactions (sorted by version then key).
         */
        private final TreeSet<StaleNodeIndex> staleNodeIndexBatch;

        /**
         * Statistics for each frozen transaction (in order).
         */
        private final List<NodeStats> nodeStats;

        /**
         * Root hashes for each frozen transaction (in order).
         */
        private final List<byte[]> rootHashes;

        FrozenTreeCache() {
            this.nodeCache = new TreeMap<>();
            this.staleNodeIndexBatch = new TreeSet<>();
            this.nodeStats = new ArrayList<>();
            this.rootHashes = new ArrayList<>();
        }

        /**
         * Retrieves a node by its full NodeKey (version + path).
         *
         * @param nodeKey the full key including version
         * @return the node entry if found, null otherwise
         */
        NodeEntry getNode(NodeKey nodeKey) {
            return nodeCache.get(nodeKey);
        }

        /**
         * Adds nodes from the current transaction to frozen cache.
         * Converts path-keyed entries to NodeKey-keyed entries using their embedded version.
         *
         * @param nodes map of path → NodeEntry to freeze
         */
        void addNodes(Map<NibblePath, NodeEntry> nodes) {
            for (Map.Entry<NibblePath, NodeEntry> entry : nodes.entrySet()) {
                NodeEntry nodeEntry = entry.getValue();
                // Use the NodeKey embedded in the NodeEntry (which includes version)
                nodeCache.put(nodeEntry.nodeKey(), nodeEntry);
            }
        }

        void addStaleNodeIndex(StaleNodeIndex index) {
            staleNodeIndexBatch.add(index);
        }

        void addNodeStats(NodeStats stats) {
            nodeStats.add(stats);
        }

        void addRootHash(byte[] rootHash) {
            rootHashes.add(Arrays.copyOf(rootHash, rootHash.length));
        }

        List<byte[]> getRootHashes() {
            return new ArrayList<>(rootHashes);
        }

        TreeUpdateBatch toBatch() {
            return new TreeUpdateBatch(
                    new TreeMap<>(nodeCache),
                    new TreeSet<>(staleNodeIndexBatch),
                    new ArrayList<>(nodeStats)
            );
        }
    }

    /**
     * Batch of updates to be atomically committed to storage.
     *
     * <p>Contains all nodes and metadata from one or more frozen transactions.
     * Nodes are keyed by full NodeKey (version + path) to preserve MVCC semantics.
     */
    public static final class TreeUpdateBatch {
        private final TreeMap<NodeKey, NodeEntry> nodes;
        private final TreeSet<StaleNodeIndex> staleIndices;
        private final List<NodeStats> stats;

        TreeUpdateBatch(
                TreeMap<NodeKey, NodeEntry> nodes,
                TreeSet<StaleNodeIndex> staleIndices,
                List<NodeStats> stats) {
            this.nodes = nodes;
            this.staleIndices = staleIndices;
            this.stats = stats;
        }

        /**
         * Returns all nodes in this batch, keyed by full NodeKey (version + path).
         *
         * @return immutable map of NodeKey → NodeEntry
         */
        public Map<NodeKey, NodeEntry> nodes() {
            return Collections.unmodifiableMap(nodes);
        }

        public Set<StaleNodeIndex> staleIndices() {
            return Collections.unmodifiableSet(staleIndices);
        }

        public List<NodeStats> stats() {
            return Collections.unmodifiableList(stats);
        }
    }
}
