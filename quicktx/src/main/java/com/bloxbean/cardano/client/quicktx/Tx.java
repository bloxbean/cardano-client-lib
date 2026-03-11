package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.quicktx.filter.UtxoFilterSpec;
import com.bloxbean.cardano.client.quicktx.filter.runtime.UtxoFilterStrategy;
import com.bloxbean.cardano.client.quicktx.filter.yaml.UtxoFilterYaml;
import com.bloxbean.cardano.client.quicktx.intent.*;
import com.bloxbean.cardano.client.quicktx.utxostrategy.ListUtxoPredicateStrategy;
import com.bloxbean.cardano.client.quicktx.utxostrategy.SingleUtxoPredicateStrategy;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.plutus.spec.RedeemerTag;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
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
import java.util.function.Predicate;

public class Tx extends AbstractTx<Tx> {
    private String sender;
    protected boolean senderAdded = false;
    private Wallet senderWallet;
    // Reference-based sender; resolved at composition via SignerRegistry
    private String fromRef;

    /**
     * Create Tx
     */
    public Tx() {
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
     * Set sender using a reference URI (e.g., account://alice, wallet://ops, kms://...).
     * The actual signer and sender address will be resolved at composition time via a SignerRegistry.
     * This is mutually exclusive with {@link #from(String)} and {@link #from(Wallet)}.
     *
     * @param ref signer reference
     * @return this Tx
     */
    public Tx fromRef(String ref) {
        if (this.senderAdded || this.sender != null || this.senderWallet != null)
            throw new TxBuildException("Sender already added. Cannot set fromRef in addition to from address or wallet.");
        if (ref == null || ref.isBlank())
            throw new TxBuildException("fromRef cannot be null or blank");

        this.fromRef = ref;
        // Do not mark senderAdded; resolution happens later
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

    // ========== Script Operations (formerly in ScriptTx) ==========

    // --- Script Input Collection ---

    /**
     * Add given script utxo as input of the transaction.
     *
     * @param utxo         Script utxo
     * @param redeemerData Redeemer data
     * @param datum        Datum object. This will be added to witness list
     * @return Tx
     */
    public Tx collectFrom(Utxo utxo, PlutusData redeemerData, PlutusData datum) {
        return collectFrom(List.of(utxo), redeemerData, datum);
    }

    /**
     * Add given script utxos as inputs of the transaction.
     *
     * @param utxos        Script utxos
     * @param redeemerData Redeemer data
     * @param datum        Datum object. This will be added to witness list
     * @return Tx
     */
    public Tx collectFrom(List<Utxo> utxos, PlutusData redeemerData, PlutusData datum) {
        if (utxos == null || utxos.isEmpty())
            return this;

        ScriptCollectFromIntent intention = ScriptCollectFromIntent.collectFrom(utxos, redeemerData, datum);

        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(intention);

        return this;
    }

    /**
     * Add given script utxo as input of the transaction.
     *
     * @param utxo         Script utxo
     * @param redeemerData Redeemer data
     * @return Tx
     */
    public Tx collectFrom(Utxo utxo, PlutusData redeemerData) {
        return collectFrom(utxo, redeemerData, null);
    }

    /**
     * Add given script utxos as inputs of the transaction.
     *
     * @param utxos        Script utxos
     * @param redeemerData Redeemer data to be used for all the utxos
     * @return Tx
     */
    public Tx collectFrom(List<Utxo> utxos, PlutusData redeemerData) {
        return collectFrom(utxos, redeemerData, null);
    }

    /**
     * Add script UTXOs selected by predicate as inputs of the transaction.
     * The predicate will be applied to UTXOs at the script address during execution.
     *
     * @param scriptAddress the script address to query UTXOs from
     * @param utxoPredicate predicate to select UTXOs
     * @param redeemerData  redeemer data
     * @param datum         datum object
     * @return Tx
     */
    public Tx collectFrom(String scriptAddress, Predicate<Utxo> utxoPredicate, PlutusData redeemerData, PlutusData datum) {
        var utxoStrategy = new SingleUtxoPredicateStrategy(scriptAddress, utxoPredicate, redeemerData, datum);

        ScriptCollectFromIntent intention = ScriptCollectFromIntent.collectFrom(utxoStrategy, redeemerData, datum);
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(intention);

        return this;
    }

    /**
     * Add script UTXOs selected by predicate as inputs of the transaction.
     *
     * @param scriptAddress the script address to query UTXOs from
     * @param utxoPredicate predicate to select UTXOs
     * @param redeemerData  redeemer data
     * @return Tx
     */
    public Tx collectFrom(String scriptAddress, Predicate<Utxo> utxoPredicate, PlutusData redeemerData) {
        return collectFrom(scriptAddress, utxoPredicate, redeemerData, null);
    }

    /**
     * Add script UTXOs selected by a serializable UTxO filter as inputs of the transaction.
     *
     * @param scriptAddress the script address to query UTXOs from
     * @param filterSpec    serializable UTXO filter
     * @param redeemerData  redeemer data
     * @param datum         datum object
     * @return Tx
     */
    public Tx collectFrom(String scriptAddress, UtxoFilterSpec filterSpec, PlutusData redeemerData, PlutusData datum) {
        var utxoStrategy = new UtxoFilterStrategy(scriptAddress, filterSpec, redeemerData, datum);
        var filterNode = UtxoFilterYaml.toNode(filterSpec);
        ScriptCollectFromIntent intention = ScriptCollectFromIntent.builder()
                .lazyUtxoStrategy(utxoStrategy)
                .redeemerData(redeemerData)
                .datum(datum)
                .address(scriptAddress)
                .utxoFilter(filterNode)
                .build();
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(intention);
        return this;
    }

    /**
     * Add script UTXOs selected by a serializable UTxO filter as inputs of the transaction.
     *
     * @param scriptAddress the script address to query UTXOs from
     * @param filterSpec    serializable UTXO filter
     * @param redeemerData  redeemer data
     * @return Tx
     */
    public Tx collectFrom(String scriptAddress, UtxoFilterSpec filterSpec, PlutusData redeemerData) {
        return collectFrom(scriptAddress, filterSpec, redeemerData, null);
    }

    /**
     * Add script UTXOs selected by list predicate as inputs of the transaction.
     *
     * @param scriptAddress the script address to query UTXOs from
     * @param listPredicate predicate to select UTXOs from the complete list
     * @param redeemerData  redeemer data
     * @param datum         datum object
     * @return Tx
     */
    public Tx collectFromList(String scriptAddress, Predicate<List<Utxo>> listPredicate, PlutusData redeemerData, PlutusData datum) {
        var utxoStrategy = new ListUtxoPredicateStrategy(scriptAddress, listPredicate, redeemerData, datum);

        ScriptCollectFromIntent intention = ScriptCollectFromIntent.collectFrom(utxoStrategy, redeemerData, datum);
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(intention);

        return this;
    }

    /**
     * Add script UTXOs selected by list predicate as inputs of the transaction.
     *
     * @param scriptAddress the script address to query UTXOs from
     * @param listPredicate predicate to select UTXOs from the complete list
     * @param redeemerData  redeemer data
     * @return Tx
     */
    public Tx collectFromList(String scriptAddress, Predicate<List<Utxo>> listPredicate, PlutusData redeemerData) {
        return collectFromList(scriptAddress, listPredicate, redeemerData, null);
    }

    // --- Reference Inputs ---

    /**
     * Add utxo(s) as reference input(s) of the transaction.
     *
     * @param utxos reference utxos
     * @return Tx
     */
    public Tx readFrom(Utxo... utxos) {
        if (utxos == null || utxos.length == 0) return this;
        if (intentions == null) intentions = new ArrayList<>();
        ReferenceInputIntent intention = new ReferenceInputIntent();
        for (Utxo utxo : utxos) {
            intention.addRef(utxo.getTxHash(), utxo.getOutputIndex());
        }
        intentions.add(intention);
        return this;
    }

    /**
     * Add transaction input(s) as reference input(s) of the transaction.
     *
     * @param transactionInputs reference transaction inputs
     * @return Tx
     */
    public Tx readFrom(TransactionInput... transactionInputs) {
        if (transactionInputs == null || transactionInputs.length == 0) return this;
        if (intentions == null) intentions = new ArrayList<>();
        ReferenceInputIntent intention = new ReferenceInputIntent();
        for (TransactionInput transactionInput : transactionInputs) {
            intention.addRef(transactionInput.getTransactionId(), transactionInput.getIndex());
        }
        intentions.add(intention);
        return this;
    }

    /**
     * Add transaction input as reference input of the transaction.
     *
     * @param txHash      transaction hash
     * @param outputIndex output index
     * @return Tx
     */
    public Tx readFrom(String txHash, int outputIndex) {
        if (txHash == null || txHash.isBlank()) return this;
        if (intentions == null) intentions = new ArrayList<>();
        ReferenceInputIntent intention = new ReferenceInputIntent();
        intention.addRef(txHash, outputIndex);
        intentions.add(intention);
        return this;
    }

    // --- Plutus Script Minting ---

    /**
     * Mint or Burn asset with given script and redeemer.
     * For minting, provide a positive quantity. For burning, provide a negative quantity.
     *
     * @param script   plutus script
     * @param asset    asset to mint or burn
     * @param redeemer redeemer
     * @return Tx
     */
    public Tx mintAsset(PlutusScript script, Asset asset, PlutusData redeemer) {
        return mintAsset(script, List.of(asset), redeemer, null, null);
    }

    /**
     * Mint assets with given script and redeemer.
     *
     * @param script   plutus script
     * @param assets   assets to mint or burn
     * @param redeemer redeemer
     * @return Tx
     */
    public Tx mintAsset(PlutusScript script, List<Asset> assets, PlutusData redeemer) {
        return mintAsset(script, assets, redeemer, null, null);
    }

    /**
     * Mint asset with given script and redeemer. The minted asset will be sent to the given receiver address.
     *
     * @param script   plutus script
     * @param asset    asset to mint
     * @param redeemer redeemer
     * @param receiver receiver address
     * @return Tx
     */
    public Tx mintAsset(PlutusScript script, Asset asset, PlutusData redeemer, String receiver) {
        return mintAsset(script, List.of(asset), redeemer, receiver, null);
    }

    /**
     * Mint assets with given script and redeemer. The minted assets will be sent to the given receiver address.
     *
     * @param script   plutus script
     * @param assets   assets to mint
     * @param redeemer redeemer
     * @param receiver receiver address
     * @return Tx
     */
    public Tx mintAsset(PlutusScript script, List<Asset> assets, PlutusData redeemer, String receiver) {
        return mintAsset(script, assets, redeemer, receiver, null);
    }

    /**
     * Mint assets with given script and redeemer. The minted assets will be sent to the given receiver address with output datum.
     *
     * @param script      plutus script
     * @param assets      assets to mint
     * @param redeemer    redeemer
     * @param receiver    receiver address
     * @param outputDatum output datum
     * @return Tx
     */
    public Tx mintAsset(PlutusScript script, List<Asset> assets, PlutusData redeemer, String receiver, PlutusData outputDatum) {
        ScriptMintingIntent intention;
        try {
            intention = ScriptMintingIntent.of(script.getPolicyId(), assets, redeemer, receiver, outputDatum);
        } catch (CborSerializationException e) {
            throw new TxBuildException("Error creating minting intention. Unable to get policyId from the minting script.", e);
        }
        ScriptValidatorAttachmentIntent attachmentIntention = ScriptValidatorAttachmentIntent.of(RedeemerTag.Mint, script);

        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(intention);
        intentions.add(attachmentIntention);

        hasMultiAssetMinting = true;
        return this;
    }

    /**
     * Mint or Burn asset with given policy id and redeemer (reference script).
     *
     * @param policyId policy id
     * @param asset    asset to mint or burn
     * @param redeemer redeemer
     * @return Tx
     */
    public Tx mintAsset(String policyId, Asset asset, PlutusData redeemer) {
        return mintAsset(policyId, List.of(asset), redeemer, null, null);
    }

    /**
     * Mint assets with given policy id and redeemer (reference script).
     *
     * @param policyId policy id
     * @param assets   assets to mint or burn
     * @param redeemer redeemer
     * @return Tx
     */
    public Tx mintAsset(String policyId, List<Asset> assets, PlutusData redeemer) {
        return mintAsset(policyId, assets, redeemer, null, null);
    }

    /**
     * Mint asset with given policy id and redeemer (reference script).
     *
     * @param policyId policy id
     * @param asset    asset to mint
     * @param redeemer redeemer
     * @param receiver receiver address
     * @return Tx
     */
    public Tx mintAsset(String policyId, Asset asset, PlutusData redeemer, String receiver) {
        return mintAsset(policyId, List.of(asset), redeemer, receiver, null);
    }

    /**
     * Mint assets with given policy id and redeemer (reference script).
     *
     * @param policyId policy id
     * @param assets   assets to mint
     * @param redeemer redeemer
     * @param receiver receiver address
     * @return Tx
     */
    public Tx mintAsset(String policyId, List<Asset> assets, PlutusData redeemer, String receiver) {
        return mintAsset(policyId, assets, redeemer, receiver, null);
    }

    /**
     * Mint assets with given policy id and redeemer (reference script).
     *
     * @param policyId    policy id
     * @param assets      assets to mint
     * @param redeemer    redeemer
     * @param receiver    receiver address
     * @param outputDatum output datum
     * @return Tx
     */
    public Tx mintAsset(String policyId, List<Asset> assets, PlutusData redeemer, String receiver, PlutusData outputDatum) {
        ScriptMintingIntent intention =
                ScriptMintingIntent.of(policyId, assets, redeemer, receiver, outputDatum);
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(intention);

        hasMultiAssetMinting = true;
        return this;
    }

    // --- Validator Attachment ---

    /**
     * Attach a spending validator script to the transaction
     *
     * @param plutusScript plutus script
     * @return Tx
     */
    public Tx attachSpendingValidator(PlutusScript plutusScript) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(ScriptValidatorAttachmentIntent.of(RedeemerTag.Spend, plutusScript));
        return this;
    }

    /**
     * Attach a minting validator script to the transaction
     *
     * @param plutusScript plutus script
     * @return Tx
     */
    public Tx attachMintValidator(PlutusScript plutusScript) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(ScriptValidatorAttachmentIntent.of(RedeemerTag.Mint, plutusScript));
        return this;
    }

    /**
     * Attach a certificate validator script to the transaction
     *
     * @param plutusScript plutus script
     * @return Tx
     */
    public Tx attachCertificateValidator(PlutusScript plutusScript) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(ScriptValidatorAttachmentIntent.of(RedeemerTag.Cert, plutusScript));
        return this;
    }

    /**
     * Attach a reward validator script to the transaction
     *
     * @param plutusScript plutus script
     * @return Tx
     */
    public Tx attachRewardValidator(PlutusScript plutusScript) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(ScriptValidatorAttachmentIntent.of(RedeemerTag.Reward, plutusScript));
        return this;
    }

    /**
     * Attach a proposing validator script to the transaction
     *
     * @param plutusScript plutus script
     * @return Tx
     */
    public Tx attachProposingValidator(PlutusScript plutusScript) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(ScriptValidatorAttachmentIntent.of(RedeemerTag.Proposing, plutusScript));
        return this;
    }

    /**
     * Attach a voting validator script to the transaction
     *
     * @param plutusScript plutus script
     * @return Tx
     */
    public Tx attachVotingValidator(PlutusScript plutusScript) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(ScriptValidatorAttachmentIntent.of(RedeemerTag.Voting, plutusScript));
        return this;
    }

    // --- Change Address with Datum ---

    /**
     * Send change to the change address with the output datum.
     *
     * @param changeAddress change address
     * @param plutusData    output datum
     * @return Tx
     */
    public Tx withChangeAddress(String changeAddress, PlutusData plutusData) {
        if (changeDatahash != null)
            throw new TxBuildException("Change data hash already set. Cannot set change data");
        this.changeAddress = changeAddress;
        this.changeData = plutusData;
        return this;
    }

    /**
     * Send change to the change address with the output datum hash.
     *
     * @param changeAddress change address
     * @param datumHash     output datum hash
     * @return Tx
     */
    public Tx withChangeAddress(String changeAddress, String datumHash) {
        if (changeData != null)
            throw new TxBuildException("Change data already set. Cannot set change data hash");
        this.changeAddress = changeAddress;
        this.changeDatahash = datumHash;
        return this;
    }

    // --- Script-Protected Stake Operations (with redeemer) ---

    /**
     * De-register stake address with redeemer (for script-protected stake addresses).
     *
     * @param address  address to de-register
     * @param redeemer redeemer
     * @return Tx
     */
    public Tx deregisterStakeAddress(@NonNull String address, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeDeregistrationIntent
                .deregister(address)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * De-register stake address with redeemer (for script-protected stake addresses).
     *
     * @param address  address to de-register
     * @param redeemer redeemer
     * @return Tx
     */
    public Tx deregisterStakeAddress(@NonNull Address address, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeDeregistrationIntent
                .deregister(address.toBech32())
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * De-register stake address with redeemer and refund address.
     *
     * @param address    address to de-register
     * @param redeemer   redeemer
     * @param refundAddr refund address
     * @return Tx
     */
    public Tx deregisterStakeAddress(@NonNull String address, PlutusData redeemer, String refundAddr) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeDeregistrationIntent
                .deregister(address, refundAddr)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Delegate stake address to a stake pool with redeemer (for script-protected stake addresses).
     *
     * @param address  address to delegate
     * @param poolId   stake pool id Bech32 or hex encoded
     * @param redeemer redeemer
     * @return Tx
     */
    public Tx delegateTo(@NonNull String address, @NonNull String poolId, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeDelegationIntent
                .delegateTo(address, poolId)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Delegate stake address to a stake pool with redeemer (for script-protected stake addresses).
     *
     * @param address  address to delegate
     * @param poolId   stake pool id Bech32 or hex encoded
     * @param redeemer redeemer
     * @return Tx
     */
    public Tx delegateTo(@NonNull Address address, @NonNull String poolId, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeDelegationIntent
                .delegateTo(address.toBech32(), poolId)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Withdraw rewards from a stake address with redeemer.
     *
     * @param rewardAddress stake address to withdraw rewards from
     * @param amount        amount to withdraw
     * @param redeemer      redeemer
     * @return Tx
     */
    public Tx withdraw(@NonNull String rewardAddress, @NonNull BigInteger amount, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeWithdrawalIntent
                .withdraw(rewardAddress, amount)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Withdraw rewards from a stake address with redeemer.
     *
     * @param rewardAddress stake address to withdraw rewards from
     * @param amount        amount to withdraw
     * @param redeemer      redeemer
     * @return Tx
     */
    public Tx withdraw(@NonNull Address rewardAddress, @NonNull BigInteger amount, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeWithdrawalIntent
                .withdraw(rewardAddress.toBech32(), amount)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Withdraw rewards from a stake address with redeemer.
     *
     * @param rewardAddress stake address to withdraw rewards from
     * @param amount        amount to withdraw
     * @param redeemer      redeemer
     * @param receiver      receiver address
     * @return Tx
     */
    public Tx withdraw(@NonNull String rewardAddress, @NonNull BigInteger amount, PlutusData redeemer, String receiver) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeWithdrawalIntent
                .withdraw(rewardAddress, amount, receiver)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Withdraw rewards from a stake address with redeemer.
     *
     * @param rewardAddress stake address to withdraw rewards from
     * @param amount        amount to withdraw
     * @param redeemer      redeemer
     * @param receiver      receiver address
     * @return Tx
     */
    public Tx withdraw(@NonNull Address rewardAddress, @NonNull BigInteger amount, PlutusData redeemer, String receiver) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeWithdrawalIntent
                .withdraw(rewardAddress.toBech32(), amount, receiver)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    // --- Script-Protected Governance Operations (with redeemer) ---

    /**
     * Register DRep with redeemer (for script-protected DRep).
     *
     * @param drepCredential DRep credential
     * @param anchor         anchor
     * @param redeemer       redeemer
     * @return Tx
     */
    public Tx registerDRep(@NonNull Credential drepCredential, Anchor anchor, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(DRepRegistrationIntent
                .register(drepCredential, anchor)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Register DRep with redeemer (for script-protected DRep).
     *
     * @param drepCredential DRep credential
     * @param redeemer       redeemer
     * @return Tx
     */
    public Tx registerDRep(@NonNull Credential drepCredential, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(DRepRegistrationIntent
                .register(drepCredential)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Unregister DRep with redeemer.
     *
     * @param drepCredential DRep credential
     * @param refundAddress  refund address
     * @param refundAmount   refund amount
     * @param redeemer       redeemer
     * @return Tx
     */
    public Tx unRegisterDRep(@NonNull Credential drepCredential, String refundAddress, BigInteger refundAmount, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(DRepDeregistrationIntent
                .deregister(drepCredential, refundAddress, refundAmount)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Unregister DRep with redeemer.
     *
     * @param drepCredential DRep credential
     * @param refundAddress  refund address
     * @param redeemer       redeemer
     * @return Tx
     */
    public Tx unRegisterDRep(@NonNull Credential drepCredential, String refundAddress, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(DRepDeregistrationIntent
                .deregister(drepCredential, refundAddress)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Update DRep with redeemer.
     *
     * @param drepCredential DRep credential
     * @param anchor         anchor
     * @param redeemer       redeemer
     * @return Tx
     */
    public Tx updateDRep(@NonNull Credential drepCredential, Anchor anchor, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(DRepUpdateIntent
                .update(drepCredential, anchor)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Create a governance proposal with redeemer.
     *
     * @param govAction     gov action
     * @param returnAddress return address
     * @param anchor        anchor
     * @param redeemer      redeemer
     * @return Tx
     */
    public Tx createProposal(GovAction govAction, @NonNull String returnAddress, Anchor anchor, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(GovernanceProposalIntent
                .create(govAction, returnAddress, anchor)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Create a vote with redeemer (for script-protected voter).
     *
     * @param voter       voter
     * @param govActionId gov action id
     * @param vote        vote
     * @param anchor      anchor
     * @param redeemer    redeemer
     * @return Tx
     */
    public Tx createVote(@NonNull Voter voter, @NonNull GovActionId govActionId, @NonNull Vote vote, Anchor anchor, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(VotingIntent
                .vote(voter, govActionId, vote, anchor)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Delegate voting power to a DRep with redeemer.
     *
     * @param address  address to delegate
     * @param drep     DRep
     * @param redeemer redeemer
     * @return Tx
     */
    public Tx delegateVotingPowerTo(@NonNull Address address, @NonNull DRep drep, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(VotingDelegationIntent
                .delegate(address, drep)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    // ========== End Script Operations ==========

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
        else if (fromRef != null)
            return null; //Change address will be set later during final transaction and set to fromRef address
        else if (hasScriptIntents())
            return null; // Script-only Tx: change address will be set by QuickTxBuilder from fee payer
        else
            throw new TxBuildException("No change address. " +
                    "Please define at least one of sender address or sender account or change address");
    }

    @Override
    protected String getFromAddress() {
        if (sender != null)
            return sender;
        else if (hasScriptIntents())
            return null; // Script-only Tx: from address will be set by QuickTxBuilder from fee payer
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

    /**
     * {@inheritDoc}
     * <p>Only sets the default sender when the user has not already called {@code from()}.
     * The {@code senderAdded} flag is set by the public {@code from()} API, so checking
     * {@code !senderAdded && sender == null} ensures we never override an explicit user choice.</p>
     */
    @Override
    void setDefaultFrom(String address) {
        if (!senderAdded && sender == null) {
            this.sender = address;
        }
    }

    /**
     * {@inheritDoc}
     * <p>Only sets the default wallet when the user has not already called {@code from()}.
     * Guards with {@code !senderAdded && senderWallet == null} to preserve explicit user choice.</p>
     */
    @Override
    void setDefaultFrom(Wallet wallet) {
        if (!senderAdded && senderWallet == null) {
            this.senderWallet = wallet;
            this.sender = wallet.getBaseAddress(0).getAddress();
        }
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

    /**
     * Get the sender reference, if set via {@link #fromRef(String)}.
     * @return reference string or null if not set
     */
    public String getFromRef() {
        return fromRef;
    }
}
