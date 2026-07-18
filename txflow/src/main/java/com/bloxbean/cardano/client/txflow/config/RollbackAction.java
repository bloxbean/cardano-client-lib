package com.bloxbean.cardano.client.txflow.config;

/**
 * Requested response after an authoritative rollback has been established.
 *
 * <p>This policy is separate from ordinary retry. In particular, an unknown
 * transaction observation is not a rollback and cannot authorize a rebuild.</p>
 */
public enum RollbackAction {
    /** End execution with a rollback-specific failure. */
    FAIL,
    /** Keep the same transaction identity and wait for it to be included again. */
    WAIT_FOR_REINCLUSION,
    /** Reconcile affected attempts and rebuild only work proven invalid. */
    RECONCILE_AND_REBUILD,
    /** Stop automated progress and leave execution available for explicit recovery. */
    PAUSE_FOR_RECOVERY
}
