package com.bloxbean.cardano.client.txflow.exec.registry;

import com.bloxbean.cardano.client.txflow.exec.FlowHandle;
import com.bloxbean.cardano.client.txflow.result.FlowResult;
import com.bloxbean.cardano.client.txflow.result.FlowStatus;

/**
 * Listener for flow lifecycle events in a {@link FlowRegistry}.
 * <p>
 * This listener is notified of registry-level events:
 * <ul>
 *     <li>Flow registration and unregistration</li>
 *     <li>Flow status changes</li>
 *     <li>Flow completion (success or failure)</li>
 * </ul>
 * <p>
 * Unlike {@link com.bloxbean.cardano.client.txflow.exec.FlowListener} which tracks
 * individual flow execution events, this listener tracks flows across the entire registry.
 */
public interface FlowLifecycleListener {

    /**
     * Called when a flow is registered in the registry.
     *
     * @param flowId the flow identifier
     * @param handle the registered flow handle
     */
    default void onFlowRegistered(String flowId, FlowHandle handle) {}

    /**
     * Called when a flow is unregistered from the registry.
     *
     * @param flowId the flow identifier
     * @param handle the unregistered flow handle
     */
    default void onFlowUnregistered(String flowId, FlowHandle handle) {}

    /**
     * Called when a flow's status changes.
     *
     * @param flowId the flow identifier
     * @param handle the flow handle
     * @param oldStatus the previous status
     * @param newStatus the new status
     */
    default void onFlowStatusChanged(String flowId, FlowHandle handle,
                                     FlowStatus oldStatus, FlowStatus newStatus) {}

    /**
     * Called when a flow completes (successfully or with failure).
     * <p>
     * This is a convenience method that is called when the flow finishes,
     * regardless of whether it succeeded or failed.
     *
     * @param flowId the flow identifier
     * @param handle the flow handle
     * @param result the final result
     */
    default void onFlowCompleted(String flowId, FlowHandle handle, FlowResult result) {}

    /**
     * A no-op listener that ignores all events.
     */
    FlowLifecycleListener NOOP = new FlowLifecycleListener() {};
}
