package com.bloxbean.cardano.vds.jmt.store;

/**
 * Access modes coordinated for one logical JMT namespace.
 */
public enum JmtAccessMode {
    /** Multi-node read operations such as proof generation and integrity traversal. */
    READ,
    /** Building and committing a new copy-on-write tree version. */
    UPDATE,
    /** Destructive lifecycle operations such as pruning and rollback. */
    MAINTENANCE
}
