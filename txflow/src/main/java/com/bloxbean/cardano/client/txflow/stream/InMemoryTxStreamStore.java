package com.bloxbean.cardano.client.txflow.stream;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory {@link TxStreamStateStore}.
 * <p>
 * Registrations and bindings are keyed by item id; projections apply only when
 * their per-item sequence advances.
 */
final class InMemoryTxStreamStore implements TxStreamStateStore {
    private final Map<String, TxStreamItemRecord> records = new ConcurrentHashMap<>();
    private final Map<String, BindingEntry> bindings = new ConcurrentHashMap<>();
    private final Map<String, ProjectionEntry> projections = new ConcurrentHashMap<>();
    private final Map<String, TxStreamBatchResult> batches = new ConcurrentHashMap<>();

    @Override
    public void registerItem(TxStreamItemRecord record) {
        Objects.requireNonNull(record, "record");
        if (records.putIfAbsent(record.itemId(), record) != null) {
            throw new TxStreamDuplicateItemException(record.itemId(),
                    "Item is already registered: " + record.itemId());
        }
    }

    @Override
    public void bind(String itemId, TxStreamBinding binding) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(binding, "binding");
        if (!records.containsKey(itemId)) {
            throw new TxStreamException("TXSTREAM_ITEM_UNKNOWN",
                    "Cannot bind unregistered item: " + itemId);
        }
        bindings.put(itemId, new BindingEntry(binding, null));
    }

    @Override
    public void confirmBinding(String itemId, BindingOutcome outcome) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(outcome, "outcome");
        BindingEntry updated = bindings.computeIfPresent(itemId,
                (ignored, entry) -> new BindingEntry(entry.binding(), outcome));
        if (updated == null) {
            throw new TxStreamException("TXSTREAM_BINDING_MISSING",
                    "No binding recorded for item: " + itemId);
        }
    }

    @Override
    public boolean projectItem(TxStreamItemResult result, long sourceSequence) {
        Objects.requireNonNull(result, "result");
        boolean[] applied = new boolean[1];
        projections.compute(result.getItemId(), (ignored, existing) -> {
            if (existing != null && sourceSequence <= existing.sequence()) {
                return existing;
            }
            applied[0] = true;
            return new ProjectionEntry(result, sourceSequence);
        });
        return applied[0];
    }

    @Override
    public Optional<TxStreamItemResult> getItem(String streamId, String itemId) {
        ProjectionEntry entry = projections.get(itemId);
        if (entry == null || !entry.result().getStreamId().equals(streamId)) {
            return Optional.empty();
        }
        return Optional.of(entry.result());
    }

    @Override
    public Optional<Long> lastProjectionSequence(String streamId, String itemId) {
        ProjectionEntry entry = projections.get(itemId);
        if (entry == null || !entry.result().getStreamId().equals(streamId)) {
            return Optional.empty();
        }
        return Optional.of(entry.sequence());
    }

    @Override
    public void evictItem(String itemId) {
        records.remove(itemId);
        bindings.remove(itemId);
        projections.remove(itemId);
    }

    @Override
    public void recordBatch(TxStreamBatchResult batch) {
        Objects.requireNonNull(batch, "batch");
        batches.put(batch.batchId(), batch);
    }

    @Override
    public Optional<TxStreamBatchResult> getBatch(String streamId, String batchId) {
        TxStreamBatchResult batch = batches.get(batchId);
        if (batch == null || !batch.streamId().equals(streamId)) {
            return Optional.empty();
        }
        return Optional.of(batch);
    }

    @Override
    public void evictBatch(String batchId) {
        batches.remove(batchId);
    }

    private record BindingEntry(TxStreamBinding binding, BindingOutcome outcome) {
    }

    private record ProjectionEntry(TxStreamItemResult result, long sequence) {
    }
}
