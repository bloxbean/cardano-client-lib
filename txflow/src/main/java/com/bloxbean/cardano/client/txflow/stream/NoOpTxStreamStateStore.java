package com.bloxbean.cardano.client.txflow.stream;

import java.util.Optional;

/**
 * Stream state store that intentionally drops all updates.
 * <p>
 * This mirrors the existing bounded-flow no-op persistence pattern and is
 * useful when callers only need receipts for the lifetime of the process.
 */
final class NoOpTxStreamStateStore implements TxStreamStateStore {
    static final NoOpTxStreamStateStore INSTANCE = new NoOpTxStreamStateStore();

    private NoOpTxStreamStateStore() {
    }

    @Override
    public void recordItem(TxStreamItemResult itemResult) {
    }

    @Override
    public Optional<TxStreamItemResult> getItem(String streamId, String itemId) {
        return Optional.empty();
    }

    @Override
    public void recordBatch(TxStreamBatchResult batchResult) {
    }

    @Override
    public Optional<TxStreamBatchResult> getBatch(String streamId, String batchId) {
        return Optional.empty();
    }
}
