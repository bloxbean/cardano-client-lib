package com.bloxbean.cardano.client.txflow.stream;

/**
 * Structured stream event callbacks.
 */
public interface TxStreamEventListener {
    /**
     * Listener instance that ignores every callback.
     */
    TxStreamEventListener NOOP = new TxStreamEventListener() {
    };

    /**
     * Called after an item has been accepted and its receipt has been created.
     *
     * @param item accepted work item
     * @param receipt receipt associated with the item
     */
    default void onItemAccepted(TxWorkItem item, TxStreamReceipt receipt) {
    }

    /**
     * Called whenever the latest item result changes.
     *
     * @param result latest item result snapshot
     */
    default void onItemUpdated(TxStreamItemResult result) {
    }

    /**
     * Called whenever the latest batch result changes.
     *
     * @param result latest batch result snapshot
     */
    default void onBatchUpdated(TxStreamBatchResult result) {
    }

    /**
     * Called after the stream worker has been started.
     *
     * @param streamId stream id
     */
    default void onStreamStarted(String streamId) {
    }

    /**
     * Called after an orderly drain has completed.
     *
     * @param streamId stream id
     */
    default void onStreamDrained(String streamId) {
    }

    /**
     * Called after the stream has closed its worker, source, and runner.
     *
     * @param streamId stream id
     */
    default void onStreamClosed(String streamId) {
    }
}
