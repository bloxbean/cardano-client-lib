package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.txflow.config.FlowExecutionPolicy;
import com.bloxbean.cardano.client.txflow.config.SpendingContentionPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Deterministic, in-process serialization for canonical spending-resource
 * identities.
 *
 * <p>Resources are acquired in sorted order using fair locks, preventing
 * lock-order cycles between concurrent flows. Waiting happens on the calling
 * task and uses the supplied {@link FlowScheduler} only for a monotonic budget;
 * this coordinator owns no executor or thread. Cancellation is cooperative.</p>
 *
 * <p>These locks protect only executions in this engine process. Durable
 * resource leases provide fencing across cooperating processes, but neither
 * mechanism reserves an on-chain UTxO against an already partitioned worker.</p>
 */
final class SpendingResourceCoordinator {
    private final Map<String, LockEntry> locks = new ConcurrentHashMap<>();
    private final FlowScheduler scheduler;

    SpendingResourceCoordinator(FlowScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /**
     * Acquires the requested resources according to contention policy.
     * Callers must close the returned acquisition on every path.
     */
    Acquisition acquire(Set<String> resources, FlowExecutionPolicy policy,
                        boolean allowConcurrentSpending, AtomicBoolean cancelled) {
        if (allowConcurrentSpending
                || policy.getSpendingContention() == SpendingContentionPolicy.ALLOW) {
            return new Acquisition(this, List.of(), List.of(), false);
        }
        List<String> identities = resources.stream().sorted().toList();
        List<HeldLock> acquired = new ArrayList<>();
        for (String identity : identities) {
            LockEntry entry = locks.compute(identity, (ignored, existing) -> {
                LockEntry retained = existing != null ? existing : new LockEntry();
                retained.references++;
                return retained;
            });
            if (!acquire(entry.lock, policy, cancelled)) {
                releaseReference(identity, entry);
                close(acquired);
                if (cancelled.get()) return new Acquisition(this, List.of(), identities, true);
                throw new SpendingResourceBusyException(identity);
            }
            acquired.add(new HeldLock(identity, entry));
        }
        return new Acquisition(this, acquired, identities, false);
    }

    private boolean acquire(ReentrantLock lock, FlowExecutionPolicy policy,
                            AtomicBoolean cancelled) {
        if (policy.getSpendingContention() == SpendingContentionPolicy.REJECT) {
            return lock.tryLock();
        }
        long remainingNanos = policy.getMaxQueueWait().toNanos();
        long deadline = scheduler.monotonicNanos() + remainingNanos;
        try {
            do {
                if (cancelled.get()) return false;
                long slice = Math.min(remainingNanos, TimeUnit.MILLISECONDS.toNanos(100));
                if (lock.tryLock(Math.max(0, slice), TimeUnit.NANOSECONDS)) return true;
                remainingNanos = deadline - scheduler.monotonicNanos();
            } while (remainingNanos > 0);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancelled.set(true);
            return false;
        }
    }

    private void close(List<HeldLock> acquired) {
        for (int i = acquired.size() - 1; i >= 0; i--) acquired.get(i).unlock();
    }

    private void releaseReference(String identity, LockEntry entry) {
        locks.computeIfPresent(identity, (ignored, current) -> {
            if (current != entry) return current;
            current.references--;
            return current.references == 0 ? null : current;
        });
    }

    int retainedLockCount() {
        return locks.size();
    }

    private final class HeldLock {
        private final String identity;
        private final LockEntry entry;

        private HeldLock(String identity, LockEntry entry) {
            this.identity = identity;
            this.entry = entry;
        }

        private void unlock() {
            entry.lock.unlock();
            releaseReference(identity, entry);
        }
    }

    private static final class LockEntry {
        private final ReentrantLock lock = new ReentrantLock(true);
        // Updated only while the map's compute lock for this identity is held.
        private int references;
    }

    /**
     * Run-scoped, idempotently closeable ownership of the acquired lock set.
     */
    static final class Acquisition implements AutoCloseable {
        private final SpendingResourceCoordinator coordinator;
        private final List<HeldLock> locks;
        private final List<String> identities;
        private final boolean cancelled;

        private Acquisition(SpendingResourceCoordinator coordinator,
                            List<HeldLock> locks, List<String> identities,
                            boolean cancelled) {
            this.coordinator = coordinator;
            this.locks = new ArrayList<>(locks);
            this.identities = List.copyOf(identities);
            this.cancelled = cancelled;
        }

        List<String> identities() { return identities; }
        boolean cancelled() { return cancelled; }

        @Override
        public void close() {
            coordinator.close(locks);
            locks.clear();
        }
    }
}
