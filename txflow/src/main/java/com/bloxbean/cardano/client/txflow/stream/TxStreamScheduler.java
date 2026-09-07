package com.bloxbean.cardano.client.txflow.stream;

/**
 * Package-local timing seam for caller-thread TxStream waits.
 *
 * <p>This keeps system timing calls out of the public API and separate from
 * the execution scheduler in {@code txflow.exec}. It creates no worker or
 * timer and blocks only the caller invoking a documented blocking helper.</p>
 */
final class TxStreamScheduler {
    private TxStreamScheduler() {
    }

    static long monotonicNanos() {
        return System.nanoTime();
    }

    static void sleepNanos(long nanos) throws InterruptedException {
        long millis = nanos / 1_000_000L;
        int nanosRemainder = (int) (nanos % 1_000_000L);
        Thread.sleep(millis, nanosRemainder);
    }
}
