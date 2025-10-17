package com.bloxbean.cardano.client.quicktx.signing;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.function.TxSigner;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.transaction.spec.Policy;
import com.bloxbean.cardano.hdwallet.Wallet;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Basic SignerBinding for common signer sources: Account, Wallet, Policy.
 */
class BasicSignerBinding implements SignerBinding {

    private final Account account;
    private final Wallet wallet;
    private final Policy policy;

    private BasicSignerBinding(Account account, Wallet wallet, Policy policy) {
        this.account = account;
        this.wallet = wallet;
        this.policy = policy;
    }

    public static BasicSignerBinding fromAccount(Account account) {
        Objects.requireNonNull(account, "account");
        return new BasicSignerBinding(account, null, null);
    }

    public static BasicSignerBinding fromWallet(Wallet wallet) {
        Objects.requireNonNull(wallet, "wallet");
        return new BasicSignerBinding(null, wallet, null);
    }

    public static BasicSignerBinding fromPolicy(Policy policy) {
        Objects.requireNonNull(policy, "policy");
        return new BasicSignerBinding(null, null, policy);
    }

    @Override
    public TxSigner signerFor(String scope) {
        String normalized = scope == null ? "" : scope.trim().toLowerCase(Locale.ROOT);

        if (account != null) {
            switch (normalized) {
                case SignerScopes.PAYMENT:
                    return SignerProviders.signerFrom(account);
                case SignerScopes.STAKE:
                    return SignerProviders.stakeKeySignerFrom(account);
                case SignerScopes.DREP:
                    return SignerProviders.drepKeySignerFrom(account);
                case SignerScopes.COMMITTEE_COLD:
                    return SignerProviders.committeeColdKeySignerFrom(account);
                case SignerScopes.COMMITTEE_HOT:
                    return SignerProviders.committeeHotKeySignerFrom(account);
                default:
                    throw new IllegalArgumentException("Unsupported scope for account: " + scope);
            }
        }

        if (wallet != null) {
            switch (normalized) {
                case SignerScopes.PAYMENT:
                    return SignerProviders.signerFrom(wallet);
                case SignerScopes.STAKE:
                    return SignerProviders.stakeKeySignerFrom(wallet);
                default:
                    throw new IllegalArgumentException("Unsupported scope for wallet: " + scope);
            }
        }

        if (policy != null) {
            if (!SignerScopes.POLICY.equals(normalized))
                throw new IllegalArgumentException("Unsupported scope for policy: " + scope);
            return SignerProviders.signerFrom(policy); // default: sign with all policy keys
        }

        throw new IllegalStateException("No underlying signer source configured");
    }

    @Override
    public Optional<Wallet> asWallet() {
        return Optional.ofNullable(wallet);
    }

    @Override
    public Optional<String> preferredAddress() {
        if (wallet != null) {
            return Optional.ofNullable(wallet.getBaseAddressString(0));
        }
        if (account != null) {
            Address addr = account.getBaseAddress();
            if (addr != null) return Optional.ofNullable(addr.toBech32());
        }
        return Optional.empty();
    }
}
