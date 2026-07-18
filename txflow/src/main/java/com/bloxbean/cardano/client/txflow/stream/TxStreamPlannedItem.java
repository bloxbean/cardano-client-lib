package com.bloxbean.cardano.client.txflow.stream;

import lombok.Getter;

/**
 * Mapping between an accepted work item and a generated flow step.
 * <p>
 * Planners produce these mappings so execution results can be applied to the
 * correct {@link TxStreamReceipt}.
 */
@Getter
public final class TxStreamPlannedItem {
    private final String itemId;
    private final String stepId;

    /**
     * Create an item-to-step mapping.
     *
     * @param itemId accepted stream item id
     * @param stepId generated or original flow step id
     */
    public TxStreamPlannedItem(String itemId, String stepId) {
        this.itemId = itemId;
        this.stepId = stepId;
    }
}
