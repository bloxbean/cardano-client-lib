package com.bloxbean.cardano.client.txflow.stream;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * Per-item projection cell: the single place where the item status transition
 * table is enforced.
 *
 * <p>Live projections advance only along the explicit transition edges, in
 * per-item sequence order. Authoritative projections — a terminal engine
 * result or a read-through re-baseline from the engine snapshot — may
 * fast-forward any non-final status directly to the engine-derived state
 * without visiting intermediate statuses; final statuses remain immutable in
 * both modes. A transaction hash, once present, is never dropped by a later
 * projection.</p>
 *
 * <p>The cell also owns the item's completion promise. The promise completes
 * exactly once, on the first settling projection (terminal or
 * {@code RECOVERY_REQUIRED}); a later recovery repair advances the live
 * projection without re-completing the promise.</p>
 */
final class ItemProjection {
    private final CompletableFuture<TxStreamItemResult> promise = new CompletableFuture<>();
    private final AtomicReference<TxStreamItemResult> current;
    private long sequence;

    ItemProjection(TxStreamItemResult initial) {
        this.current = new AtomicReference<>(initial);
        this.sequence = 1;
    }

    /** Creates a projection already settled at its initial result. */
    static ItemProjection settled(TxStreamItemResult terminal) {
        ItemProjection projection = new ItemProjection(terminal);
        projection.promise.complete(terminal);
        return projection;
    }

    /**
     * Creates a re-attached item's projection seeded at the per-item sequence
     * its durable projection last reached, so the first authoritative advance
     * writes at {@code storedSequence + 1} and dominates the pre-crash durable
     * projection's compare-and-swap (see
     * {@link TxStreamStateStore#lastProjectionSequence(String, String)}). A
     * settling seed ({@code RECOVERY_REQUIRED}) begins already-settled but
     * remains repairable to a final status; a non-settling seed keeps an open
     * promise for the re-attach or live dispatch to complete.
     *
     * @param seed the stored (or synthesized) projection to resume from
     * @param storedSequence the durable projection's last per-item sequence, so
     *        the next advance dominates it
     * @return seeded re-attach projection
     */
    static ItemProjection reattaching(TxStreamItemResult seed, long storedSequence) {
        ItemProjection projection = new ItemProjection(seed);
        projection.sequence = storedSequence;
        if (settles(seed.getStatus())) {
            projection.promise.complete(seed);
        }
        return projection;
    }

    TxStreamItemResult current() {
        return current.get();
    }

    CompletableFuture<TxStreamItemResult> promise() {
        return promise;
    }

    boolean isSettled() {
        return promise.isDone();
    }

    void completePromise(TxStreamItemResult result) {
        promise.complete(result);
    }

    /**
     * Attempts to advance the projection.
     *
     * @param target status to project
     * @param customize additional field changes applied on top of the prior snapshot
     * @param at projection timestamp
     * @param authoritative whether this projection re-baselines from engine truth
     * @return the applied snapshot with its per-item sequence and the status it
     *         advanced from, or {@code null} when the transition table (or
     *         final-state immutability) rejects it
     */
    synchronized Applied advance(TxStreamItemStatus target,
                                 UnaryOperator<TxStreamItemResult.Builder> customize,
                                 Instant at, boolean authoritative) {
        TxStreamItemResult prior = current.get();
        TxStreamItemStatus from = prior.getStatus();
        if (from == target) return null;
        boolean allowed = authoritative ? !isFinal(from) : allowsLive(from, target);
        if (!allowed) return null;
        TxStreamItemResult.Builder builder = prior.toBuilder().status(target).updatedAt(at);
        if (customize != null) {
            builder = customize.apply(builder);
        }
        TxStreamItemResult next = builder.build();
        if (next.getTransactionHash() == null && prior.getTransactionHash() != null) {
            next = next.toBuilder().transactionHash(prior.getTransactionHash()).build();
        }
        long appliedSequence = ++sequence;
        current.set(next);
        return new Applied(next, appliedSequence, from);
    }

    /**
     * Applied projection snapshot together with its per-item sequence and the
     * status it advanced from ({@code null} for the initial acceptance
     * snapshot).
     */
    record Applied(TxStreamItemResult result, long sequence, TxStreamItemStatus previous) {
    }

    static boolean isFinal(TxStreamItemStatus status) {
        return status == TxStreamItemStatus.CONFIRMED
                || status == TxStreamItemStatus.FAILED
                || status == TxStreamItemStatus.CANCELLED;
    }

    /** Whether the status settles the item promise. */
    static boolean settles(TxStreamItemStatus status) {
        return isFinal(status) || status == TxStreamItemStatus.RECOVERY_REQUIRED;
    }

    static boolean allowsLive(TxStreamItemStatus from, TxStreamItemStatus to) {
        switch (from) {
            case ACCEPTED:
                return to == TxStreamItemStatus.PLANNED
                        || to == TxStreamItemStatus.FAILED
                        || to == TxStreamItemStatus.CANCELLED;
            case PLANNED:
                return to == TxStreamItemStatus.SUBMITTED
                        || to == TxStreamItemStatus.FAILED
                        || to == TxStreamItemStatus.CANCELLED
                        || to == TxStreamItemStatus.RECOVERY_REQUIRED;
            case SUBMITTED:
                return to == TxStreamItemStatus.CONFIRMED
                        || to == TxStreamItemStatus.FAILED
                        || to == TxStreamItemStatus.CANCELLED
                        || to == TxStreamItemStatus.RECOVERY_REQUIRED;
            case RECOVERY_REQUIRED:
                return to == TxStreamItemStatus.CONFIRMED
                        || to == TxStreamItemStatus.FAILED
                        || to == TxStreamItemStatus.CANCELLED;
            default:
                return false;
        }
    }
}
