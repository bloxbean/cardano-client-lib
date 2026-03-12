package com.bloxbean.cardano.client.ledger.slice;

/**
 * Provides access to stake pool state during validation.
 * <p>
 * Used by pool certificate validation rules (registration, retirement, cost checks).
 * Yaci provides a concrete implementation backed by its ledger state storage.
 */
public interface PoolsSlice {

    /**
     * Check if a pool is currently registered.
     *
     * @param poolId the pool ID (hex-encoded hash)
     * @return true if registered
     */
    boolean isRegistered(String poolId);

    /**
     * Get the epoch in which a pool is scheduled to retire, if any.
     *
     * @param poolId the pool ID (hex-encoded hash)
     * @return the retirement epoch, or -1 if not retiring
     */
    long getRetirementEpoch(String poolId);
}
