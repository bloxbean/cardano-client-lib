package com.bloxbean.cardano.client.txflow.stream;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test double recording authoritative store calls (optionally into a shared
 * ordered call log) while delegating storage to the in-memory implementation.
 */
final class RecordingStateStore implements TxStreamStateStore {
    private final TxStreamStateStore delegate = TxStreamStateStore.inMemory();
    final List<String> calls;
    /** Batch projection writes, recorded apart from the authoritative call log. */
    final List<String> batchCalls = new CopyOnWriteArrayList<>();
    final Map<String, TxStreamBinding> bindings = new ConcurrentHashMap<>();
    final List<BindingOutcome> outcomes = new CopyOnWriteArrayList<>();
    volatile RuntimeException bindFailure;
    /** When set, bind fails only for this item id (any-member fail-closed tests). */
    volatile String bindFailureItemId;
    volatile RuntimeException registerFailure;
    volatile RuntimeException confirmFailure;
    /** When set, counted down as soon as registerItem is entered. */
    volatile CountDownLatch registerEntered;
    /** When set, registerItem blocks on this gate before proceeding. */
    volatile CountDownLatch registerGate;

    RecordingStateStore() {
        this(new CopyOnWriteArrayList<>());
    }

    RecordingStateStore(List<String> calls) {
        this.calls = calls;
    }

    @Override
    public void registerItem(TxStreamItemRecord record) {
        CountDownLatch entered = registerEntered;
        if (entered != null) entered.countDown();
        CountDownLatch gate = registerGate;
        if (gate != null) {
            try {
                if (!gate.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("register gate was never released");
                }
            } catch (InterruptedException interrupt) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupt);
            }
        }
        RuntimeException failure = registerFailure;
        if (failure != null) throw failure;
        delegate.registerItem(record);
        calls.add("register:" + record.itemId());
    }

    @Override
    public void bind(String itemId, TxStreamBinding binding) {
        RuntimeException failure = bindFailure;
        if (failure != null && (bindFailureItemId == null || bindFailureItemId.equals(itemId))) {
            throw failure;
        }
        delegate.bind(itemId, binding);
        bindings.put(itemId, binding);
        calls.add("bind:" + itemId);
    }

    @Override
    public void confirmBinding(String itemId, BindingOutcome outcome) {
        RuntimeException failure = confirmFailure;
        outcomes.add(outcome);
        calls.add("confirm:" + itemId + ":" + outcome);
        if (failure != null) throw failure;
        delegate.confirmBinding(itemId, outcome);
    }

    @Override
    public boolean projectItem(TxStreamItemResult result, long sourceSequence) {
        return delegate.projectItem(result, sourceSequence);
    }

    @Override
    public Optional<TxStreamItemResult> getItem(String streamId, String itemId) {
        return delegate.getItem(streamId, itemId);
    }

    @Override
    public void evictItem(String itemId) {
        delegate.evictItem(itemId);
        calls.add("evict:" + itemId);
    }

    @Override
    public void recordBatch(TxStreamBatchResult batch) {
        delegate.recordBatch(batch);
        batchCalls.add("batch:" + batch.batchId() + ":" + batch.status());
    }

    @Override
    public Optional<TxStreamBatchResult> getBatch(String streamId, String batchId) {
        return delegate.getBatch(streamId, batchId);
    }

    @Override
    public void evictBatch(String batchId) {
        delegate.evictBatch(batchId);
        batchCalls.add("evictBatch:" + batchId);
    }
}
