package com.bloxbean.cardano.client.txflow.stream;

/**
 * Converts a window of accepted work into bounded {@code TxFlow} executions.
 * <p>
 * Planners are responsible for preserving item-to-step mappings so receipts and
 * status queries can map generated transaction results back to original work.
 */
public interface TxStreamPlanner {
    /**
     * Plan one accepted stream window.
     *
     * @param context stream planning context
     * @return executable stream plan
     */
    TxStreamPlan plan(TxStreamPlanningContext context);

    /**
     * Return the default MVP planner.
     *
     * @return planner that maps one item to one generated step
     */
    static TxStreamPlanner defaultPlanner() {
        return new DefaultTxStreamPlanner();
    }
}
