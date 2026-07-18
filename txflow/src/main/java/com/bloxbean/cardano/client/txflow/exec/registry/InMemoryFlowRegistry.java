package com.bloxbean.cardano.client.txflow.exec.registry;

import com.bloxbean.cardano.client.txflow.exec.FlowHandle;
import com.bloxbean.cardano.client.txflow.result.FlowResult;
import com.bloxbean.cardano.client.txflow.result.FlowStatus;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory implementation of {@link FlowRegistry}.
 * <p>
 * Features:
 * <ul>
 *     <li>Thread-safe using ConcurrentHashMap</li>
 *     <li>Optional auto-cleanup of completed flows</li>
 *     <li>Lifecycle listener support</li>
 *     <li>Status change tracking</li>
 * </ul>
 * <p>
 * Example:
 * <pre>{@code
 * // Basic usage
 * FlowRegistry registry = new InMemoryFlowRegistry();
 *
 * // With auto-cleanup (remove completed flows after 5 minutes)
 * FlowRegistry registry = InMemoryFlowRegistry.builder()
 *     .withAutoCleanup(Duration.ofMinutes(5))
 *     .withCleanupExecutor(applicationExecutor)
 *     .build();
 * }</pre>
 */
@Slf4j
public class InMemoryFlowRegistry implements FlowRegistry {

    private final ConcurrentMap<String, FlowHandle> flows = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, FlowStatus> lastKnownStatus = new ConcurrentHashMap<>();
    private final List<FlowLifecycleListener> listeners = new CopyOnWriteArrayList<>();
    private final Executor cleanupExecutor;
    private final Duration autoCleanupDelay;

    /**
     * Create a new InMemoryFlowRegistry with no auto-cleanup.
     */
    public InMemoryFlowRegistry() {
        this(null, null);
    }

    /**
     * Create a registry without auto-cleanup. A non-zero delay requires the
     * executor-accepting constructor so the application owns scheduling resources.
     *
     * @param autoCleanupDelay null or zero to disable cleanup
     * @deprecated use {@link #InMemoryFlowRegistry(Duration, Executor)} when enabling cleanup
     */
    @Deprecated
    public InMemoryFlowRegistry(Duration autoCleanupDelay) {
        this(autoCleanupDelay, null);
    }

    /**
     * Create a registry using an application-managed executor for delayed cleanup work.
     * The executor is never shut down by this registry, so Java 21 applications may pass
     * a virtual-thread executor.
     */
    public InMemoryFlowRegistry(Duration autoCleanupDelay, Executor cleanupExecutor) {
        this.autoCleanupDelay = autoCleanupDelay;
        if (autoCleanupDelay != null && !autoCleanupDelay.isZero()) {
            this.cleanupExecutor = Objects.requireNonNull(cleanupExecutor, "cleanupExecutor cannot be null");
        } else {
            this.cleanupExecutor = null;
        }
    }

    @Override
    public void register(String flowId, FlowHandle handle) {
        Objects.requireNonNull(flowId, "flowId cannot be null");
        Objects.requireNonNull(handle, "handle cannot be null");

        FlowHandle previous = flows.put(flowId, handle);
        FlowStatus currentStatus = handle.getStatus();
        lastKnownStatus.put(flowId, currentStatus);

        if (previous != null) {
            log.debug("Replaced existing flow: {}", flowId);
        }

        // Notify listeners
        notifyRegistered(flowId, handle);

        // Set up completion callback for auto-cleanup and status tracking
        setupCompletionTracking(flowId, handle);

        log.debug("Registered flow: {} (status: {})", flowId, currentStatus);
    }

    @Override
    public Optional<FlowHandle> unregister(String flowId) {
        FlowHandle removed = flows.remove(flowId);
        lastKnownStatus.remove(flowId);

        if (removed != null) {
            notifyUnregistered(flowId, removed);
            log.debug("Unregistered flow: {}", flowId);
        }

        return Optional.ofNullable(removed);
    }

    @Override
    public Optional<FlowHandle> getFlow(String flowId) {
        return Optional.ofNullable(flows.get(flowId));
    }

    @Override
    public Collection<FlowHandle> getAllFlows() {
        return Collections.unmodifiableCollection(new ArrayList<>(flows.values()));
    }

    @Override
    public Collection<FlowHandle> getActiveFlows() {
        return getFlowsByStatus(FlowStatus.IN_PROGRESS);
    }

    @Override
    public Collection<FlowHandle> getFlowsByStatus(FlowStatus status) {
        return flows.values().stream()
                .filter(handle -> handle.getStatus() == status)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public int size() {
        return flows.size();
    }

    @Override
    public int activeCount() {
        return (int) flows.values().stream()
                .filter(handle -> handle.getStatus() == FlowStatus.IN_PROGRESS)
                .count();
    }

    @Override
    public boolean contains(String flowId) {
        return flows.containsKey(flowId);
    }

    @Override
    public void clear() {
        List<String> flowIds = new ArrayList<>(flows.keySet());
        for (String flowId : flowIds) {
            unregister(flowId);
        }
        log.debug("Cleared all flows from registry");
    }

    @Override
    public void addLifecycleListener(FlowLifecycleListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeLifecycleListener(FlowLifecycleListener listener) {
        listeners.remove(listener);
    }

    /**
     * Clear the registry. Application-managed executors are not shut down.
     */
    public void shutdown() {
        clear();
    }

    private void setupCompletionTracking(String flowId, FlowHandle handle) {
        handle.getResultFuture().whenComplete((result, error) -> {
            if (result != null) {
                // Check for status change
                FlowStatus newStatus = handle.getStatus();
                FlowStatus oldStatus = lastKnownStatus.put(flowId, newStatus);
                if (oldStatus != null && oldStatus != newStatus) {
                    notifyStatusChanged(flowId, handle, oldStatus, newStatus);
                }

                // Notify completion
                notifyCompleted(flowId, handle, result);

                // Schedule auto-cleanup if enabled
                if (autoCleanupDelay != null && cleanupExecutor != null) {
                    CompletableFuture.delayedExecutor(autoCleanupDelay.toMillis(), TimeUnit.MILLISECONDS,
                            cleanupExecutor).execute(() -> {
                        if (flows.containsKey(flowId) && handle.isDone()) {
                            unregister(flowId);
                            log.debug("Auto-cleaned flow after completion: {}", flowId);
                        }
                    });
                }
            }
        });
    }

    private void notifyRegistered(String flowId, FlowHandle handle) {
        for (FlowLifecycleListener listener : listeners) {
            try {
                listener.onFlowRegistered(flowId, handle);
            } catch (Exception e) {
                log.warn("Listener error in onFlowRegistered: {}", e.getMessage());
            }
        }
    }

    private void notifyUnregistered(String flowId, FlowHandle handle) {
        for (FlowLifecycleListener listener : listeners) {
            try {
                listener.onFlowUnregistered(flowId, handle);
            } catch (Exception e) {
                log.warn("Listener error in onFlowUnregistered: {}", e.getMessage());
            }
        }
    }

    private void notifyStatusChanged(String flowId, FlowHandle handle,
                                     FlowStatus oldStatus, FlowStatus newStatus) {
        for (FlowLifecycleListener listener : listeners) {
            try {
                listener.onFlowStatusChanged(flowId, handle, oldStatus, newStatus);
            } catch (Exception e) {
                log.warn("Listener error in onFlowStatusChanged: {}", e.getMessage());
            }
        }
    }

    private void notifyCompleted(String flowId, FlowHandle handle, FlowResult result) {
        for (FlowLifecycleListener listener : listeners) {
            try {
                listener.onFlowCompleted(flowId, handle, result);
            } catch (Exception e) {
                log.warn("Listener error in onFlowCompleted: {}", e.getMessage());
            }
        }
    }

    /**
     * Create a builder for configuring InMemoryFlowRegistry.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for InMemoryFlowRegistry.
     */
    public static class Builder {
        private Duration autoCleanupDelay;
        private Executor cleanupExecutor;

        /**
         * Enable auto-cleanup of completed flows after the specified delay.
         *
         * @param delay time to wait after completion before removing
         * @return this builder
         */
        public Builder withAutoCleanup(Duration delay) {
            this.autoCleanupDelay = delay;
            return this;
        }

        /** Configure the application-managed executor used by delayed cleanup tasks. */
        public Builder withCleanupExecutor(Executor executor) {
            this.cleanupExecutor = Objects.requireNonNull(executor, "executor cannot be null");
            return this;
        }

        /**
         * Build the registry.
         *
         * @return a new InMemoryFlowRegistry
         */
        public InMemoryFlowRegistry build() {
            return new InMemoryFlowRegistry(autoCleanupDelay, cleanupExecutor);
        }
    }
}
