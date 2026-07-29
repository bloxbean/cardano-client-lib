package com.bloxbean.cardano.client.txflow.exec;

/**
 * Durable lifecycle state of a TxFlow execution.
 *
 * <p>{@link #RECOVERY_REQUIRED} is intentionally distinct from
 * {@link #FAILED}: transaction state or durable ownership is uncertain and
 * must be reconciled before any rebuild or retry. {@link #PARTIALLY_COMPLETED}
 * indicates that at least one step succeeded before a later failure.</p>
 */
public enum FlowExecutionState {
    /** Execution record exists but work has not begun. */
    CREATED,
    /** Portable definition is being compiled and validated. */
    COMPILING,
    /** Execution is waiting for a spending resource. */
    QUEUED,
    /** Execution or explicit recovery is active. */
    RUNNING,
    /** Every required step completed successfully. */
    COMPLETED,
    /** At least one step completed before a later conclusive failure. */
    PARTIALLY_COMPLETED,
    /** Execution ended in a conclusive failure. */
    FAILED,
    /** Execution ended after an authoritative rollback outcome. */
    ROLLED_BACK,
    /** Execution ended after cooperative cancellation. */
    CANCELLED,
    /** State is uncertain and must be reconciled before retry or rebuild. */
    RECOVERY_REQUIRED
}
