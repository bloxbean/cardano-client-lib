package com.bloxbean.cardano.client.txflow.stream;

/**
 * Point-in-time stream counters derived from the same projections that back
 * item status, so the counters cannot disagree with receipts.
 * <p>
 * All item counters are cumulative and unaffected by retention eviction: an
 * evicted settled item keeps its contribution. {@code submittedItemCount}
 * counts transactions the chain actually saw. Each item contributes to exactly
 * one final bucket (confirmed/failed/cancelled) because final statuses are
 * immutable; {@code recoveryRequiredItemCount} reflects items <em>currently</em>
 * awaiting reconciliation — a repaired recovery-required item moves into its
 * final bucket instead of double-counting.
 * <p>
 * Because every counter is projection-derived rather than asserted, the
 * submitted count may trail the chain until a live read-through or terminal
 * projection observes the submission event, and an authoritative fast-forward
 * repair may skip intermediate hops (for example an item repaired straight to
 * {@code CONFIRMED} never increments the submitted counter).
 *
 * @param acceptedItemCount total items accepted by the stream
 * @param plannedItemCount items whose execution binding was recorded
 * @param submittedItemCount items whose transaction submission was observed
 * @param confirmedItemCount items that reached {@code CONFIRMED}
 * @param failedItemCount items that reached {@code FAILED}
 * @param cancelledItemCount items that reached {@code CANCELLED}
 * @param recoveryRequiredItemCount items currently awaiting reconciliation
 * @param pendingBufferSize accepted items waiting for dispatch across all lanes
 * @param inFlightCount executions currently running, bounded by the builder's
 *        {@code maxInFlight} and by one per canonical lane identity
 */
public record TxStreamStats(long acceptedItemCount,
                            long plannedItemCount,
                            long submittedItemCount,
                            long confirmedItemCount,
                            long failedItemCount,
                            long cancelledItemCount,
                            long recoveryRequiredItemCount,
                            int pendingBufferSize,
                            int inFlightCount) {
}
