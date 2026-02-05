package com.bloxbean.cardano.client.txflow.exec.registry;

import com.bloxbean.cardano.client.txflow.exec.FlowHandle;
import com.bloxbean.cardano.client.txflow.result.FlowStatus;

import java.util.Collection;
import java.util.Optional;

/**
 * Registry for tracking active transaction flows in an application.
 * <p>
 * FlowRegistry provides centralized monitoring and management of running flows.
 * It tracks {@link FlowHandle} instances in-memory for fast lookup and status queries.
 * <p>
 * <b>Note:</b> FlowRegistry is in-memory only and does not survive application restarts.
 * For persistence across restarts, use FlowStateStore (when available).
 *
 * @see InMemoryFlowRegistry
 * @see FlowLifecycleListener
 */
public interface FlowRegistry {

    /**
     * Register a flow handle for tracking.
     * <p>
     * If a flow with the same ID already exists, it will be replaced.
     *
     * @param flowId the unique flow identifier
     * @param handle the flow handle to track
     */
    void register(String flowId, FlowHandle handle);

    /**
     * Unregister a flow from tracking.
     *
     * @param flowId the flow identifier to remove
     * @return the removed handle, or empty if not found
     */
    Optional<FlowHandle> unregister(String flowId);

    /**
     * Get a specific flow by ID.
     *
     * @param flowId the flow identifier
     * @return the flow handle if found
     */
    Optional<FlowHandle> getFlow(String flowId);

    /**
     * Get all registered flows.
     *
     * @return collection of all flow handles (never null)
     */
    Collection<FlowHandle> getAllFlows();

    /**
     * Get all flows that are currently running (status = IN_PROGRESS).
     *
     * @return collection of active flow handles
     */
    Collection<FlowHandle> getActiveFlows();

    /**
     * Get flows filtered by status.
     *
     * @param status the status to filter by
     * @return collection of flow handles with the given status
     */
    Collection<FlowHandle> getFlowsByStatus(FlowStatus status);

    /**
     * Get the count of registered flows.
     *
     * @return total number of flows in the registry
     */
    int size();

    /**
     * Get the count of active (running) flows.
     *
     * @return number of flows with IN_PROGRESS status
     */
    int activeCount();

    /**
     * Check if a flow is registered.
     *
     * @param flowId the flow identifier
     * @return true if the flow is registered
     */
    boolean contains(String flowId);

    /**
     * Clear all flows from the registry.
     */
    void clear();

    /**
     * Add a global lifecycle listener for all flows in this registry.
     * <p>
     * The listener will be notified when flows are registered, unregistered,
     * or change status.
     *
     * @param listener the listener to add
     */
    void addLifecycleListener(FlowLifecycleListener listener);

    /**
     * Remove a lifecycle listener.
     *
     * @param listener the listener to remove
     */
    void removeLifecycleListener(FlowLifecycleListener listener);
}
