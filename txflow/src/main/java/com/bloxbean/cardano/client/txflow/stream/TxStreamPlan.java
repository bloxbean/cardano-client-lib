package com.bloxbean.cardano.client.txflow.stream;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Planner output for one stream batch.
 */
@Getter
public final class TxStreamPlan {
    private final String batchId;
    private final List<TxStreamPlannedFlow> flows;

    /**
     * Create a stream plan.
     *
     * @param batchId generated batch id
     * @param flows generated bounded flows to execute
     */
    public TxStreamPlan(String batchId, List<TxStreamPlannedFlow> flows) {
        this.batchId = batchId;
        this.flows = Collections.unmodifiableList(new ArrayList<>(flows));
    }
}
