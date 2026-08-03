package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.txflow.TxFlow;

import java.util.List;
import java.util.Objects;

/**
 * One engine execution produced by a {@link TxStreamPlanner} for a window:
 * the flow to run, the single lane it runs on, the flow-level idempotency
 * claim key, and the mapping from member items to the flow's steps.
 * <p>
 * The stream converts a planned execution mechanically into a
 * {@code FlowExecutionRequest}: the execution id is derived deterministically
 * from the claim ({@code stableId(namespace, idempotencyKey)}), the lane's
 * canonical spending identity is declared as the request's spending resource,
 * and each member item is bound write-ahead to {@code (executionId, stepId)}
 * before the engine is invoked.
 * <p>
 * Exactly one lane per execution (ADR 0004 Decision 2): a planned flow whose
 * member items belong to different lanes is rejected typed
 * {@code TXSTREAM_PLAN_CROSS_LANE}. Multi-item flows carry a flow-level claim
 * only (Decision 3): a redelivery of the exact same member set matches the
 * stored execution, but a single redelivered member landing in a
 * differently-composed window is a new claim — durable per-item exactly-once
 * requires the {@link TxStreamPlanner#perItem()} planner.
 *
 * @param flow flow definition to execute; its steps carry the members'
 *        transactions
 * @param laneName lane every member item of this execution belongs to
 * @param idempotencyKey flow-level claim key, derived deterministically from
 *        the member items' idempotency keys
 * @param items item-to-step mappings for every member of this execution
 */
public record PlannedExecution(TxFlow flow, String laneName, String idempotencyKey,
                               List<TxStreamPlannedItem> items) {
    /**
     * Validates presence and snapshots the member mappings.
     *
     * @param flow non-null flow definition
     * @param laneName non-null lane name
     * @param idempotencyKey non-null flow claim key
     * @param items member mappings, snapshotted immutably
     */
    public PlannedExecution {
        Objects.requireNonNull(flow, "flow");
        Objects.requireNonNull(laneName, "laneName");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        items = List.copyOf(items != null ? items : List.of());
    }
}
