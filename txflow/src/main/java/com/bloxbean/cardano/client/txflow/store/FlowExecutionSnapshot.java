package com.bloxbean.cardano.client.txflow.store;

import com.bloxbean.cardano.client.txflow.exec.FlowExecutionState;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Latest durable projection of one flow execution.
 *
 * <p>{@code revision} is the optimistic-concurrency token used by
 * {@link FlowExecutionStore#append}. {@code lastSequence} is the event-journal tail, while
 * {@code compactedThroughSequence} records the inclusive prefix that is no longer available.
 * Definition and request fingerprints bind an idempotency claim to the exact definition and
 * semantically significant request that created it.</p>
 *
 * <p>The top-level {@code data} map is defensively copied and unmodifiable. Its values are not
 * deep-copied, so durable adapters and callers should store immutable value objects or make their
 * own defensive copies.</p>
 *
 * @param executionId unique execution identity
 * @param definitionFingerprint canonical fingerprint of the compiled flow definition
 * @param requestFingerprint canonical fingerprint of the execution request
 * @param state current execution lifecycle state
 * @param revision non-negative optimistic revision
 * @param lastSequence latest sequence allocated in the event journal
 * @param compactedThroughSequence inclusive journal-compaction watermark
 * @param updatedAt time of the latest snapshot transition
 * @param data durable execution data, such as bindings and attempt history
 */
public record FlowExecutionSnapshot(String executionId, String definitionFingerprint,
                                    String requestFingerprint, FlowExecutionState state,
                                    long revision, long lastSequence,
                                    long compactedThroughSequence,
                                    Instant updatedAt, Map<String, Object> data) {
    /**
     * Creates a validated snapshot with an immutable top-level data map.
     *
     * @param executionId non-blank unique execution identity
     * @param definitionFingerprint canonical fingerprint of the compiled flow definition
     * @param requestFingerprint canonical fingerprint of the execution request
     * @param state current execution lifecycle state
     * @param revision non-negative optimistic revision
     * @param lastSequence latest sequence allocated in the event journal
     * @param compactedThroughSequence inclusive journal-compaction watermark
     * @param updatedAt time of the latest snapshot transition
     * @param data durable execution data; the map is copied but its values are not deep-copied
     */
    public FlowExecutionSnapshot {
        if (executionId == null || executionId.isBlank()) throw new IllegalArgumentException("executionId cannot be blank");
        Objects.requireNonNull(definitionFingerprint, "definitionFingerprint");
        Objects.requireNonNull(requestFingerprint, "requestFingerprint");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (revision < 0 || lastSequence < 0 || compactedThroughSequence < 0
                || compactedThroughSequence > lastSequence) {
            throw new IllegalArgumentException("snapshot revisions and sequences are invalid");
        }
        data = Map.copyOf(data != null ? data : Map.of());
    }

    /**
     * Creates the value proposed for a state/data transition.
     *
     * <p>This helper deliberately retains the current revision and journal metadata. The store
     * assigns their committed values when the proposal is passed to
     * {@link FlowExecutionStore#append}.</p>
     *
     * @param nextState proposed lifecycle state
     * @param at transition time
     * @param nextData replacement durable data
     * @return an immutable transition value based on this snapshot
     */
    public FlowExecutionSnapshot withState(FlowExecutionState nextState, Instant at,
                                           Map<String, Object> nextData) {
        return new FlowExecutionSnapshot(executionId, definitionFingerprint, requestFingerprint,
                nextState, revision, lastSequence, compactedThroughSequence, at, nextData);
    }
}
