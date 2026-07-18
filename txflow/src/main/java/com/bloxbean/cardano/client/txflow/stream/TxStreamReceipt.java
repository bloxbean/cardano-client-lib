package com.bloxbean.cardano.client.txflow.stream;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Per-item receipt returned when work is accepted by a stream.
 * <p>
 * The receipt exposes both a latest known result and a future that completes
 * when the item reaches a terminal state.
 */
public final class TxStreamReceipt {
    private final String streamId;
    private final String itemId;
    private final CompletableFuture<TxStreamItemResult> future = new CompletableFuture<>();
    private final AtomicReference<TxStreamItemResult> currentResult;

    TxStreamReceipt(String streamId, String itemId, TxStreamItemResult acceptedResult) {
        this.streamId = streamId;
        this.itemId = itemId;
        this.currentResult = new AtomicReference<>(acceptedResult);
    }

    /**
     * Return the stream id that accepted this item.
     *
     * @return stream id
     */
    public String getStreamId() {
        return streamId;
    }

    /**
     * Return the caller-visible item id.
     *
     * @return item id
     */
    public String getItemId() {
        return itemId;
    }

    /**
     * Return the latest known item status.
     *
     * @return latest status
     */
    public TxStreamItemStatus getStatus() {
        return currentResult.get().getStatus();
    }

    /**
     * Return the latest known item result.
     *
     * @return current result snapshot
     */
    public TxStreamItemResult getCurrentResult() {
        return currentResult.get();
    }

    /**
     * Return a future that completes when this item reaches a terminal status.
     *
     * @return terminal result future
     */
    public CompletableFuture<TxStreamItemResult> future() {
        return future;
    }

    /**
     * Check whether the item has reached a terminal status.
     *
     * @return true when the terminal future is complete
     */
    public boolean isDone() {
        return future.isDone();
    }

    /**
     * Wait for the terminal item result.
     *
     * @return terminal item result
     * @throws ExecutionException if the future completes exceptionally
     * @throws InterruptedException if interrupted while waiting
     */
    public TxStreamItemResult await() throws ExecutionException, InterruptedException {
        return future.get();
    }

    /**
     * Wait for the terminal item result up to a timeout.
     *
     * @param timeout maximum wait duration
     * @return terminal item result
     * @throws ExecutionException if the future completes exceptionally
     * @throws InterruptedException if interrupted while waiting
     * @throws TimeoutException if the timeout elapses
     */
    public TxStreamItemResult await(Duration timeout)
            throws ExecutionException, InterruptedException, TimeoutException {
        return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    void update(TxStreamItemResult result) {
        currentResult.set(result);
        if (result.isTerminal()) {
            future.complete(result);
        }
    }
}
