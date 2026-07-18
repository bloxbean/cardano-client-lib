package com.bloxbean.cardano.client.txflow.stream;

import java.util.Optional;

/**
 * Stream-level state store for item, batch, and mapping status.
 * <p>
 * This store is separate from bounded-flow persistence. It tracks stream
 * concerns such as accepted item ids, item-to-batch mappings, item-to-flow
 * mappings, and terminal item status.
 */
public interface TxStreamStateStore {
    /**
     * Record the latest item result snapshot.
     *
     * @param itemResult item result snapshot
     */
    void recordItem(TxStreamItemResult itemResult);

    /**
     * Get the latest item result snapshot.
     *
     * @param streamId stream id
     * @param itemId item id
     * @return item result if present
     */
    Optional<TxStreamItemResult> getItem(String streamId, String itemId);

    /**
     * Record the latest batch result snapshot.
     *
     * @param batchResult batch result snapshot
     */
    void recordBatch(TxStreamBatchResult batchResult);

    /**
     * Get the latest batch result snapshot.
     *
     * @param streamId stream id
     * @param batchId batch id
     * @return batch result if present
     */
    Optional<TxStreamBatchResult> getBatch(String streamId, String batchId);

    /**
     * Create a thread-safe in-memory store.
     *
     * @return in-memory stream state store
     */
    static TxStreamStateStore inMemory() {
        return new InMemoryTxStreamStateStore();
    }

    /**
     * Create a no-op store that does not retain state.
     *
     * @return no-op stream state store
     */
    static TxStreamStateStore noop() {
        return NoOpTxStreamStateStore.INSTANCE;
    }
}
