package com.bloxbean.cardano.client.txflow.stream;

import java.util.List;
import java.util.Objects;

/**
 * Point-in-time view of one planning batch: the items of one closed window,
 * the executions the planner produced for them, and the batch status derived
 * from the member items.
 * <p>
 * The batch id is a stream-scoped monotonic counter ({@code "batch-N"}) and
 * is <b>observability metadata only — it is NEVER part of engine identity</b>:
 * execution ids, flow ids, and claim keys are derived exclusively from item
 * idempotency keys (batch sequence would make redelivered work
 * non-idempotent, which is exactly what the pre-v2 MVP got wrong).
 *
 * @param streamId stream that owns the batch
 * @param batchId stream-scoped monotonic batch id ({@code "batch-N"})
 * @param status batch status derived from the member items
 * @param itemIds member item ids, in window acceptance order
 * @param executionIds execution ids the plan produced; empty until the plan
 *        validated
 * @param failure typed planning failure when the whole window failed
 *        (planner threw, plan rejected), or {@code null}
 */
public record TxStreamBatchResult(String streamId, String batchId, TxStreamBatchStatus status,
                                  List<String> itemIds, List<String> executionIds,
                                  Throwable failure) {
    /**
     * Validates identity fields and snapshots the member lists.
     *
     * @param streamId non-null stream id
     * @param batchId non-null batch id
     * @param status non-null batch status
     * @param itemIds member item ids, snapshotted immutably
     * @param executionIds planned execution ids, snapshotted immutably
     * @param failure typed planning failure, or {@code null}
     */
    public TxStreamBatchResult {
        Objects.requireNonNull(streamId, "streamId");
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(status, "status");
        itemIds = List.copyOf(itemIds != null ? itemIds : List.of());
        executionIds = List.copyOf(executionIds != null ? executionIds : List.of());
    }
}
