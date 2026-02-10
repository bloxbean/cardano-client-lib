package com.bloxbean.cardano.client.txflow.exec.store;

import com.bloxbean.cardano.client.txflow.result.FlowStatus;

import java.util.List;
import java.util.Optional;

/**
 * Pluggable interface for persisting flow execution state.
 * <p>
 * FlowStateStore enables recovery after application restart by persisting
 * flow and transaction state to external storage (database, file, etc.).
 * <p>
 * Unlike {@link com.bloxbean.cardano.client.txflow.exec.registry.FlowRegistry} which
 * provides in-memory tracking of live FlowHandle objects, FlowStateStore persists
 * serializable state snapshots that survive application restarts.
 * <p>
 * <b>Implementations should be thread-safe</b> as multiple flows may update
 * state concurrently.
 * <p>
 * Applications typically implement this interface using JPA, Redis, or other
 * storage mechanisms. See the txflow documentation for example implementations.
 *
 * @see FlowStateSnapshot
 * @see StepStateSnapshot
 * @see RecoveryCallback
 */
public interface FlowStateStore {

    /**
     * Save the initial flow state when execution starts.
     * <p>
     * This should be called when a flow begins execution to capture
     * the initial state. Subsequent updates use {@link #updateTransactionState}.
     *
     * @param snapshot the flow state to save
     */
    void saveFlowState(FlowStateSnapshot snapshot);

    /**
     * Load all flows that are pending (need tracking or recovery).
     * <p>
     * This is typically called on application startup to find flows
     * that were in progress when the application shut down.
     * <p>
     * Should return flows with status IN_PROGRESS or PENDING that have
     * transactions in SUBMITTED, IN_BLOCK, or CONFIRMED states.
     *
     * @return list of pending flow snapshots
     */
    List<FlowStateSnapshot> loadPendingFlows();

    /**
     * Update the state of a specific transaction with full details.
     * <p>
     * This is called on key state transitions:
     * <ul>
     *     <li>When transaction is submitted (SUBMITTED)</li>
     *     <li>When transaction is seen in block (IN_BLOCK)</li>
     *     <li>When transaction reaches confirmation depth (CONFIRMED)</li>
     *     <li>When transaction is rolled back (ROLLED_BACK)</li>
     * </ul>
     * <p>
     * The details object contains additional tracking information such as
     * block height, confirmation depth, timestamps, and error messages.
     *
     * @param flowId the flow identifier
     * @param stepId the step identifier
     * @param txHash the transaction hash
     * @param details the transaction state with tracking details
     */
    void updateTransactionState(String flowId, String stepId, String txHash, TransactionStateDetails details);

    /**
     * Mark a flow as complete.
     * <p>
     * This is called when a flow finishes (success or failure).
     * The implementation may choose to delete the flow data or
     * retain it for audit purposes.
     *
     * @param flowId the flow identifier
     * @param status the final status (COMPLETED or FAILED)
     */
    void markFlowComplete(String flowId, FlowStatus status);

    /**
     * Get the current state of a specific flow.
     *
     * @param flowId the flow identifier
     * @return the flow state snapshot, or empty if not found
     */
    Optional<FlowStateSnapshot> getFlowState(String flowId);

    /**
     * Delete a flow from the store.
     * <p>
     * This is typically called after a flow is fully processed and
     * no longer needs to be tracked.
     *
     * @param flowId the flow identifier
     * @return true if the flow was deleted, false if not found
     */
    boolean deleteFlow(String flowId);

    /**
     * A no-op implementation that does not persist anything.
     * <p>
     * Useful for testing or when persistence is not needed.
     */
    FlowStateStore NOOP = new FlowStateStore() {
        @Override
        public void saveFlowState(FlowStateSnapshot snapshot) {}

        @Override
        public List<FlowStateSnapshot> loadPendingFlows() {
            return List.of();
        }

        @Override
        public void updateTransactionState(String flowId, String stepId,
                                           String txHash, TransactionStateDetails details) {}

        @Override
        public void markFlowComplete(String flowId, FlowStatus status) {}

        @Override
        public Optional<FlowStateSnapshot> getFlowState(String flowId) {
            return Optional.empty();
        }

        @Override
        public boolean deleteFlow(String flowId) {
            return false;
        }
    };
}
