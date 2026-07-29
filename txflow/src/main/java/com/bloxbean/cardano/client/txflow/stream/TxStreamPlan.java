package com.bloxbean.cardano.client.txflow.stream;

import java.util.List;

/**
 * Output of one {@link TxStreamPlanner} invocation: the executions to run for
 * one closed window.
 * <p>
 * The stream validates the plan before dispatching anything (ADR 0004
 * Decision 6): duplicate or foreign item mappings and invalid flow claim keys
 * reject the whole plan typed {@code TXSTREAM_PLAN_INVALID}; a flow whose
 * items span lanes rejects it typed {@code TXSTREAM_PLAN_CROSS_LANE}; window
 * items the planner omitted fail typed {@code TXSTREAM_PLAN_OMITTED} while
 * the rest of the plan proceeds.
 *
 * @param executions planned executions, dispatched through the stream's
 *        per-lane FIFO scheduler
 */
public record TxStreamPlan(List<PlannedExecution> executions) {
    /**
     * Snapshots the executions immutably.
     *
     * @param executions planned executions; {@code null} is treated as empty
     */
    public TxStreamPlan {
        executions = List.copyOf(executions != null ? executions : List.of());
    }

    /**
     * Creates a plan over the given executions.
     *
     * @param executions planned executions
     * @return immutable plan
     */
    public static TxStreamPlan of(List<PlannedExecution> executions) {
        return new TxStreamPlan(executions);
    }
}
