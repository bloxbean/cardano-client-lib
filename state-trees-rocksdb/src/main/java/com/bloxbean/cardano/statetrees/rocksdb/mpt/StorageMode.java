package com.bloxbean.cardano.statetrees.rocksdb.mpt;

/**
 * Storage mode for MPT RocksDB state trees.
 *
 * <p>Determines whether the database stores multiple historical versions or operates
 * in snapshot mode with only the current state.</p>
 *
 * @author Bloxbean Project
 * @since 0.8.0
 */
public enum StorageMode {
    /**
     * Multi-version mode (default).
     *
     * <p>Stores multiple historical root versions with garbage collection support
     * (refcount or mark-sweep). Enables historical queries and rollback support.</p>
     *
     * <p><b>Use when:</b></p>
     * <ul>
     *   <li>Need rollback support for blockchain re-orgs</li>
     *   <li>Need to query historical states</li>
     *   <li>Willing to accept GC overhead for these features</li>
     * </ul>
     */
    MULTI_VERSION,

    /**
     * Single-version snapshot mode.
     *
     * <p>Stores only the current state (always at version 0). Each commit overwrites
     * the previous root, creating orphaned nodes that must be periodically cleaned up
     * via {@link RocksDbStateTrees#cleanupOrphanedNodes}.</p>
     *
     * <p><b>Use when:</b></p>
     * <ul>
     *   <li>Data is immutable after finalization (no rollback needed)</li>
     *   <li>High write throughput is critical</li>
     *   <li>Historical queries are not required</li>
     * </ul>
     *
     * <p><b>Valid examples:</b></p>
     * <ul>
     *   <li>Finalized UTxO state (blocks beyond rollback depth)</li>
     *   <li>Audit logs (append-only)</li>
     *   <li>Derived indexes (can rebuild from source)</li>
     * </ul>
     */
    SINGLE_VERSION
}