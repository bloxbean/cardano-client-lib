package com.bloxbean.cardano.client.quicktx.signing;

import com.bloxbean.cardano.client.function.TxSigner;
import com.bloxbean.cardano.client.transaction.spec.Policy;
import com.bloxbean.cardano.hdwallet.Wallet;

import java.util.Optional;

/**
 * A binding between a reference and concrete signing capabilities.
 * Provides a way to construct a {@link TxSigner} for a given scope and
 * optionally expose a Wallet or a preferred address for payer/sender decisions.
 */
public interface SignerBinding {

    /**
     * Create a signer for a given scope.
     * Supported scopes include: "payment", "stake", "drep",
     * "committeeCold", "committeeHot", "policy".
     *
     * @param scope signer scope
     * @return a composed {@link TxSigner}
     * @throws IllegalArgumentException if the scope is unsupported by this binding
     */
    TxSigner signerFor(String scope);

    /**
     * Optional Wallet exposed by this binding, if applicable.
     * Useful for fee/collateral payer resolution when wallet-based UTXO search is preferred.
     */
    Optional<Wallet> asWallet();

    /**
     * Optional preferred base address (bech32) suggested by this binding.
     * Useful for sender/fee/collateral payer selection when an address is needed.
     */
    Optional<String> preferredAddress();

    /**
     * Optional native policy exposed by this binding.
     * Used by policy_ref minting when the transaction needs both policy script
     * material and the policy signer.
     */
    default Optional<Policy> asPolicy() {
        return Optional.empty();
    }
}
