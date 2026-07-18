package com.bloxbean.cardano.client.txflow.stream;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe in-memory stream state store.
 * <p>
 * This implementation is suitable for tests and embedded/prototype use. It is
 * not durable across process restart.
 */
public final class InMemoryTxStreamStateStore implements TxStreamStateStore {
    private final ConcurrentMap<String, TxStreamItemResult> items = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TxStreamBatchResult> batches = new ConcurrentHashMap<>();

    /**
     * Create an empty in-memory stream state store.
     */
    public InMemoryTxStreamStateStore() {
    }

    @Override
    public void recordItem(TxStreamItemResult itemResult) {
        items.put(key(itemResult.getStreamId(), itemResult.getItemId()), itemResult);
    }

    @Override
    public Optional<TxStreamItemResult> getItem(String streamId, String itemId) {
        return Optional.ofNullable(items.get(key(streamId, itemId)));
    }

    @Override
    public void recordBatch(TxStreamBatchResult batchResult) {
        batches.put(key(batchResult.getStreamId(), batchResult.getBatchId()), batchResult);
    }

    @Override
    public Optional<TxStreamBatchResult> getBatch(String streamId, String batchId) {
        return Optional.ofNullable(batches.get(key(streamId, batchId)));
    }

    private String key(String streamId, String id) {
        return streamId + '\u0000' + id;
    }
}
