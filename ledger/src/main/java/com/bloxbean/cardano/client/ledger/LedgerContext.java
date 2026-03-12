package com.bloxbean.cardano.client.ledger;

import com.bloxbean.cardano.client.api.model.ProtocolParams;
import com.bloxbean.cardano.client.ledger.slice.*;
import com.bloxbean.cardano.client.plutus.spec.CostMdls;
import com.bloxbean.cardano.client.spec.NetworkId;
import lombok.Builder;
import lombok.Getter;

/**
 * Provides all context needed for ledger rule validation:
 * protocol parameters, current slot, network identity, and state slices.
 */
@Getter
@Builder
public class LedgerContext {
    private ProtocolParams protocolParams;
    private long currentSlot;
    private long currentEpoch; // 0 = unknown → skip epoch-dependent checks
    private NetworkId networkId;

    // Cost models for scriptDataHash recomputation (needed for witness validation)
    private CostMdls costMdls;

    // State slices — CCL provides simple in-memory implementations;
    // Yaci provides storage-backed implementations for block validation.
    private UtxoSlice utxoSlice;
    private AccountsSlice accountsSlice;
    private PoolsSlice poolsSlice;
    private DRepsSlice drepsSlice;
    private CommitteeSlice committeeSlice;
    private ProposalsSlice proposalsSlice;
}
