package com.bloxbean.cardano.client.txflow.store;

import java.time.Instant;
import java.util.List;

/**
 * Durable recovery record for one transaction attempt of a flow step.
 *
 * <p>An attempt number distinguishes rebuilds of the same step. Once signed, the record carries
 * the exact payload and transaction hash needed to observe or safely resubmit identical bytes;
 * validity bounds and spent-input identities let recovery decide whether that is still safe.
 * Inclusion history is retained across rollback and re-inclusion rather than overwritten.</p>
 *
 * <p>The input and inclusion lists are defensively copied and unmodifiable. Nullable payload,
 * validity bounds, and error code allow the record to represent pre-signing, intentionally
 * unbounded, and non-error states respectively.</p>
 *
 * @param stepId step that owns the attempt
 * @param attemptNumber positive sequence number within the step
 * @param state latest durable attempt state
 * @param signedPayload exact signed bytes or an external reference; nullable before signing
 * @param validFromSlot lower validity bound, or {@code null} when absent
 * @param validToSlot upper validity bound, or {@code null} when absent
 * @param spentInputs canonical identities of transaction inputs consumed by this attempt
 * @param inclusions observed inclusion and rollback history
 * @param updatedAt time of the latest attempt transition
 * @param errorCode stable failure or recovery code, or {@code null} when none applies
 */
public record FlowAttemptSnapshot(String stepId, int attemptNumber, AttemptState state,
                                  SignedPayload signedPayload, Long validFromSlot, Long validToSlot,
                                  List<String> spentInputs, List<InclusionRecord> inclusions,
                                  Instant updatedAt, String errorCode) {
    /**
     * Creates an attempt snapshot with immutable input and inclusion collections.
     *
     * @param stepId step that owns the attempt
     * @param attemptNumber positive sequence number within the step
     * @param state latest durable attempt state
     * @param signedPayload exact signed bytes or an external reference; nullable before signing
     * @param validFromSlot lower validity bound, or {@code null} when absent
     * @param validToSlot upper validity bound, or {@code null} when absent
     * @param spentInputs canonical identities of transaction inputs consumed by this attempt
     * @param inclusions observed inclusion and rollback history
     * @param updatedAt time of the latest attempt transition
     * @param errorCode stable failure or recovery code, or {@code null} when none applies
     */
    public FlowAttemptSnapshot {
        spentInputs = List.copyOf(spentInputs != null ? spentInputs : List.of());
        inclusions = List.copyOf(inclusions != null ? inclusions : List.of());
    }
}
