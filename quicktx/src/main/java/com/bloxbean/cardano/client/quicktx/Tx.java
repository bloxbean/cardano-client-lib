package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.quicktx.intent.*;
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
import com.bloxbean.cardano.hdwallet.Wallet;
import lombok.NonNull;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Tx extends AbstractTx<Tx> {
    private String sender;
    protected boolean senderAdded = false;
    private Wallet senderWallet;

    /**
     * Create Tx
     */
    public Tx() {
    }

    /**
     * Enable intention recording for all sub-transactions.
     * This allows capturing stake and governance operations for YAML serialization.
     */
    public void enableIntentionRecording() {

    }

    /**
     * Get all intentions from this transaction and its sub-transactions.
     *
     * @return combined list of all intentions
     */
    @Override
    public List<TxIntent> getIntentions() {
        List<TxIntent> allIntentions = new ArrayList<>();

        if (intentions != null) {
            allIntentions.addAll(intentions);
        }

        return allIntentions;
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
        // Create and store minting intention
        MintingIntent intention = MintingIntent.from(script, assets, receiver);

        if (intentions == null) {
            intentions = new java.util.ArrayList<>();
        }
        intentions.add(intention);

        hasMultiAssetMinting = true;

        return this;
    }

    /**
     * Attaches a NativeScript to the current transaction.
     * This method ensures that the given NativeScript is added to
     * the set of native scripts associated with the transaction.
     *
     * @param script the NativeScript to be attached to the transaction
     * @return the current Tx instance with the updated native scripts set
     */
    public Tx attachNativeScript(NativeScript script) {
        if (intentions == null) {
            intentions = new ArrayList<>();
        }
        intentions.add(NativeScriptAttachmentIntent.of(script));
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
     *
     * @param utxos List of utxos
     * @return Tx
     */
    public Tx collectFrom(List<Utxo> utxos) {
        // Record intention for YAML/plan replay
        if (utxos != null && !utxos.isEmpty()) {
            if (intentions == null) intentions = new ArrayList<>();
            intentions.add(CollectFromIntent.builder()
                    .utxos(utxos)
                    .build());
        }
        return this;
    }

    /**
     * Create Tx with given utxos as inputs.
     *
     * @param utxos Set of utxos
     * @return Tx
     */
    public Tx collectFrom(Set<Utxo> utxos) {
        // Record intention for YAML/plan replay
        if (utxos != null && !utxos.isEmpty()) {
            if (intentions == null) intentions = new ArrayList<>();
            intentions.add(CollectFromIntent.builder()
                    .utxos(new ArrayList<>(utxos))
                    .build());
        }
        return this;
    }

    /**
     * Register stake address
     *
     * @param address address to register. Address should have delegation credential. So it should be a base address or stake address.
     * @return Tx
     */
    public Tx registerStakeAddress(@NonNull String address) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeRegistrationIntent.register(address));
        return this;
    }

    public Tx registerStakeAddress(@NonNull Wallet wallet) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeRegistrationIntent.register(wallet.getStakeAddress()));
        return this;
    }

    /**
     * Register stake address
     *
     * @param address address to register. Address should have delegation credential. So it should be a base address or stake address.
     * @return Tx
     */
    public Tx registerStakeAddress(@NonNull Address address) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeRegistrationIntent.register(address.toBech32()));
        return this;
    }

    /**
     * De-register stake address. The key deposit will be refunded to the change address or fee payer if change address is not specified
     *
     * @param address address to de-register. Address should have delegation credential. So it should be a base address or stake address.
     * @return Tx
     */
    public Tx deregisterStakeAddress(@NonNull String address) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeDeregistrationIntent.deregister(address));
        return this;
    }

    /**
     * De-register stake address. The key deposit will be refunded to the change address or fee payer if change address is not specified
     *
     * @param address address to de-register. Address should have delegation credential. So it should be a base address or stake address.
     * @return Tx
     */
    public Tx deregisterStakeAddress(@NonNull Address address) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeDeregistrationIntent.deregister(address.toBech32()));
        return this;
    }

    public Tx deregisterStakeAddress(@NonNull Wallet wallet) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeDeregistrationIntent.deregister(wallet.getStakeAddress()));
        return this;
    }

    /**
     * De-register stake address. The key deposit will be refunded to the refund address.
     *
     * @param address    address to de-register. Address should have delegation credential. So it should be a base address or stake address.
     * @param refundAddr refund address
     * @return T
     */
    public Tx deregisterStakeAddress(@NonNull String address, @NonNull String refundAddr) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeDeregistrationIntent.deregister(address, refundAddr));
        return this;
    }

    /**
     * De-register stake address. The key deposit will be refunded to the refund address.
     *
     * @param address    address to de-register. Address should have delegation credential. So it should be a base address or stake address.
     * @param refundAddr refund address
     * @return T
     */
    public Tx deregisterStakeAddress(@NonNull Address address, @NonNull String refundAddr) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeDeregistrationIntent.deregister(address.toBech32(), refundAddr));
        return this;
    }

    /**
     * Delegate stake address to a stake pool
     *
     * @param address address to delegate. Address should have delegation credential. So it should be a base address or stake address.
     * @param poolId  stake pool id Bech32 or hex encoded
     * @return ScriptTx
     */
    public Tx delegateTo(@NonNull String address, @NonNull String poolId) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeDelegationIntent.delegateTo(address, poolId));
        return this;
    }

    public Tx delegateTo(@NonNull Wallet wallet, @NonNull String poolId) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeDelegationIntent.delegateTo(wallet.getStakeAddress(), poolId));
        return this;
    }

    /**
     * Delegate stake address to a stake pool
     *
     * @param address address to delegate. Address should have delegation credential. So it should be a base address or stake address.
     * @param poolId  stake pool id Bech32 or hex encoded
     * @return ScriptTx
     */
    public Tx delegateTo(@NonNull Address address, @NonNull String poolId) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeDelegationIntent.delegateTo(address.toBech32(), poolId));
        return this;
    }

    /**
     * Withdraw rewards from a stake address
     *
     * @param rewardAddress stake address to withdraw rewards from
     * @param amount        amount to withdraw
     * @return Tx
     */
    public Tx withdraw(@NonNull String rewardAddress, @NonNull BigInteger amount) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeWithdrawalIntent.withdraw(rewardAddress, amount));
        return this;
    }

    /**
     * Withdraw rewards from a stake address
     *
     * @param rewardAddress stake address to withdraw rewards from
     * @param amount        amount to withdraw
     * @return Tx
     */
    public Tx withdraw(@NonNull Address rewardAddress, @NonNull BigInteger amount) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeWithdrawalIntent.withdraw(rewardAddress.toBech32(), amount));
        return this;
    }

    /**
     * Withdraw rewards from a stake address
     *
     * @param rewardAddress stake address to withdraw rewards from
     * @param amount        amount to withdraw
     * @param receiver      receiver address
     * @return Tx
     */
    public Tx withdraw(@NonNull String rewardAddress, @NonNull BigInteger amount, String receiver) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeWithdrawalIntent.withdraw(rewardAddress, amount, receiver));
        return this;
    }

    /**
     * Withdraw rewards from a stake address
     *
     * @param rewardAddress stake address to withdraw rewards from
     * @param amount        amount to withdraw
     * @param receiver      receiver address
     * @return Tx
     */
    public Tx withdraw(@NonNull Address rewardAddress, @NonNull BigInteger amount, String receiver) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeWithdrawalIntent.withdraw(rewardAddress.toBech32(), amount, receiver));
        return this;
    }

    /**
     * Register a stake pool
     *
     * @param poolRegistration stake pool registration certificate
     * @return Tx
     */
    public Tx registerPool(@NonNull PoolRegistration poolRegistration) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(PoolRegistrationIntent.register(poolRegistration));
        return this;
    }

    /**
     * Update a stake pool
     *
     * @param poolRegistration
     * @return Tx
     */
    public Tx updatePool(@NonNull PoolRegistration poolRegistration) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(PoolRegistrationIntent.update(poolRegistration));
        return this;
    }

    /**
     * Retire a stake pool
     *
     * @param poolId stake pool id Bech32 or hex encoded
     * @param epoch  epoch to retire the pool
     * @return Tx
     */
    public Tx retirePool(@NonNull String poolId, @NonNull int epoch) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(PoolRetirementIntent.retire(poolId, epoch));
        return this;
    }

    /**
     * Register a DRep
     *
     * @param account Account
     * @param anchor  Anchor
     * @return Tx
     */
    public Tx registerDRep(@NonNull Account account, Anchor anchor) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(DRepRegistrationIntent.register(account.drepCredential(), anchor));
        return this;
    }

    /**
     * Register a DRep
     *
     * @param account Account
     * @return Tx
     */
    public Tx registerDRep(@NonNull Account account) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(DRepRegistrationIntent.register(account.drepCredential()));
        return this;
    }

    /**
     * Register a DRep
     *
     * @param drepCredential Credential
     * @param anchor         Anchor
     * @return Tx
     */
    public Tx registerDRep(@NonNull Credential drepCredential, Anchor anchor) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(DRepRegistrationIntent.register(drepCredential, anchor));
        return this;
    }

    /**
     * Register a DRep
     *
     * @param drepCredential
     * @return Tx
     */
    public Tx registerDRep(@NonNull Credential drepCredential) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(DRepRegistrationIntent.register(drepCredential));
        return this;
    }

    /**
     * Unregister a DRep
     *
     * @param drepCredential Credential
     * @param refundAddress  Refund address
     * @param refundAmount   Refund amount
     * @return Tx
     */
    public Tx unregisterDRep(@NonNull Credential drepCredential, String refundAddress, BigInteger refundAmount) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(DRepDeregistrationIntent.deregister(drepCredential, refundAddress, refundAmount));
        return this;
    }

    /**
     * Unregister a DRep
     *
     * @param drepCredential Credential
     * @return Tx
     */
    public Tx unregisterDRep(@NonNull Credential drepCredential) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(DRepDeregistrationIntent.deregister(drepCredential));
        return this;
    }

    /**
     * Unregister a DRep
     *
     * @param drepCredential Credential
     * @param refundAddress  Refund address
     * @return Tx
     */
    public Tx unregisterDRep(@NonNull Credential drepCredential, @NonNull String refundAddress) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(DRepDeregistrationIntent.deregister(drepCredential, refundAddress));
        return this;
    }

    /**
     * Update a DRep
     *
     * @param drepCredential Credential
     * @param anchor         Anchor
     * @return Tx
     */
    public Tx updateDRep(@NonNull Credential drepCredential, Anchor anchor) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(DRepUpdateIntent.update(drepCredential, anchor));
        return this;
    }

    /**
     * Update a DRep
     *
     * @param drepCredential Credential
     * @return Tx
     */
    public Tx updateDRep(@NonNull Credential drepCredential) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(DRepUpdateIntent.update(drepCredential));
        return this;
    }

    /**
     * Create a new governance proposal
     *
     * @param govAction     GovAction
     * @param rewardAccount return address for the deposit refund
     * @param anchor        Anchor
     * @return Tx
     */
    public Tx createProposal(@NonNull GovAction govAction, @NonNull String rewardAccount, Anchor anchor) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(GovernanceProposalIntent.create(govAction, rewardAccount, anchor));
        return this;
    }

    /**
     * Create a voting procedure
     *
     * @param voter       Voter
     * @param govActionId GovActionId
     * @param vote        Vote
     * @param anchor
     * @return Tx
     */
    public Tx createVote(@NonNull Voter voter, @NonNull GovActionId govActionId, @NonNull Vote vote, Anchor anchor) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(VotingIntent.vote(voter, govActionId, vote, anchor));
        return this;
    }

    /**
     * Create a voting procedure
     *
     * @param voter       Voter
     * @param govActionId GovActionId
     * @param vote        Vote
     * @return Tx
     */
    public Tx createVote(@NonNull Voter voter, @NonNull GovActionId govActionId, @NonNull Vote vote) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(VotingIntent.vote(voter, govActionId, vote));
        return this;
    }

    /**
     * Delegate voting power to a DRep
     *
     * @param address Address
     * @param drep    Drep
     * @return Tx
     */
    public Tx delegateVotingPowerTo(@NonNull String address, @NonNull DRep drep) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(VotingDelegationIntent.delegate(address, drep));
        return this;
    }

    /**
     * Delegate voting power to a DRep
     *
     * @param address Address
     * @param drep    Drep
     * @return Tx
     */
    public Tx delegateVotingPowerTo(@NonNull Address address, @NonNull DRep drep) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(VotingDelegationIntent.delegate(address, drep));
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
        if (senderWallet != null)
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
        TxBuilder txBuilder = super.complete();
        return txBuilder;
    }

    private void verifySenderNotExists() {
        if (senderAdded)
            throw new TxBuildException("Sender already added. Cannot add additional sender.");
    }

    /**
     * Get the sender address for this transaction.
     *
     * @return sender address or null if not set
     */
    public String getSender() {
        return sender;
    }
}
