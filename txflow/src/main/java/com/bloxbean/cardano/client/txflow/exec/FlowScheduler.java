package com.bloxbean.cardano.client.txflow.exec;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Run-scoped abstraction for wall time, monotonic elapsed time, and cooperative
 * delays used by TxFlow execution.
 *
 * <p>Keeping both operations behind one run-scoped dependency makes polling,
 * retry, rollback, and recovery behavior deterministic in tests. A scheduler
 * delays the calling task; it does not create a worker, select an executor, or
 * own thread lifecycle. Consequently execution remains compatible with whatever
 * platform- or virtual-thread executor the caller supplies.</p>
 */
interface FlowScheduler {

    /** Returns wall-clock time for timestamps and absolute deadlines. */
    Instant now();

    /** Monotonic time used only for elapsed-time budgets. */
    long monotonicNanos();

    /** Delays the calling task for the requested non-negative duration. */
    void sleep(Duration delay) throws InterruptedException;

    /**
     * Delays the calling task while honoring a cooperative cancellation signal.
     *
     * @return {@code true} when the delay completed without cancellation
     */
    default boolean sleep(Duration delay, BooleanSupplier cancelled) throws InterruptedException {
        Objects.requireNonNull(cancelled, "cancelled");
        if (cancelled.getAsBoolean()) return false;
        sleep(delay);
        return !cancelled.getAsBoolean();
    }

    /** Returns the production implementation backed by UTC and monotonic system time. */
    static FlowScheduler system() {
        return new SystemFlowScheduler(Clock.systemUTC());
    }
}

/**
 * Production scheduler that blocks only the calling task and checks
 * cancellation in short slices. It creates and owns no threads.
 */
final class SystemFlowScheduler implements FlowScheduler {
    private final Clock clock;

    SystemFlowScheduler(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Instant now() {
        return clock.instant();
    }

    @Override
    public long monotonicNanos() {
        return System.nanoTime();
    }

    @Override
    public void sleep(Duration delay) throws InterruptedException {
        Objects.requireNonNull(delay, "delay");
        if (delay.isNegative()) {
            throw new IllegalArgumentException("delay cannot be negative");
        }
        if (!delay.isZero()) {
            Thread.sleep(delay.toMillis());
        }
    }

    @Override
    public boolean sleep(Duration delay, BooleanSupplier cancelled) throws InterruptedException {
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(cancelled, "cancelled");
        if (delay.isNegative()) throw new IllegalArgumentException("delay cannot be negative");
        long remainingMillis = delay.toMillis();
        while (remainingMillis > 0) {
            if (cancelled.getAsBoolean()) return false;
            long slice = Math.min(remainingMillis, 50);
            Thread.sleep(slice);
            remainingMillis -= slice;
        }
        return !cancelled.getAsBoolean();
    }
}
