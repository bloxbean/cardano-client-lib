package com.bloxbean.cardano.client.ledger.slice;

import java.math.BigInteger;
import java.util.Optional;

/**
 * Provides access to DRep (Delegated Representative) state during validation.
 * <p>
 * Used by DRep certificate and governance validation rules.
 * Yaci provides a concrete implementation backed by its ledger state storage.
 */
public interface DRepsSlice {

    /**
     * Check if a DRep is currently registered.
     *
     * @param drepCredentialHash the DRep credential hash (hex-encoded)
     * @return true if registered
     */
    boolean isRegistered(String drepCredentialHash);

    /**
     * Get the deposit held for a DRep.
     *
     * @param drepCredentialHash the DRep credential hash (hex-encoded)
     * @return the deposit amount, or empty if not registered
     */
    Optional<BigInteger> getDeposit(String drepCredentialHash);
}
