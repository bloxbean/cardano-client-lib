package com.bloxbean.cardano.client.ledger.slice;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple in-memory implementation of {@link ProposalsSlice} backed by a HashMap.
 * <p>
 * Keys are "txHash#index" strings. Suitable for testing and single-transaction validation.
 */
public class SimpleProposalsSlice implements ProposalsSlice {
    private final Map<String, String> proposals; // "txHash#index" → actionType

    public SimpleProposalsSlice(Map<String, String> proposals) {
        this.proposals = new HashMap<>(proposals);
    }

    @Override
    public boolean exists(String txHash, int index) {
        return proposals.containsKey(txHash + "#" + index);
    }

    @Override
    public String getActionType(String txHash, int index) {
        return proposals.get(txHash + "#" + index);
    }

    /**
     * Add a proposal to the slice.
     */
    public void addProposal(String txHash, int index, String actionType) {
        proposals.put(txHash + "#" + index, actionType);
    }
}
