package com.bloxbean.cardano.client.txflow.config;

/**
 * Safety-aware action selected for a failed transaction attempt.
 *
 * <p>The actions distinguish reuse of a known transaction from construction
 * of a new one. That distinction is essential after submission because a new
 * transaction must not be built while the previous transaction might already
 * have been accepted.</p>
 */
public enum RetryAction {
    /** Retry using the already-known transaction identity rather than rebuilding it. */
    RETRY_SAME_TRANSACTION,
    /** Reconcile the known hash first, then retry only if reconciliation permits it. */
    RECONCILE_THEN_RETRY,
    /** Build a new attempt for the step; valid only when prior submission is ruled out. */
    REBUILD_STEP,
    /** Stop retrying and preserve the failure. */
    FAIL
}
