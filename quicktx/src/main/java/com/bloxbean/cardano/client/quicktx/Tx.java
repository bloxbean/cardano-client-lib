package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.AssetUtil;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.cert.PoolRegistration;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import com.bloxbean.cardano.client.transaction.spec.governance.Vote;
import com.bloxbean.cardano.client.transaction.spec.governance.Voter;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovAction;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovActionId;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.util.Tuple;
import com.bloxbean.cardano.hdwallet.Wallet;
import lombok.NonNull;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

public class Tx extends AbstractTx<Tx> {

    private StakeTx stakeTx;
    private GovTx govTx;

    private String sender;
    protected boolean senderAdded = false;
    private Wallet senderWallet;

    /**
     * Create Tx
     */
    public Tx() {
        stakeTx = new StakeTx();
        govTx = new GovTx();
    }

    /**
     * Add a mint asset to the transaction. The newly minted asset will be transferred to the defined receivers in payToAddress methods.
     * <p>
     * This method can also be used to burn assets by passing a negative quantity.
     * </p>
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
     * <p>
     * This method can also be used to burn assets by passing a negative quantity.
     * </p>
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
                            List.of(new Amount(AssetUtil.getUnit(policyId, asset), asset.getValue())));
                });
            }

            addToMultiAssetList(script, assets);
        } catch (Exception e) {
            throw new TxBuildException(e);
        }

        return this;
    }

    /**
     * Create Tx with a sender address. The application needs to provide the signer for this sender address.
     * A Tx object can have only one sender. This method should be called after all outputs are defined.
     *
     * @param sender sender address
     * @return Tx
     */
    public Tx from(String sender) {
        verifySenderNotExists();
        this.sender = sender;
        this.senderAdded = true;
        return this;
    }

    public Tx from(Wallet sender) {
        verifySenderNotExists();
        this.senderWallet = sender;
        // TODO sender is not used in this scenarios, but it must be set to avoid breaking other things.
        this.sender = this.senderWallet.getBaseAddress(0).getAddress(); // TODO - is it clever to use the first address as sender here?
        this.changeAddress = this.sender;
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

    public Tx registerStakeAddress(@NonNull Wallet wallet) {
        stakeTx.registerStakeAddress(new Address(wallet.getStakeAddress()));
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

    public Tx deregisterStakeAddress(@NonNull Wallet wallet) {
        stakeTx.deregisterStakeAddress(new Address(wallet.getStakeAddress()), null, null);
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

    public Tx delegateTo(@NonNull Wallet wallet, @NonNull String poolId) {
        stakeTx.delegateTo(new Address(wallet.getStakeAddress()), poolId, null);
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
     * Register a stake pool
     * @param poolRegistration stake pool registration certificate
     * @return Tx
     */
    public Tx registerPool(@NonNull PoolRegistration poolRegistration) {
        stakeTx.registerPool(poolRegistration);
        return this;
    }

    /**
     * Update a stake pool
     * @param poolRegistration
     * @return Tx
     */
    public Tx updatePool(@NonNull PoolRegistration poolRegistration) {
        stakeTx.updatePool(poolRegistration);
        return this;
    }

    /**
     * Retire a stake pool
     * @param poolId stake pool id Bech32 or hex encoded
     * @param epoch epoch to retire the pool
     * @return Tx
     */
    public Tx retirePool(@NonNull String poolId, @NonNull int epoch) {
        stakeTx.retirePool(poolId, epoch);
        return this;
    }

    /**
     * Register a DRep
     * @param account Account
     * @param anchor Anchor
     * @return Tx
     */
    public Tx registerDRep(@NonNull Account account, Anchor anchor) {
        govTx.registerDRep(account.drepCredential(), anchor, null);
        return this;
    }

    /**
     * Register a DRep
     * @param account Account
     * @return Tx
     */
    public Tx registerDRep(@NonNull Account account) {
        govTx.registerDRep(account.drepCredential(), null, null);
        return this;
    }

    /**
     * Register a DRep
     * @param drepCredential Credential
     * @param anchor Anchor
     * @return Tx
     */
    public Tx registerDRep(@NonNull Credential drepCredential, Anchor anchor) {
        govTx.registerDRep(drepCredential, anchor, null);
        return this;
    }

    /**
     * Register a DRep
     * @param drepCredential
     * @return Tx
     */
    public Tx registerDRep(@NonNull Credential drepCredential) {
        govTx.registerDRep(drepCredential, null, null);
        return this;
    }

    /**
     * Unregister a DRep
     * @param drepCredential Credential
     * @param refundAddress Refund address
     * @param refundAmount Refund amount
     * @return Tx
     */
    public Tx unregisterDRep(@NonNull Credential drepCredential, String refundAddress, BigInteger refundAmount) {
        govTx.unregisterDRep(drepCredential, refundAddress, refundAmount, null);
        return this;
    }

    /**
     * Unregister a DRep
     * @param drepCredential Credential
     * @return Tx
     */
    public Tx unregisterDRep(@NonNull Credential drepCredential) {
        govTx.unregisterDRep(drepCredential, null, null, null);
        return this;
    }

    /**
     * Unregister a DRep
     * @param drepCredential Credential
     * @param refundAddress Refund address
     * @return Tx
     */
    public Tx unregisterDRep(@NonNull Credential drepCredential, @NonNull String refundAddress) {
        govTx.unregisterDRep(drepCredential, refundAddress, null, null);
        return this;
    }

    /**
     * Update a DRep
     * @param drepCredential Credential
     * @param anchor Anchor
     * @return Tx
     */
    public Tx updateDRep(@NonNull Credential drepCredential, Anchor anchor) {
        govTx.updateDRep(drepCredential, anchor, null);
        return this;
    }

    /**
     * Update a DRep
     * @param drepCredential Credential
     * @return Tx
     */
    public Tx updateDRep(@NonNull Credential drepCredential) {
        govTx.updateDRep(drepCredential, null, null);
        return this;
    }

    /**
     * Create a new governance proposal
     * @param govAction GovAction
     * @param rewardAccount return address for the deposit refund
     * @param anchor Anchor
     * @return Tx
     */
    public Tx createProposal(@NonNull GovAction govAction, @NonNull String rewardAccount, Anchor anchor) {
        govTx.createProposal(govAction, rewardAccount, anchor, null);
        return this;
    }

    /**
     * Create a voting procedure
     * @param voter Voter
     * @param govActionId GovActionId
     * @param vote Vote
     * @param anchor
     * @return Tx
     */
    public Tx createVote(@NonNull Voter voter, @NonNull GovActionId govActionId, @NonNull Vote vote, Anchor anchor) {
        govTx.createVote(voter, govActionId, vote, anchor, null);
        return this;
    }

    /**
     * Create a voting procedure
     * @param voter Voter
     * @param govActionId GovActionId
     * @param vote Vote
     * @return Tx
     */
    public Tx createVote(@NonNull Voter voter, @NonNull GovActionId govActionId, @NonNull Vote vote) {
        govTx.createVote(voter, govActionId, vote, null, null);
        return this;
    }

    /**
     * Delegate voting power to a DRep
     * @param address Address
     * @param drep Drep
     * @return Tx
     */
    public Tx delegateVotingPowerTo(@NonNull String address, @NonNull DRep drep) {
        govTx.delegateVotingPowerTo(new Address(address), drep, null);
        return this;
    }

    /**
     * Delegate voting power to a DRep
     * @param address Address
     * @param drep Drep
     * @return Tx
     */
    public Tx delegateVotingPowerTo(@NonNull Address address, @NonNull DRep drep) {
        govTx.delegateVotingPowerTo(address, drep, null);
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
        else if (senderWallet != null)
            return senderWallet.getBaseAddress(0).getAddress(); // TODO - Change address to a new index??
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
    protected Wallet getFromWallet() {
        if(senderWallet != null)
            return senderWallet;
        else
            return null;
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
       Tuple<List<DepositRefundContext>, TxBuilder> stakeBuildTuple =
               stakeTx.build(getFromAddress(), getChangeAddress());

        //Add stake deposit refund contexts
        addDepositRefundContext(stakeBuildTuple._1);

        //Gov txs
        Tuple<List<DepositRefundContext>, TxBuilder> govBuildTuple =
                govTx.build(getFromAddress(), getChangeAddress());

        //Add gov deposit refund contexts
        addDepositRefundContext(govBuildTuple._1);

        TxBuilder txBuilder = super.complete();

        txBuilder = txBuilder.andThen(stakeBuildTuple._2)
                .andThen(govBuildTuple._2);

        return txBuilder;
    }

    private void verifySenderNotExists() {
        if (senderAdded)
            throw new TxBuildException("Sender already added. Cannot add additional sender.");
    }
}
