package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.AssetUtil;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.util.Tuple;
import lombok.NonNull;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Tx extends AbstractTx<Tx> {

    private StakeTx stakeTx;

    private String sender;
    protected boolean senderAdded = false;

    /**
     * Create Tx
     */
    public Tx() {
        stakeTx = new StakeTx();
    }

    /**
     * Add a mint asset to the transaction. The newly minted asset will be transferred to the defined receivers in payToAddress methods.
     *
     * @param script Policy script
     * @param asset  Asset to mint
     * @return Tx
     */
    public Tx mintAssets(@NonNull NativeScript script, Asset asset) {
        return mintAssets(script, List.of(asset), null);
    }

    /**
     * Add a mint asset to the transaction. The newly minted asset will be transferred to the receiver address.
     *
     * @param script   Policy script
     * @param asset    Asset to mint
     * @param receiver Receiver address
     * @return Tx
     */
    public Tx mintAssets(@NonNull NativeScript script, Asset asset, String receiver) {
        return mintAssets(script, List.of(asset), receiver);
    }

    /**
     * Add mint assets to the transaction. The newly minted assets will be transferred to the defined receivers in payToAddress methods.
     *
     * @param script Policy script
     * @param assets List of assets to mint
     * @return Tx
     */
    public Tx mintAssets(@NonNull NativeScript script, List<Asset> assets) {
        return mintAssets(script, assets, null);
    }

    /**
     * Add mint assets to the transaction. The newly minted assets will be transferred to the receiver address.
     *
     * @param script   Policy script
     * @param assets   List of assets to mint
     * @param receiver Receiver address
     * @return Tx
     */
    public Tx mintAssets(@NonNull NativeScript script, List<Asset> assets, String receiver) {
        try {
            String policyId = script.getPolicyId();

            if (receiver != null) { //If receiver address is defined
                assets.forEach(asset -> {
                    payToAddress(receiver,
                            List.of(new Amount(AssetUtil.getUnit(policyId, asset), asset.getValue())), true);
                });
            }

            MultiAsset multiAsset = MultiAsset.builder()
                    .policyId(policyId)
                    .assets(assets)
                    .build();

            if (multiAssets == null)
                multiAssets = new ArrayList<>();

            multiAssets.add(new Tuple<>(script, multiAsset));
        } catch (Exception e) {
            throw new TxBuildException(e);
        }

        return this;
    }

    /**
     * Create Tx with a sender address. The application needs to provide the signer for this sender address.
     * A Tx object can have only one sender. This method should be called after all outputs are defined.
     *
     * @param sender
     * @return Tx
     */
    public Tx from(String sender) {
        verifySenderNotExists();
        this.sender = sender;
        this.senderAdded = true;
        return this;
    }

    /**
     * Create Tx with given utxos as inputs.
     * @param utxos List of utxos
     * @return Tx
     */
    public Tx collectFrom(List<Utxo> utxos) {
        this.inputUtxos = utxos;
        return this;
    }

    /**
     * Create Tx with given utxos as inputs.
     * @param utxos Set of utxos
     * @return Tx
     */
    public Tx collectFrom(Set<Utxo> utxos) {
        this.inputUtxos = List.copyOf(utxos);
        return this;
    }

    /**
     * Register stake address
     * @param address address to register. Address should have delegation credential. So it should be a base address or stake address.
     * @return Tx
     */
    public Tx registerStakeAddress(@NonNull String address) {
        stakeTx.registerStakeAddress(new Address(address));
        return this;
    }

    /**
     * Register stake address
     * @param address address to register. Address should have delegation credential. So it should be a base address or stake address.
     * @return Tx
     */
    public Tx registerStakeAddress(@NonNull Address address) {
        stakeTx.registerStakeAddress(address);
        return this;
    }

    /**
     * De-register stake address. The key deposit will be refunded to the change address or fee payer if change address is not specified
     * @param address address to de-register. Address should have delegation credential. So it should be a base address or stake address.
     * @return Tx
     */
    public Tx deregisterStakeAddress(@NonNull String address) {
        stakeTx.deregisterStakeAddress(new Address(address), null, null);
        return this;
    }

    /**
     * De-register stake address. The key deposit will be refunded to the change address or fee payer if change address is not specified
     * @param address address to de-register. Address should have delegation credential. So it should be a base address or stake address.
     * @return Tx
     */
    public Tx deregisterStakeAddress(@NonNull Address address) {
        stakeTx.deregisterStakeAddress(address, null, null);
        return this;
    }

    /**
     * De-register stake address. The key deposit will be refunded to the refund address.
     * @param address address to de-register. Address should have delegation credential. So it should be a base address or stake address.
     * @param refundAddr refund address
     * @return T
     */
    public Tx deregisterStakeAddress(@NonNull String address, @NonNull String refundAddr) {
        stakeTx.deregisterStakeAddress(new Address(address), null, refundAddr);
        return this;
    }

    /**
     * De-register stake address. The key deposit will be refunded to the refund address.
     * @param address address to de-register. Address should have delegation credential. So it should be a base address or stake address.
     * @param refundAddr refund address
     * @return T
     */
    public Tx deregisterStakeAddress(@NonNull Address address, @NonNull String refundAddr) {
        stakeTx.deregisterStakeAddress(address, null, refundAddr);
        return this;
    }

    /**
     * Delegate stake address to a stake pool
     * @param address address to delegate. Address should have delegation credential. So it should be a base address or stake address.
     * @param poolId stake pool id Bech32 or hex encoded
     * @return ScriptTx
     */
    public Tx delegateTo(@NonNull String address, @NonNull String poolId) {
        stakeTx.delegateTo(new Address(address), poolId, null);
        return this;
    }

    /**
     * Delegate stake address to a stake pool
     * @param address address to delegate. Address should have delegation credential. So it should be a base address or stake address.
     * @param poolId stake pool id Bech32 or hex encoded
     * @return ScriptTx
     */
    public Tx delegateTo(@NonNull Address address, @NonNull String poolId) {
        stakeTx.delegateTo(address, poolId, null);
        return this;
    }

    /**
     * Withdraw rewards from a stake address
     * @param rewardAddress stake address to withdraw rewards from
     * @param amount  amount to withdraw
     * @return Tx
     */
    public Tx withdraw(@NonNull String rewardAddress, @NonNull BigInteger amount) {
        stakeTx.withdraw(new Address(rewardAddress), amount, null, null);
        return this;
    }

    /**
     * Withdraw rewards from a stake address
     * @param rewardAddress stake address to withdraw rewards from
     * @param amount amount to withdraw
     * @return Tx
     */
    public Tx withdraw(@NonNull Address rewardAddress, @NonNull BigInteger amount) {
        stakeTx.withdraw(rewardAddress, amount, null, null);
        return this;
    }

    /**
     * Withdraw rewards from a stake address
     * @param rewardAddress stake address to withdraw rewards from
     * @param amount amount to withdraw
     * @param receiver receiver address
     * @return Tx
     */
    public Tx withdraw(@NonNull String rewardAddress, @NonNull BigInteger amount, String receiver) {
        stakeTx.withdraw(new Address(rewardAddress), amount, null, receiver);
        return this;
    }

    /**
     * Withdraw rewards from a stake address
     * @param rewardAddress stake address to withdraw rewards from
     * @param amount amount to withdraw
     * @param receiver receiver address
     * @return Tx
     */
    public Tx withdraw(@NonNull Address rewardAddress, @NonNull BigInteger amount, String receiver) {
        stakeTx.withdraw(rewardAddress, amount, null, receiver);
        return this;
    }

    /**
     * Sender address
     *
     * @return String
     */
    String sender() {
        if (sender != null)
            return sender;
        else
            return null;
    }

    @Override
    protected String getChangeAddress() {
        if (changeAddress != null)
            return changeAddress;
        else if (sender != null)
            return sender;
        else
            throw new TxBuildException("No change address. " +
                    "Please define at least one of sender address or sender account or change address");
    }

    @Override
    protected String getFromAddress() {
        if (sender != null)
            return sender;
        else
            throw new TxBuildException("No sender address or sender account defined");
    }

    @Override
    protected void postBalanceTx(Transaction transaction) {

    }

    @Override
    protected void verifyData() {

    }

    @Override
    protected String getFeePayer() {
        if (sender != null)
            return sender;
        else
            return null;
    }

    @Override
    TxBuilder complete() {
       Tuple<List<StakeTx.PaymentContext>, TxBuilder> stakeBuildTuple =
               stakeTx.build(getFromAddress(), getChangeAddress());

        for (StakeTx.PaymentContext paymentContext: stakeBuildTuple._1) {
            payToAddress(paymentContext.getAddress(), paymentContext.getAmount());
        }

        TxBuilder txBuilder = super.complete();

        txBuilder = txBuilder.andThen(stakeBuildTuple._2);
        return txBuilder;
    }

    private void verifySenderNotExists() {
        if (senderAdded)
            throw new TxBuildException("Sender already added. Cannot add additional sender.");
    }
}
