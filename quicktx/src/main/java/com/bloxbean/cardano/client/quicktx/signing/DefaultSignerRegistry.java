package com.bloxbean.cardano.client.quicktx.signing;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.transaction.spec.Policy;
import com.bloxbean.cardano.hdwallet.Wallet;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default in-memory registry.
 * Allows registering accounts, wallets, and policies against reference strings.
 */
public class DefaultSignerRegistry implements SignerRegistry {

    private final Map<String, SignerBinding> bindings = new ConcurrentHashMap<>();

    public DefaultSignerRegistry addAccount(String ref, Account account) {
        bindings.put(ref, BasicSignerBinding.fromAccount(account));
        return this;
    }

    public DefaultSignerRegistry addWallet(String ref, Wallet wallet) {
        bindings.put(ref, BasicSignerBinding.fromWallet(wallet));
        return this;
    }

    public DefaultSignerRegistry addPolicy(String ref, Policy policy) {
        bindings.put(ref, BasicSignerBinding.fromPolicy(policy));
        return this;
    }

    public DefaultSignerRegistry addCustom(String ref, SignerBinding binding) {
        bindings.put(ref, binding);
        return this;
    }

    @Override
    public Optional<SignerBinding> resolve(String ref) {
        return Optional.ofNullable(bindings.get(ref));
    }
}

