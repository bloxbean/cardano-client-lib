package com.bloxbean.cardano.client.txflow.exec;

/**
 * Coarse-grained classification for an execution failure.
 *
 * <p>Categories are suitable for metrics and policy routing. Callers that need
 * a precise condition should use {@link FlowError#code()}.</p>
 */
public enum FlowErrorCategory {
    /** Invalid definition, binding, or request metadata. */
    VALIDATION,
    /** Operation rejected by server-owned safety policy. */
    POLICY,
    /** Execution capacity or spending-resource contention. */
    RESOURCE,
    /** Transaction construction failure before signing. */
    BUILD,
    /** Transaction signing failure. */
    SIGN,
    /** Transaction submission failure or unknown submission response. */
    SUBMISSION,
    /** Network transport failure. */
    NETWORK,
    /** Required backend service was unavailable. */
    BACKEND_UNAVAILABLE,
    /** Confirmation tracking failed or timed out. */
    CONFIRMATION,
    /** Confirmed transaction was authoritatively observed as rolled back. */
    ROLLBACK,
    /** Durable store or fencing failure. */
    PERSISTENCE,
    /** Execution requires explicit reconciliation before it can continue. */
    RECOVERY,
    /** Execution stopped after cooperative cancellation. */
    CANCELLATION,
    /** Unclassified runtime or invariant failure. */
    INTERNAL
}
