package com.bloxbean.cardano.statetrees.api;

/**
 * Storage mode for state trees (applicable to all storage backends).
 *
 * <p>Determines whether the state tree stores multiple historical versions or operates
 * in snapshot mode with only the current state. This is a storage-agnostic concept
 * that applies to all backends (RocksDB, RDBMS, etc.).</p>
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
     * the previous root, creating orphaned nodes that must be periodically cleaned up.</p>
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
