package com.bloxbean.cardano.client.ledger.slice;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Simple in-memory implementation of {@link DRepsSlice} backed by a HashMap.
 * <p>
 * Suitable for testing and single-transaction validation.
 */
public class SimpleDRepsSlice implements DRepsSlice {
    private final Map<String, BigInteger> deposits;

    public SimpleDRepsSlice(Map<String, BigInteger> deposits) {
        this.deposits = new HashMap<>(deposits);
    }

    @Override
    public boolean isRegistered(String drepCredentialHash) {
        return deposits.containsKey(drepCredentialHash);
    }

    @Override
    public Optional<BigInteger> getDeposit(String drepCredentialHash) {
        return Optional.ofNullable(deposits.get(drepCredentialHash));
    }
}
