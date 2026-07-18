package com.bloxbean.cardano.client.txflow.stream;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Inputs provided to a planner for one stream batch.
 */
@Getter
public final class TxStreamPlanningContext {
    private final String streamId;
    private final String batchId;
    private final List<TxWorkItem> items;

    /**
     * Create a planning context.
     *
     * @param streamId stream id
     * @param batchId generated batch id
     * @param items accepted work items in this batch
     */
    public TxStreamPlanningContext(String streamId, String batchId, List<TxWorkItem> items) {
        this.streamId = streamId;
        this.batchId = batchId;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
    }
}
