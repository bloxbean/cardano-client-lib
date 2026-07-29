package com.bloxbean.cardano.client.txflow.stream;

import java.util.Collection;

/**
 * Deterministic identity factory exposed to {@link TxStreamPlanner}s through
 * the {@link TxStreamPlanningContext}.
 * <p>
 * Identities are pure functions of the member items' idempotency keys —
 * derived from the stream's claim namespace and the <em>sorted</em> member
 * keys, never from batch sequence, window position, timestamps, or counters —
 * so the same items produce the same identities on every process and on every
 * redelivery.
 * <p>
 * <b>SPI obligation (ADR 0004 Decision 3):</b> custom planners are required
 * to derive every flow and step identity through this factory, and more
 * broadly to be fully deterministic — the same window items (in any order)
 * must produce a byte-identical plan: same flow ids, step ids, claim keys,
 * transaction content, and member mappings. The engine fingerprints the whole
 * compiled request under the flow's idempotency claim, so a non-deterministic
 * planner converts legitimate redeliveries into
 * {@code TXFLOW_IDEMPOTENCY_CONFLICT} failures instead of idempotent matches.
 */
public interface StableIdFactory {
    /**
     * Derives the deterministic flow identity for a planned execution from
     * its member items' idempotency keys. The keys are sorted internally, so
     * any iteration order yields the same identity.
     *
     * @param memberIdempotencyKeys idempotency keys of every member item of
     *        the planned flow; must be non-empty with non-blank elements
     * @return stable flow id
     */
    String flowId(Collection<String> memberIdempotencyKeys);

    /**
     * Derives the deterministic step identity carrying one member item's
     * transaction inside a planned flow.
     *
     * @param memberIdempotencyKey the member item's idempotency key
     * @return stable step id, unique per member key
     */
    String stepId(String memberIdempotencyKey);
}
