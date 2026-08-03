package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.txflow.store.ExecutionLease;
import com.bloxbean.cardano.client.txflow.store.FlowExecutionStore;
import com.bloxbean.cardano.client.txflow.store.MutationFence;
import com.bloxbean.cardano.client.txflow.store.ResourceLease;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Run-scoped guard for one execution lease and its spending-resource leases.
 *
 * <p>Lease state is synchronized and every mutation fence is created only after
 * checking renewal health. Renewal work is dispatched through the supplied
 * maintenance executor, which must remain independently schedulable while flow
 * execution is blocked. The guard does not own or shut down that executor, so
 * callers may choose platform or virtual-thread execution as appropriate.</p>
 *
 * <p>{@link #close()} is idempotent and releases resource leases in reverse
 * acquisition order, followed by the execution lease. Release failures caused
 * by an already stale or expired lease are intentionally ignored.</p>
 */
final class DurableLeaseGuard implements AutoCloseable {
    private final FlowExecutionStore store;
    private final Clock clock;
    private final Duration leaseDuration;
    private final Executor maintenanceExecutor;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean renewalStarted = new AtomicBoolean();
    private final AtomicReference<Throwable> renewalFailure = new AtomicReference<>();
    private ExecutionLease executionLease;
    private List<ResourceLease> resourceLeases = new ArrayList<>();

    DurableLeaseGuard(FlowExecutionStore store, Clock clock, Duration leaseDuration,
                      Executor maintenanceExecutor) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration");
        this.maintenanceExecutor = Objects.requireNonNull(maintenanceExecutor, "maintenanceExecutor");
        if (leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
    }

    /** Acquires the execution fence that must precede resource leases. */
    synchronized void acquireExecution(String executionId, String ownerToken) {
        executionLease = store.acquireExecutionLease(
                executionId, ownerToken, clock.instant(), leaseDuration);
    }

    /** Acquires and retains one spending-resource lease under this execution. */
    synchronized void acquireResource(String resourceId, String executionId, String ownerToken) {
        resourceLeases.add(store.acquireResourceLease(
                resourceId, executionId, ownerToken, clock.instant(), leaseDuration));
    }

    /**
     * Starts the idempotent renewal loop after the execution lease is acquired.
     * Renewal failures are retained and surfaced by {@link #checkHealthy()}.
     */
    void startRenewal() {
        if (renewalStarted.compareAndSet(false, true)) scheduleRenewal();
    }

    /** @return whether a lease-renewal operation has failed */
    boolean hasFailed() {
        return renewalFailure.get() != null;
    }

    /** Throws the first renewal failure, if any, before further durable work. */
    void checkHealthy() {
        Throwable failure = renewalFailure.get();
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure != null) {
            throw new FlowExecutionException("Durable lease renewal failed", failure);
        }
    }

    /** Returns the current execution/resource epochs for a fenced store write. */
    synchronized MutationFence fence() {
        checkHealthy();
        return new MutationFence(executionLease, resourceLeases);
    }

    private void scheduleRenewal() {
        long delayMillis = Math.max(1, leaseDuration.toMillis() / 3);
        Executor recordingExecutor = command -> {
            try {
                maintenanceExecutor.execute(() -> runRenewalTask(command));
            } catch (Throwable dispatchFailure) {
                recordRenewalFailure(dispatchFailure);
            }
        };
        try {
            CompletableFuture.delayedExecutor(delayMillis, TimeUnit.MILLISECONDS,
                    recordingExecutor).execute(() -> {
                if (closed.get()) return;
                try {
                    renew();
                } catch (Throwable failure) {
                    recordRenewalFailure(failure);
                    return;
                }
                if (!closed.get()) scheduleRenewal();
            });
        } catch (Throwable schedulingFailure) {
            recordRenewalFailure(schedulingFailure);
        }
    }

    private void runRenewalTask(Runnable command) {
        try {
            command.run();
        } catch (Throwable failure) {
            recordRenewalFailure(failure);
        }
    }

    private void recordRenewalFailure(Throwable failure) {
        renewalFailure.compareAndSet(null, failure);
    }

    private synchronized void renew() {
        if (closed.get()) return;
        executionLease = store.renewExecutionLease(
                executionLease, clock.instant(), leaseDuration);
        List<ResourceLease> renewed = new ArrayList<>(resourceLeases.size());
        for (ResourceLease lease : resourceLeases) {
            renewed.add(store.renewResourceLease(lease, clock.instant(), leaseDuration));
        }
        resourceLeases = renewed;
    }

    @Override
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) return;
        for (int i = resourceLeases.size() - 1; i >= 0; i--) {
            try {
                store.releaseResourceLease(resourceLeases.get(i));
            } catch (RuntimeException ignored) {
                // A stale/expired lease is already unavailable to this owner.
            }
        }
        if (executionLease != null) {
            try {
                store.releaseExecutionLease(executionLease);
            } catch (RuntimeException ignored) {
                // A stale/expired lease is already unavailable to this owner.
            }
        }
    }
}
