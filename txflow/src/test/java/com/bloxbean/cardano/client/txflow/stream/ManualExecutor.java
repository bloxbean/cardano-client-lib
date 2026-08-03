package com.bloxbean.cardano.client.txflow.stream;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;

/**
 * Deterministic hand-driven executor: submitted tasks queue instead of
 * running, and the test decides exactly when — and in which order — they
 * execute. This makes claimed-but-not-started dispatch states and pump
 * wakeup ordering directly scriptable without sleeps.
 */
final class ManualExecutor implements Executor {
    private final ConcurrentLinkedDeque<Runnable> tasks = new ConcurrentLinkedDeque<>();

    @Override
    public void execute(Runnable command) {
        tasks.addLast(command);
    }

    /** Runs the oldest queued task; fails when none is queued. */
    void runNext() {
        Runnable task = tasks.pollFirst();
        if (task == null) {
            throw new IllegalStateException("no queued task to run");
        }
        task.run();
    }

    /** Runs the newest queued task; fails when none is queued. */
    void runLast() {
        Runnable task = tasks.pollLast();
        if (task == null) {
            throw new IllegalStateException("no queued task to run");
        }
        task.run();
    }

    /** Runs queued tasks — including newly spawned ones — until none remain. */
    void runAll() {
        Runnable task;
        while ((task = tasks.pollFirst()) != null) {
            task.run();
        }
    }
}
