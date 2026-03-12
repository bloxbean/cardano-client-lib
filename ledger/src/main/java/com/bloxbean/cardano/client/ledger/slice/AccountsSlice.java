package com.bloxbean.cardano.client.ledger.slice;

import java.math.BigInteger;
import java.util.Optional;

/**
 * Provides access to stake account state during validation.
 * <p>
 * Defined in CCL for use by certificate and withdrawal validation rules.
 * Yaci provides a concrete implementation backed by its ledger state storage.
 */
public interface AccountsSlice {

    /**
     * Check if a stake credential is registered.
     *
     * @param credentialHash the stake credential hash (28 bytes, hex-encoded)
     * @return true if registered
     */
    boolean isRegistered(String credentialHash);

    /**
     * Get the current reward balance for a stake credential.
     *
     * @param credentialHash the stake credential hash (hex-encoded)
     * @return the reward balance, or empty if not registered
     */
    Optional<BigInteger> getRewardBalance(String credentialHash);

    /**
     * Get the deposit amount held for a stake credential.
     *
     * @param credentialHash the stake credential hash (hex-encoded)
     * @return the deposit amount, or empty if not registered
     */
    Optional<BigInteger> getDeposit(String credentialHash);
}
