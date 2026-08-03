package com.bloxbean.cardano.client.txflow.exec;

import com.bloxbean.cardano.client.txflow.store.FlowStoreTextPolicy;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable event in one execution's ordered lifecycle journal.
 *
 * <p>Sequence numbers are strictly positive and ordered within an
 * {@code executionId}; they are not a global ordering across executions. The
 * optional step and transaction identifiers scope transaction-level events.</p>
 *
 * @param sequence sequence cursor within the execution
 * @param executionId execution that emitted the event
 * @param type lifecycle transition or observation
 * @param timestamp wall-clock time at which the event was recorded
 * @param stepId associated step, or {@code null} for an execution-wide event
 * @param transactionHash associated transaction hash, or {@code null}
 * @param details immutable event-specific attributes
 */
public record FlowEvent(long sequence, String executionId, FlowEventType type,
                        Instant timestamp, String stepId, String transactionHash,
                        Map<String, Object> details) {
    /**
     * Validates identifiers and snapshots the event-detail map.
     *
     * @param sequence sequence cursor within the execution
     * @param executionId execution that emitted the event
     * @param type lifecycle transition or observation
     * @param timestamp event record time
     * @param stepId associated step, or {@code null}
     * @param transactionHash associated transaction hash, or {@code null}
     * @param details event-specific attributes
     */
    public FlowEvent {
        if (sequence < 1) throw new IllegalArgumentException("event sequence must be positive");
        FlowStoreTextPolicy.requireIdentifier(executionId, "executionId",
                FlowStoreTextPolicy.MAX_EXECUTION_ID_BYTES);
        FlowStoreTextPolicy.requireOptionalText(stepId, "stepId",
                FlowStoreTextPolicy.MAX_STEP_ID_BYTES);
        FlowStoreTextPolicy.requireOptionalText(transactionHash, "transactionHash",
                FlowStoreTextPolicy.MAX_TRANSACTION_HASH_BYTES);
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(timestamp, "timestamp");
        details = Map.copyOf(details != null ? details : Map.of());
    }
}
