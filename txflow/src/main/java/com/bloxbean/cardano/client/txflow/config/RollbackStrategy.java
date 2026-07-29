package com.bloxbean.cardano.client.txflow.config;

/**
 * Legacy execution strategy for handling a transaction rollback.
 *
 * <p>New portable definitions should prefer {@link RollbackPolicy}. This enum
 * remains the canonical value type for the original executor API.</p>
 */
public enum RollbackStrategy {
    /** Fail the flow as soon as an authoritative rollback is detected. */
    FAIL_IMMEDIATELY,

    /** Notify listeners and continue monitoring for re-inclusion. */
    NOTIFY_ONLY,

    /** Reconcile the retained prefix and rebuild from the invalidated step. */
    REBUILD_FROM_FAILED,

    /** Reconcile the whole flow before rebuilding invalidated work. */
    REBUILD_ENTIRE_FLOW
}
