package com.bloxbean.cardano.vds.core.api;

/**
 * Unified interface for managing verifiable data structure state across different storage backends.
 *
 * <p>This interface provides a storage-agnostic abstraction for state management of various
 * structures (MPT, JMT, and others), enabling polymorphic usage of different backends
 * (RocksDB, RDBMS, Redis, etc.) with a consistent API.</p>
 *
 * <p><b>Supported Storage Modes:</b></p>
 * <ul>
 *   <li><b>MULTI_VERSION:</b> Historical versioning with GC (refcount/mark-sweep)</li>
 *   <li><b>SINGLE_VERSION:</b> Snapshot-only mode with periodic cleanup</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * // Polymorphic usage - can swap RocksDB for Postgres
 * StateTrees trees = new RocksDbStateTrees("/path/to/db", StorageMode.SINGLE_VERSION);
 * // Can be used with any structure implementation
 * MerklePatriciaTrie mpt = new MerklePatriciaTrie(trees.nodeStore(), Blake2b256::digest);
 * JellyfishMerkleTree jmt = new JellyfishMerkleTree(trees.nodeStore(), Blake2b256::digest);
 *
 * // Perform operations on your chosen structure
 * mpt.put(key, value);
 * byte[] root = mpt.getRootHash();
 *
 * // Store root (method depends on storage mode)
 * if (trees.storageMode() == StorageMode.MULTI_VERSION) {
 *     long version = trees.putRootWithRefcount(root);
 * } else {
 *     trees.putRootSnapshot(root);
 * }
 * }</pre>
 *
 * <p><b>Lifecycle:</b> Implementations must properly manage backend resources.
 * Always use try-with-resources or ensure {@link #close()} is called.</p>
 *
 * @since 0.8.0
 * @see NodeStore
 * @see RootsIndex
 * @see StorageMode
 */
public interface StateTrees extends AutoCloseable {

    /**
     * Returns the NodeStore for persisting verifiable data structure nodes.
     *
     * <p>The NodeStore provides content-addressed storage where nodes are
     * identified by their hash. All structure implementations (MPT, JMT, etc.)
     * share the same NodeStore interface regardless of backend.</p>
     *
     * @return the NodeStore instance for this state trees
     */
    NodeStore nodeStore();

    /**
     * Returns the RootsIndex for managing versioned root hashes.
     *
     * <p>The RootsIndex provides versioned storage for data structure root hashes,
     * enabling historical state access and version management. The semantics
     * differ based on storage mode:</p>
     * <ul>
     *   <li><b>MULTI_VERSION:</b> Multiple versions stored, user controls version numbers</li>
     *   <li><b>SINGLE_VERSION:</b> Single version at index 0, overwritten on each commit</li>
     * </ul>
     *
     * @return the RootsIndex instance for this state trees
     */
    RootsIndex rootsIndex();

    /**
     * Returns the storage mode for this state trees instance.
     *
     * <p>The storage mode is immutable once a database is created. Attempting
     * to reopen an existing database with a different mode will fail with
     * {@link IllegalStateException}.</p>
     *
     * @return the storage mode (MULTI_VERSION or SINGLE_VERSION)
     */
    StorageMode storageMode();

    /**
     * Atomically stores a new root version with reference counting (MULTI_VERSION mode only).
     *
     * <p>This method performs two operations atomically:</p>
     * <ol>
     *   <li>Stores the root hash in the RootsIndex with an auto-assigned version number</li>
     *   <li>Increments reference counts for all nodes reachable from the root</li>
     * </ol>
     *
     * <p><b>Usage:</b></p>
     * <pre>{@code
     * long version = stateTrees.putRootWithRefcount(trie.getRootHash());
     * System.out.println("Stored at version: " + version);
     * }</pre>
     *
     * @param root the trie root hash to store (typically 32 bytes for Blake2b-256)
     * @return the assigned version number
     * @throws IllegalStateException if called in SINGLE_VERSION mode
     * @throws IllegalArgumentException if root is null or empty
     * @throws RuntimeException if storage operation fails
     */
    long putRootWithRefcount(byte[] root);

    /**
     * Stores a new root in snapshot mode, overwriting the previous root (SINGLE_VERSION mode only).
     *
     * <p>In snapshot mode, there is always exactly one root stored at version 0.
     * Each call to this method overwrites the previous root, creating orphaned nodes
     * that must be cleaned up periodically using {@link #cleanupOrphanedNodes(Object)}.</p>
     *
     * <p><b>Usage:</b></p>
     * <pre>{@code
     * stateTrees.putRootSnapshot(trie.getRootHash());
     *
     * // Periodically cleanup orphans
     * if (commitCount % 1000 == 0) {
     *     GcReport report = stateTrees.cleanupOrphanedNodes(new GcOptions());
     *     System.out.println("Deleted " + report.deleted + " orphaned nodes");
     * }
     * }</pre>
     *
     * @param root the trie root hash to store (typically 32 bytes for Blake2b-256)
     * @throws IllegalStateException if called in MULTI_VERSION mode
     * @throws IllegalArgumentException if root is null or empty
     * @throws RuntimeException if storage operation fails
     */
    void putRootSnapshot(byte[] root);

    /**
     * Retrieves the current root in snapshot mode (SINGLE_VERSION mode only).
     *
     * <p>This is a convenience method equivalent to {@code rootsIndex().get(0L)}.</p>
     *
     * @return the current root hash, or null if no root has been stored yet
     * @throws IllegalStateException if called in MULTI_VERSION mode
     * @throws RuntimeException if storage operation fails
     */
    byte[] getCurrentRoot();

    /**
     * Cleans up orphaned nodes in snapshot mode (SINGLE_VERSION mode only).
     *
     * <p>Runs mark-sweep garbage collection to remove nodes that are no longer
     * reachable from the current root. This should be called periodically to
     * prevent unbounded storage growth.</p>
     *
     * <p><b>Recommended Schedule:</b></p>
     * <ul>
     *   <li>High write rate: Every 100-1000 commits</li>
     *   <li>Low write rate: Daily or weekly</li>
     *   <li>Monitor disk usage to tune frequency</li>
     * </ul>
     *
     * <p><b>Note:</b> The GcOptions type is intentionally Object to avoid coupling
     * this interface to backend-specific GC option classes. Implementations should
     * document their specific options type.</p>
     *
     * @param options backend-specific GC options (cast to implementation's type)
     * @return a report containing GC statistics (deleted nodes, elapsed time, etc.)
     * @throws IllegalStateException if called in MULTI_VERSION mode
     * @throws Exception if GC operation fails
     */
    Object cleanupOrphanedNodes(Object options) throws Exception;

    /**
     * Closes the state trees instance and releases all backend resources.
     *
     * <p>This method should properly close database connections, file handles,
     * and any other resources held by the backend. After calling close(), no
     * further operations should be performed on this instance.</p>
     *
     * @throws Exception if an error occurs during close
     */
    @Override
    void close() throws Exception;
}
