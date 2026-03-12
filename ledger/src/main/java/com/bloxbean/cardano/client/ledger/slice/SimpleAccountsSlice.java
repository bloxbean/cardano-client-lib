package com.bloxbean.cardano.client.ledger.slice;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Simple in-memory implementation of {@link AccountsSlice} backed by HashMaps.
 * <p>
 * Suitable for testing and single-transaction validation.
 */
public class SimpleAccountsSlice implements AccountsSlice {
    private final Map<String, BigInteger> rewardBalances;
    private final Map<String, BigInteger> deposits;

    public SimpleAccountsSlice(Map<String, BigInteger> rewardBalances, Map<String, BigInteger> deposits) {
        this.rewardBalances = new HashMap<>(rewardBalances);
        this.deposits = new HashMap<>(deposits);
    }

    @Override
    public boolean isRegistered(String credentialHash) {
        return rewardBalances.containsKey(credentialHash);
    }

    @Override
    public Optional<BigInteger> getRewardBalance(String credentialHash) {
        return Optional.ofNullable(rewardBalances.get(credentialHash));
    }

    @Override
    public Optional<BigInteger> getDeposit(String credentialHash) {
        return Optional.ofNullable(deposits.get(credentialHash));
    }
}
