package com.bloxbean.cardano.client.ledger.slice;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Simple in-memory implementation of {@link PoolsSlice} backed by HashMaps.
 * <p>
 * Suitable for testing and single-transaction validation.
 */
public class SimplePoolsSlice implements PoolsSlice {
    private final Set<String> registeredPools;
    private final Map<String, Long> retirementEpochs;

    public SimplePoolsSlice(Set<String> registeredPools, Map<String, Long> retirementEpochs) {
        this.registeredPools = Set.copyOf(registeredPools);
        this.retirementEpochs = new HashMap<>(retirementEpochs);
    }

    @Override
    public boolean isRegistered(String poolId) {
        return registeredPools.contains(poolId);
    }

    @Override
    public long getRetirementEpoch(String poolId) {
        return retirementEpochs.getOrDefault(poolId, -1L);
    }
}
