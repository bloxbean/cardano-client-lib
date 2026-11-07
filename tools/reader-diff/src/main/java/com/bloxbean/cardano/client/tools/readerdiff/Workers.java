package com.bloxbean.cardano.client.tools.readerdiff;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** A fixed pool with a bounded backlog. The first failure of a task stops the run: it is thrown to the submitter. */
final class Workers implements AutoCloseable {
    @FunctionalInterface
    interface Task {
        void run() throws Exception;
    }

    private final ExecutorService pool;
    private final Semaphore backlog;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    Workers(int threads) {
        pool = Executors.newFixedThreadPool(threads);
        backlog = new Semaphore(threads * 4);
    }

    void submit(Task task) throws Exception {
        rethrow();
        backlog.acquire();
        pool.execute(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            } finally {
                backlog.release();
            }
        });
    }

    private void rethrow() throws Exception {
        if (failure.get() != null)
            throw new IllegalStateException("a worker failed", failure.get());
    }

    @Override
    public void close() throws Exception {
        pool.shutdown();
        pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        rethrow();
    }
}
