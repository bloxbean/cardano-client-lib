package com.bloxbean.cardano.client.txflow.stream;

import java.util.Objects;

/**
 * One item-to-step mapping inside a {@link PlannedExecution}: the planner's
 * declaration that the work item {@code itemId} rides the planned flow's step
 * {@code stepId}.
 * <p>
 * The stream validates mappings mechanically: an item mapped twice (inside
 * one execution or across executions of one plan), a mapping naming an item
 * that is not part of the planned window, a step id absent from the planned
 * flow, or a flow step no item maps to all reject the whole plan typed
 * {@code TXSTREAM_PLAN_INVALID}.
 * <p>
 * Multiple items MAY share one {@code stepId} — this is the one form of
 * "duplicate" mapping the stream permits, and it is <em>transaction-granular
 * batching</em>: the {@link TxStreamPlanner#batching(BatchingOptions) batching}
 * planner merges several payment items into a single shared step, so all of them
 * ride one transaction and each is projected from that step's single outcome
 * (same status, same transaction hash). (An item mapped to two <em>different</em>
 * steps or flows is still a validation error — see above; only several items
 * sharing <em>one</em> step is legitimate.)
 * <p>
 * <b>Sharing a step is a planner-owned guarantee the stream cannot check.</b>
 * When a planner maps several items to one step, it OWNS the guarantee that that
 * step's transaction genuinely serves <em>every</em> item mapped to it. The
 * stream projects all of them from the one outcome and <em>cannot inspect the
 * transaction's semantics</em> to verify it pays each item — so a mis-mapped
 * shared step reports items {@code CONFIRMED} (with the step's hash) whose
 * payment never executed. The built-in {@code batching} planner is correct by
 * construction (it builds the merged transaction from exactly its mapped
 * members); a custom planner that shares steps must uphold the invariant itself.
 * See {@link TxStreamPlanner}.
 *
 * @param itemId caller-visible id of a window item
 * @param stepId id of the flow step carrying that item's transaction
 */
public record TxStreamPlannedItem(String itemId, String stepId) {
    /**
     * Validates the mapping identities.
     *
     * @param itemId non-null item identity
     * @param stepId non-null step identity
     */
    public TxStreamPlannedItem {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(stepId, "stepId");
    }
}
