package com.bloxbean.cardano.client.txflow.stream;

/**
 * Confirmed disposition of a write-ahead {@link TxStreamBinding} after the
 * engine start call returns.
 */
public enum BindingOutcome {
    /** The engine created a new execution for the binding's identity. */
    CREATED,
    /**
     * The engine matched an existing idempotency claim; the binding's
     * deterministic execution id names exactly that execution.
     */
    MATCHED,
    /** The engine rejected the request before creating an execution. */
    REJECTED
}
