package com.bloxbean.cardano.client.cip.cip113;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.util.HexUtil;

import java.util.Optional;

/**
 * Smart-wallet address derivation.
 *
 * <p>A smart wallet is {@code (programmableLogicBase, ownerCredential)}: the payment part is
 * always the shared base script, and the stake part identifies the owner. The address is
 * therefore deterministic from the owner's own key — a wallet can display programmable
 * balances with an ordinary UTxO query and no indexer.</p>
 *
 * <p>The CIP allows the owner credential to come from either the user's payment key or their
 * stake key, and leaves the choice to the substandard. This class defaults to the
 * <b>payment</b> key, matching the CIP's own worked example, and offers the other explicitly.</p>
 */
public final class SmartWalletAddress {

    private SmartWalletAddress() {}

    /** Smart wallet for an explicit owner credential. */
    public static Address of(Cip113Deployment deployment, Credential owner) {
        return AddressProvider.getBaseAddress(
                deployment.programmableLogicBaseCredential(), owner, deployment.getNetwork());
    }

    /** Smart wallet keyed by the owner's payment credential — the CIP's default. */
    public static Address ofPaymentCredential(Cip113Deployment deployment, Address ownerAddress) {
        Credential owner = AddressProvider.getPaymentCredential(ownerAddress)
                .orElseThrow(() -> new Cip113Exception(
                        "No payment credential in address " + ownerAddress.getAddress()));
        return of(deployment, owner);
    }

    /** Smart wallet keyed by the owner's stake credential. */
    public static Address ofStakeCredential(Cip113Deployment deployment, Address ownerAddress) {
        Credential owner = AddressProvider.getDelegationCredential(ownerAddress)
                .orElseThrow(() -> new Cip113Exception(
                        "No delegation credential in address " + ownerAddress.getAddress()));
        return of(deployment, owner);
    }

    /**
     * True when this address is already a usable smart wallet of the given deployment.
     *
     * <p>Both halves matter. The base-script payment credential alone is not enough:
     * {@code assets.ak:collect_output_assets} requires {@code Some(Inline(..))} for the stake
     * credential, so an enterprise or pointer address carrying that payment credential would be
     * rejected on chain. Treating it as "already a smart wallet" and passing it through unchanged
     * would produce exactly that output.</p>
     */
    public static boolean isSmartWallet(Cip113Deployment deployment, Address address) {
        boolean baseScriptPayment = AddressProvider.getPaymentCredential(address)
                .map(c -> HexUtil.encodeHexString(c.getBytes())
                        .equalsIgnoreCase(deployment.getProgrammableLogicBaseHash()))
                .orElse(false);
        return baseScriptPayment && AddressProvider.getDelegationCredential(address).isPresent();
    }

    /** Owner credential of a UTxO sitting at a smart-wallet address. */
    public static Optional<Credential> ownerOf(Cip113Deployment deployment, Utxo utxo) {
        Address address = new Address(utxo.getAddress());
        if (!isSmartWallet(deployment, address)) return Optional.empty();
        return AddressProvider.getDelegationCredential(address);
    }
}
