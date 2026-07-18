package com.bloxbean.cardano.client.txflow.exec;

/**
 * Events exposed by {@link FlowExecutionHandle} and, when configured, appended
 * to the durable execution journal.
 *
 * <p>The enum covers execution, step, transaction-confirmation, and recovery
 * lifecycles. Consumers should use each event's sequence as their resume
 * cursor and tolerate event types added in future releases.</p>
 */
public enum FlowEventType {
    /** Execution identity and initial record were created. */
    EXECUTION_CREATED,
    /** Portable definition compiled successfully. */
    COMPILATION_COMPLETED,
    /** Execution is waiting for spending-resource ownership. */
    EXECUTION_QUEUED,
    /** Execution acquired its required resources and began running. */
    EXECUTION_STARTED,
    /** A flow step began execution. */
    STEP_STARTED,
    /** Final signed bytes were prepared and, in durable mode, persisted. */
    TRANSACTION_PREPARED,
    /** Submission is about to cross the backend boundary. */
    TRANSACTION_SUBMITTING,
    /** Backend reported successful transaction submission. */
    TRANSACTION_SUBMITTED,
    /** Transaction was observed in a block. */
    TRANSACTION_IN_BLOCK,
    /** Observed confirmation depth changed. */
    CONFIRMATION_DEPTH_CHANGED,
    /** Transaction reached the configured confirmation target. */
    TRANSACTION_CONFIRMED,
    /** Transaction disappeared but absence was not yet authoritative. */
    TRANSACTION_ROLLBACK_SUSPECTED,
    /** Transaction rollback was authoritatively established. */
    TRANSACTION_ROLLED_BACK,
    /** Step reached successful completion. */
    STEP_COMPLETED,
    /** Step reached a failed outcome. */
    STEP_FAILED,
    /** All required flow work completed successfully. */
    EXECUTION_COMPLETED,
    /** Execution stopped with a conclusive failure. */
    EXECUTION_FAILED,
    /** Execution stopped after cooperative cancellation. */
    EXECUTION_CANCELLED,
    /** Explicit reconciliation of a persisted attempt began. */
    RECOVERY_STARTED,
    /** Explicit reconciliation produced a conclusive disposition. */
    RECOVERY_COMPLETED,
    /** Further operator or recovery-coordinator action is required. */
    RECOVERY_REQUIRED
}
