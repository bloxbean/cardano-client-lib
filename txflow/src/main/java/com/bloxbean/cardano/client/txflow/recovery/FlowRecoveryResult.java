package com.bloxbean.cardano.client.txflow.recovery;

import com.bloxbean.cardano.client.txflow.exec.FlowError;
import com.bloxbean.cardano.client.txflow.store.AttemptState;
import com.bloxbean.cardano.client.txflow.store.InclusionRecord;

/**
 * Outcome of reconciling one persisted transaction attempt.
 *
 * @param state reconciled attempt state; failed reconciliation is represented
 *              by {@link AttemptState#RECOVERY_REQUIRED}
 * @param transactionHash persisted transaction identity, when available
 * @param identicalPayloadResubmitted whether this recovery call submitted the
 *                                    verified persisted bytes unchanged
 * @param error typed explanation when further recovery is required
 * @param inclusion newly observed inclusion metadata, if the backend reported it
 */
public record FlowRecoveryResult(AttemptState state, String transactionHash,
                                 boolean identicalPayloadResubmitted, FlowError error,
                                 InclusionRecord inclusion) {
    /**
     * Creates a result without newly observed inclusion metadata.
     *
     * @param state reconciled attempt state
     * @param transactionHash persisted transaction identity
     * @param identicalPayloadResubmitted whether identical bytes were resubmitted
     * @param error recovery error, if any
     */
    public FlowRecoveryResult(AttemptState state, String transactionHash,
                              boolean identicalPayloadResubmitted, FlowError error) {
        this(state, transactionHash, identicalPayloadResubmitted, error, null);
    }
}
