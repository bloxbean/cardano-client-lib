package com.bloxbean.cardano.client.programmabletoken;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.programmabletoken.intent.ProgrammableBurnIntent;
import com.bloxbean.cardano.client.programmabletoken.intent.ProgrammableMintIntent;
import com.bloxbean.cardano.client.programmabletoken.intent.ProgrammableRegisterIntent;
import com.bloxbean.cardano.client.programmabletoken.intent.ProgrammableRegistryUpdateIntent;
import com.bloxbean.cardano.client.programmabletoken.intent.ProgrammableThirdPartyTransferIntent;
import com.bloxbean.cardano.client.programmabletoken.intent.ProgrammableTokenAsset;
import com.bloxbean.cardano.client.programmabletoken.intent.ProgrammableTransferIntent;
import com.bloxbean.cardano.client.programmabletoken.intent.ProgrammableUnfrackIntent;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.intent.PlutusDataValue;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.hdwallet.Wallet;

import java.util.List;

/**
 * Protocol-neutral authoring facade. Every verb records semantic data and performs no chain I/O.
 *
 * <p>The programmable-token verbs record typed intents that the registered protocol extension
 * materializes at build time. Everything else is an ordinary {@link Tx}: reference inputs added
 * with {@code readFrom(...)}, metadata, withdrawals and plain payments are applied as usual, and
 * the extension adds the protocol's own reference inputs (coordination UTxO, registry nodes,
 * global state, published scripts) on top of them.</p>
 *
 * <p>Inherited {@code Tx} methods return {@code Tx}, so keep the programmable-token verbs
 * together in a chain, or hold the {@code ProgrammableTokenTx} in a local variable, before
 * calling a core method.</p>
 */
public class ProgrammableTokenTx extends Tx {
    public static final String EXTENSION_ID = "programmable-token";

    @Override
    public ProgrammableTokenTx from(String sender) {
        super.from(sender);
        return this;
    }

    @Override
    public ProgrammableTokenTx from(Wallet sender) {
        super.from(sender);
        return this;
    }

    /** Transfer a programmable token to {@code receiver}'s smart wallet. */
    public ProgrammableTokenTx transfer(String receiver, Amount amount, PlutusData transferRedeemer) {
        return transfer(receiver, amount, transferRedeemer, null);
    }

    /**
     * Transfer a programmable token to {@code receiver}'s smart wallet, writing {@code inlineDatum}
     * on the receiving output. The datum is optional and bounded by the deployment's inline-datum
     * limit so the output remains seizable.
     */
    public ProgrammableTokenTx transfer(String receiver, Amount amount, PlutusData transferRedeemer,
                                        PlutusData inlineDatum) {
        require(receiver, "receiver");
        if (amount == null || amount.getUnit() == null || "lovelace".equals(amount.getUnit()))
            throw new IllegalArgumentException("A programmable-token amount is required");
        if (amount.getQuantity() == null || amount.getQuantity().signum() <= 0)
            throw new IllegalArgumentException("transfer quantity must be positive");
        requirePlutusData(transferRedeemer, "transfer redeemer");
        addIntention(ProgrammableTransferIntent.builder()
                .receiver(receiver).amount(amount)
                .transferRedeemer(PlutusDataValue.of(transferRedeemer))
                .inlineDatum(PlutusDataValue.ofNullable(inlineDatum)).build());
        return this;
    }

    public ProgrammableTokenTx mint(String policyId, String receiver, List<Asset> assets,
                                    PlutusData issuanceRedeemer, PlutusData inlineDatum) {
        return mint(ProgrammableTokenPolicyRef.policyId(policyId), receiver, assets,
                issuanceRedeemer, inlineDatum);
    }

    public ProgrammableTokenTx mint(ProgrammableTokenPolicyRef policy, String receiver,
                                    List<Asset> assets, PlutusData issuanceRedeemer,
                                    PlutusData inlineDatum) {
        if (policy == null) throw new IllegalArgumentException("policy is required");
        require(receiver, "receiver");
        requireAssets(assets, "mint");
        requirePlutusData(issuanceRedeemer, "issuance redeemer");
        addIntention(ProgrammableMintIntent.builder()
                .policy(policy).receiver(receiver).assets(assets.stream()
                        .map(ProgrammableTokenAsset::from).toList())
                .issuanceRedeemer(PlutusDataValue.of(issuanceRedeemer))
                .inlineDatum(PlutusDataValue.ofNullable(inlineDatum)).build());
        return this;
    }

    /**
     * Destroy part of the sender's supply of a programmable token.
     *
     * <p>A burn is a separate verb rather than a negative {@link #mint} quantity because it is a
     * different transaction: the holder's smart-wallet UTxOs are spent through the base script
     * under the token's <i>transfer</i> logic, the remainder returns to the smart wallet, and the
     * issuance policy additionally runs the token's <i>minting</i> logic — two authorizations
     * with two redeemers, which {@link BurnAuthorization} keeps distinct. A mint needs neither
     * an input nor the transfer logic, only a receiver.</p>
     */
    public ProgrammableTokenTx burn(String policyId, List<Asset> assets,
                                    BurnAuthorization authorization) {
        if (authorization == null) throw new IllegalArgumentException("authorization is required");
        requireAssets(assets, "burn");
        requirePlutusData(authorization.getTransferRedeemer(), "transfer redeemer");
        requirePlutusData(authorization.getIssuanceRedeemer(), "issuance redeemer");
        addIntention(ProgrammableBurnIntent.builder()
                .policy(ProgrammableTokenPolicyRef.policyId(policyId)).assets(assets.stream()
                        .map(ProgrammableTokenAsset::from).toList())
                .transferRedeemer(PlutusDataValue.of(authorization.getTransferRedeemer()))
                .issuanceRedeemer(PlutusDataValue.of(authorization.getIssuanceRedeemer())).build());
        return this;
    }

    public ProgrammableTokenTx thirdPartyTransfer(String holder, String receiver, Amount amount,
                                                   PlutusData thirdPartyRedeemer) {
        require(holder, "holder");
        require(receiver, "receiver");
        if (amount == null || amount.getUnit() == null || "lovelace".equals(amount.getUnit()))
            throw new IllegalArgumentException("A programmable-token amount is required");
        if (amount.getQuantity() == null || amount.getQuantity().signum() <= 0)
            throw new IllegalArgumentException("third-party transfer quantity must be positive");
        requirePlutusData(thirdPartyRedeemer, "third-party redeemer");
        addIntention(ProgrammableThirdPartyTransferIntent.builder()
                .holder(holder).receiver(receiver).amount(amount)
                .thirdPartyRedeemer(PlutusDataValue.of(thirdPartyRedeemer)).build());
        return this;
    }

    public ProgrammableTokenTx register(String name, ProgrammableTokenRegistration registration,
                                        PlutusData registrationRedeemer) {
        require(name, "registration name");
        if (registration == null) throw new IllegalArgumentException("registration is required");
        requirePlutusData(registrationRedeemer, "registration redeemer");
        addIntention(ProgrammableRegisterIntent.builder()
                .name(name).registration(registration)
                .registrationRedeemer(PlutusDataValue.of(registrationRedeemer)).build());
        return this;
    }

    public ProgrammableTokenTx updateRegistry(String policyId, ProgrammableTokenRegistryUpdate update,
                                              PlutusData authorization) {
        ProgrammableTokenPolicyRef.policyId(policyId);
        if (update == null) throw new IllegalArgumentException("update is required");
        requirePlutusData(authorization, "authorization");
        addIntention(ProgrammableRegistryUpdateIntent.builder()
                .policyId(policyId).update(update)
                .authorization(PlutusDataValue.of(authorization)).build());
        return this;
    }

    /**
     * Regroup one policy out of the sender's shared smart-wallet UTxOs into its own output.
     *
     * <p>A "fracked" UTxO holds several policies, so a restriction scoped to one of them locks
     * the others as well. Unfracking spends every smart-wallet UTxO that holds {@code policyId}
     * together with another asset, leaves everything else in place, and moves all of that
     * policy's tokens into one fresh single-policy output at the same smart wallet. The token's
     * unfracking hook authorises it with {@code authorization}; a token whose issuer never set a
     * hook cannot be unfracked. It must be the only operation in its transaction.</p>
     */
    public ProgrammableTokenTx unfrack(String policyId, PlutusData authorization) {
        ProgrammableTokenPolicyRef.policyId(policyId);
        requirePlutusData(authorization, "authorization");
        addIntention(ProgrammableUnfrackIntent.builder()
                .policyId(policyId).authorization(PlutusDataValue.of(authorization)).build());
        return this;
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }

    private static void requirePlutusData(PlutusData value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
    }

    private static void requireAssets(List<Asset> assets, String operation) {
        if (assets == null || assets.isEmpty())
            throw new IllegalArgumentException(operation + " assets are required");
        for (Asset asset : assets) {
            if (asset == null || asset.getValue() == null || asset.getValue().signum() <= 0)
                throw new IllegalArgumentException(operation + " asset quantities must be positive");
        }
    }
}
