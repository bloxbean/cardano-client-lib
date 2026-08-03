package com.bloxbean.cardano.client.txflow.stream;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Deterministic {@link ScheduledExecutorService} seam for window-age wakeups:
 * {@code schedule(Runnable, delay, unit)} records the task and its delay
 * instead of running anything; the test asserts the recorded delay and fires
 * the task manually. No threads, no timers, no real delays.
 */
final class ManualScheduler implements ScheduledExecutorService {
    final List<ScheduledTask> tasks = new CopyOnWriteArrayList<>();

    /** The single not-yet-fired, not-cancelled task; fails when ambiguous. */
    ScheduledTask pending() {
        List<ScheduledTask> live = tasks.stream()
                .filter(task -> !task.fired.get() && !task.cancelled.get())
                .toList();
        if (live.size() != 1) {
            throw new IllegalStateException("expected exactly one pending task, found " + live);
        }
        return live.get(0);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        ScheduledTask task = new ScheduledTask(command, unit.toMillis(delay));
        tasks.add(task);
        return task;
    }

    static final class ScheduledTask implements ScheduledFuture<Object> {
        final Runnable command;
        final long delayMillis;
        final AtomicBoolean cancelled = new AtomicBoolean();
        final AtomicBoolean fired = new AtomicBoolean();

        private ScheduledTask(Runnable command, long delayMillis) {
            this.command = command;
            this.delayMillis = delayMillis;
        }

        /** Fires the wakeup exactly as the scheduler thread would. */
        void fire() {
            if (!fired.compareAndSet(false, true)) {
                throw new IllegalStateException("task already fired");
            }
            command.run();
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(delayMillis, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(delayMillis, other.getDelay(TimeUnit.MILLISECONDS));
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return cancelled.compareAndSet(false, true);
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }

        @Override
        public boolean isDone() {
            return fired.get() || cancelled.get();
        }

        @Override
        public Object get() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return "ScheduledTask{delayMillis=" + delayMillis + ", fired=" + fired
                    + ", cancelled=" + cancelled + "}";
        }
    }

    // ------------------------------------------------------------------
    // Unused surface: the stream only calls schedule(Runnable, delay, unit).
    // ------------------------------------------------------------------

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay,
                                                  long period, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay,
                                                     long delay, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void shutdown() {
    }

    @Override
    public List<Runnable> shutdownNow() {
        return List.of();
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return true;
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<?> submit(Runnable task) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> List<Future<T>> invokeAll(java.util.Collection<? extends Callable<T>> tasks) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> List<Future<T>> invokeAll(java.util.Collection<? extends Callable<T>> tasks,
                                         long timeout, TimeUnit unit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T invokeAny(java.util.Collection<? extends Callable<T>> tasks)
            throws ExecutionException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T invokeAny(java.util.Collection<? extends Callable<T>> tasks,
                           long timeout, TimeUnit unit)
            throws ExecutionException, TimeoutException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void execute(Runnable command) {
        throw new UnsupportedOperationException();
    }
}
