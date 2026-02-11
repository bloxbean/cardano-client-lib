package com.bloxbean.cardano.client.txflow.exec.store;

/**
 * Callback interface for application-level recovery decisions.
 * <p>
 * When recovering pending flows after application restart, the application
 * may need to decide how to handle transactions that were submitted but
 * not yet confirmed. This callback allows the application to make those decisions.
 * <p>
 * The callback is invoked for each pending transaction during recovery,
 * allowing the application to decide whether to continue tracking,
 * skip, resubmit, or fail the flow.
 * <p>
 * Default implementations are provided: {@code CONTINUE_ALL}, {@code SKIP_ALL},
 * and {@code FAIL_ALL}.
 */
public interface RecoveryCallback {

    /**
     * Actions that can be taken for a pending transaction during recovery.
     */
    enum RecoveryAction {
        /**
         * Continue tracking the transaction for confirmation.
         * Use when the transaction is still pending in mempool.
         */
        CONTINUE_TRACKING,

        /**
         * Skip this transaction and mark it as failed.
         * Use when the transaction is no longer valid or needed.
         */
        SKIP,

        /**
         * Fail the entire flow.
         * Use when recovery is not possible or desirable.
         */
        FAIL_FLOW
    }

    /**
     * Called for each pending transaction during recovery.
     * <p>
     * The application should check the current state of the transaction
     * (e.g., is it in mempool, in a block, or gone?) and decide what action to take.
     *
     * @param flowSnapshot the flow state snapshot
     * @param stepSnapshot the step state snapshot with pending transaction
     * @return the action to take for this transaction
     */
    RecoveryAction onPendingTransaction(FlowStateSnapshot flowSnapshot, StepStateSnapshot stepSnapshot);

    /**
     * Called when a flow recovery is starting.
     * <p>
     * This is a notification callback that allows the application to prepare
     * for recovery (e.g., logging, metrics).
     *
     * @param flowSnapshot the flow being recovered
     */
    default void onRecoveryStarting(FlowStateSnapshot flowSnapshot) {}

    /**
     * Called when a flow recovery is complete.
     *
     * @param flowSnapshot the flow that was recovered
     * @param success true if recovery was successful
     * @param errorMessage error message if recovery failed, null otherwise
     */
    default void onRecoveryComplete(FlowStateSnapshot flowSnapshot, boolean success, String errorMessage) {}

    /**
     * A default callback that continues tracking all pending transactions.
     */
    RecoveryCallback CONTINUE_ALL = (flow, step) -> RecoveryAction.CONTINUE_TRACKING;

    /**
     * A default callback that skips all pending transactions.
     */
    RecoveryCallback SKIP_ALL = (flow, step) -> RecoveryAction.SKIP;

    /**
     * A default callback that fails all pending flows.
     */
    RecoveryCallback FAIL_ALL = (flow, step) -> RecoveryAction.FAIL_FLOW;
}
