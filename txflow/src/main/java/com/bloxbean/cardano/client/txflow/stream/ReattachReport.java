package com.bloxbean.cardano.client.txflow.stream;

import java.util.List;

/**
 * Outcome of a durable stream's restart re-attach pass (ADR 0004 Decision 5).
 *
 * <p>A durable stream, on {@link TxFlowStream#start()} or an explicit
 * {@link TxFlowStream#reattach()}, resolves its persisted non-terminal item
 * bindings against engine truth before opening for new work: bindings whose
 * engine snapshot is <em>present</em> are re-projected from that snapshot
 * (authoritative fast-forward — a crash-recovered item whose snapshot says
 * completed goes straight to confirmed) without re-running the transaction;
 * bindings whose snapshot is <em>absent</em> (the start never happened) are
 * re-dispatched from the persisted {@link TxStreamPlannedRecord}, idempotently,
 * under the same deterministic execution id. Items whose execution is still
 * running are surfaced {@link TxStreamItemStatus#RECOVERY_REQUIRED} rather than
 * masked as failed; this process refreshes them by read-through
 * ({@link TxFlowStream#getItemStatus(String)} / {@link TxFlowStream#reconcile
 * (String)}), not by live push watching, which is a later iteration.</p>
 *
 * @param reattachedItems items re-projected from a present engine snapshot
 *        (not re-dispatched), including those fast-forwarded to a terminal
 *        status and those surfaced recovery-required
 * @param redispatched items re-dispatched from a persisted plan because their
 *        engine snapshot was absent (the start never happened)
 * @param recoveryRequired items that settled
 *        {@link TxStreamItemStatus#RECOVERY_REQUIRED} during re-attach and need
 *        operator-driven reconciliation
 * @param reattachedItemIds item ids re-attached to an existing execution
 *        (present snapshot)
 */
public record ReattachReport(int reattachedItems, int redispatched, int recoveryRequired,
                             List<String> reattachedItemIds) {
    /**
     * Validates counts and defensively copies the id list.
     *
     * @param reattachedItems non-negative re-attached count
     * @param redispatched non-negative re-dispatched count
     * @param recoveryRequired non-negative recovery-required count
     * @param reattachedItemIds re-attached item ids; copied
     */
    public ReattachReport {
        if (reattachedItems < 0 || redispatched < 0 || recoveryRequired < 0) {
            throw new IllegalArgumentException("reattach counts cannot be negative");
        }
        reattachedItemIds = List.copyOf(reattachedItemIds != null ? reattachedItemIds : List.of());
    }

    /**
     * Returns a report describing an empty re-attach (nothing to recover).
     *
     * @return zeroed report
     */
    static ReattachReport empty() {
        return new ReattachReport(0, 0, 0, List.of());
    }
}
