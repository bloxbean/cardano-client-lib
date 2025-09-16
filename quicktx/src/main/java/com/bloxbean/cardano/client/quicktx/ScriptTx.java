package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.function.helper.RedeemerUtil;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.quicktx.intent.*;
import com.bloxbean.cardano.client.quicktx.utxostrategy.LazyUtxoStrategy;
import com.bloxbean.cardano.client.quicktx.utxostrategy.ListUtxoPredicateStrategy;
import com.bloxbean.cardano.client.quicktx.utxostrategy.SingleUtxoPredicateStrategy;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import com.bloxbean.cardano.client.transaction.spec.governance.Vote;
import com.bloxbean.cardano.client.transaction.spec.governance.Voter;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovAction;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovActionId;
import com.bloxbean.cardano.hdwallet.Wallet;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

@Slf4j
public class ScriptTx extends AbstractTx<ScriptTx> {
    protected List<LazyUtxoStrategy> lazyStrategies;
    protected String fromAddress;
    protected Wallet fromWallet;

    public ScriptTx() {
        lazyStrategies = new ArrayList<>();
    }

    /**
     * Get all intentions from this transaction and its sub-transactions.
     *
     * @return combined list of all intentions
     */
    @Override
    public java.util.List<TxIntent> getIntentions() {
        java.util.List<TxIntent> all = new java.util.ArrayList<>();
        if (this.intentions != null) all.addAll(this.intentions);
        return all;
    }

    /**
     * Add given script utxo as input of the transaction.
     *
     * @param utxo         Script utxo
     * @param redeemerData Redeemer data
     * @param datum        Datum object. This will be added to witness list
     * @return ScriptTx
     */
    public ScriptTx collectFrom(Utxo utxo, PlutusData redeemerData, PlutusData datum) {
        return collectFrom(List.of(utxo), redeemerData, datum);
    }

    /**
     * Add given script utxos as inputs of the transaction.
     *
     * @param utxos        Script utxos
     * @param redeemerData Redeemer data
     * @param datum        Datum object. This will be added to witness list
     * @return ScriptTx
     */
    public ScriptTx collectFrom(List<Utxo> utxos, PlutusData redeemerData, PlutusData datum) {
        if (utxos == null || utxos.isEmpty())
            return this;

        // Create intention using the factory method that stores original objects
        ScriptCollectFromIntent intention = ScriptCollectFromIntent.collectFrom(utxos, redeemerData, datum);

        if (intentions == null) {
            intentions = new ArrayList<>();
        }
        intentions.add(intention);

        return this;
    }

    /**
     * Add given script utxo as input of the transaction.
     *
     * @param utxo         Script utxo
     * @param redeemerData Redeemer data
     * @return ScriptTx
     */
    public ScriptTx collectFrom(Utxo utxo, PlutusData redeemerData) {
        return collectFrom(utxo, redeemerData, null);
    }

    /**
     * Add given script utxos as inputs of the transaction.
     *
     * @param utxos        Script utxos
     * @param redeemerData Redeemer data to be used for all the utxos
     * @return ScriptTx
     */
    public ScriptTx collectFrom(List<Utxo> utxos, PlutusData redeemerData) {
        return collectFrom(utxos, redeemerData, null);
    }

    /**
     * Add this utxo as input of the transaction.
     * This method doesn't add any redeemer. The utxo will only be added as input and balance will be calculated.
     *
     * @param utxo Utxo
     * @return ScriptTx
     */
    public ScriptTx collectFrom(Utxo utxo) {
        if (utxo == null)
            return this;

        // Record intention without redeemer/datum
        ScriptCollectFromIntent intention = ScriptCollectFromIntent.collectFrom(utxo);
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(intention);

        return this;
    }

    /**
     * Add these utxos as inputs of the transaction.
     * This method doesn't add any redeemer. The utxos will only be added as input and balance will be calculated.
     *
     * @param utxos Utxos
     * @return ScriptTx
     */
    public ScriptTx collectFrom(List<Utxo> utxos) {
        if (utxos == null || utxos.isEmpty())
            return this;

        // Record a single intention with UTXO list and no redeemer/datum
        ScriptCollectFromIntent intention = ScriptCollectFromIntent.collectFrom(utxos, null, null);
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(intention);

        return this;
    }

    /**
     * Add script UTXOs selected by predicate as inputs of the transaction.
     * The predicate will be applied to UTXOs at the script address during execution.
     * This enables lazy UTXO resolution for transaction chains.
     *
     * @param scriptAddress the script address to query UTXOs from
     * @param utxoPredicate predicate to select UTXOs
     * @param redeemerData  redeemer data
     * @param datum         datum object
     * @return ScriptTx
     */
    public ScriptTx collectFrom(String scriptAddress, Predicate<Utxo> utxoPredicate, PlutusData redeemerData, PlutusData datum) {
        // TODO(yaml): Predicate-based collectFrom uses runtime-only lazy strategies and is not serialized to YAML yet.
        var utxoStrategy = new SingleUtxoPredicateStrategy(scriptAddress, utxoPredicate, redeemerData, datum);

        ScriptCollectFromIntent intention = ScriptCollectFromIntent.collectFrom(utxoStrategy, redeemerData, datum);
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(intention);

        return this;
    }

    /**
     * Add script UTXOs selected by predicate as inputs of the transaction.
     * The predicate will be applied to UTXOs at the script address during execution.
     *
     * @param scriptAddress the script address to query UTXOs from
     * @param utxoPredicate predicate to select UTXOs
     * @param redeemerData  redeemer data
     * @return ScriptTx
     */
    public ScriptTx collectFrom(String scriptAddress, Predicate<Utxo> utxoPredicate, PlutusData redeemerData) {
        // TODO(yaml): Predicate-based collectFrom uses runtime-only lazy strategies and is not serialized to YAML yet.
        return collectFrom(scriptAddress, utxoPredicate, redeemerData, null);
    }

    /**
     * Add script UTXOs selected by list predicate as inputs of the transaction.
     * The list predicate receives all UTXOs at the script address and returns filtered selection.
     * This enables complex UTXO selection strategies for transaction chains.
     *
     * @param scriptAddress the script address to query UTXOs from
     * @param listPredicate predicate to select UTXOs from the complete list
     * @param redeemerData  redeemer data
     * @param datum         datum object
     * @return ScriptTx
     */
    public ScriptTx collectFromList(String scriptAddress, Predicate<List<Utxo>> listPredicate, PlutusData redeemerData, PlutusData datum) {
        // TODO(yaml): Predicate-based collectFromList uses runtime-only lazy strategies and is not serialized to YAML yet.
        // Consider registry/DSL or snapshot+hints for future YAML support.
        var utxoStrategy = new ListUtxoPredicateStrategy(scriptAddress, listPredicate, redeemerData, datum);

        ScriptCollectFromIntent intention = ScriptCollectFromIntent.collectFrom(utxoStrategy, redeemerData, datum);
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(intention);

        return this;
    }

    /**
     * Add script UTXOs selected by list predicate as inputs of the transaction.
     * The list predicate receives all UTXOs at the script address and returns filtered selection.
     *
     * @param scriptAddress the script address to query UTXOs from
     * @param listPredicate predicate to select UTXOs from the complete list
     * @param redeemerData  redeemer data
     * @return ScriptTx
     */
    public ScriptTx collectFromList(String scriptAddress, Predicate<List<Utxo>> listPredicate, PlutusData redeemerData) {
        return collectFromList(scriptAddress, listPredicate, redeemerData, null);
    }

    /**
     * Add utxo(s) as reference input(s) of the transaction.
     *
     * @param utxos
     * @return ScriptTx
     */
    public ScriptTx readFrom(Utxo... utxos) {
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
     * @param transactionInputs
     * @return ScriptTx
     */
    public ScriptTx readFrom(TransactionInput... transactionInputs) {
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
     * @param txHash
     * @param outputIndex
     * @return ScriptTx
     */
    public ScriptTx readFrom(String txHash, int outputIndex) {
        if (txHash == null || txHash.isBlank()) return this;
        if (intentions == null) intentions = new ArrayList<>();
        ReferenceInputIntent intention = new ReferenceInputIntent();
        intention.addRef(txHash, outputIndex);
        intentions.add(intention);
        return this;
    }

    //TODO: registerPool(poolParam)
    //TODO: retirePool(poolId, epochNo)
    //TODO: updatePool(poolParam)

    /**
     * Mint or Burn asset with given script and redeemer
     * For minting, provide a positive quantity. For burning, provide a negative quantity.
     *
     * @param script   plutus script
     * @param asset    asset to mint or burn
     * @param redeemer redeemer
     * @return ScriptTx
     */
    public ScriptTx mintAsset(PlutusScript script, Asset asset, PlutusData redeemer) {
        return mintAsset(script, List.of(asset), redeemer, null, null);
    }

    /**
     * Mint assets with given script and redeemer.
     * For minting, provide a positive quantity. For burning, provide a negative quantity.
     *
     * @param script   plutus script
     * @param assets   assets to mint or burn
     * @param redeemer redeemer
     * @return ScriptTx
     */
    public ScriptTx mintAsset(PlutusScript script, List<Asset> assets, PlutusData redeemer) {
        return mintAsset(script, assets, redeemer, null, null);
    }

    /**
     * Mint asset with given script and redeemer. The minted asset will be sent to the given receiver address
     *
     * @param script   plutus script
     * @param asset    asset to mint
     * @param redeemer redeemer
     * @param receiver receiver address
     * @return ScriptTx
     */
    public ScriptTx mintAsset(PlutusScript script, Asset asset, PlutusData redeemer, String receiver) {
        return mintAsset(script, List.of(asset), redeemer, receiver, null);
    }

    /**
     * Mint assets with given script and redeemer. The minted assets will be sent to the given receiver address
     *
     * @param script   plutus script
     * @param assets   assets to mint
     * @param redeemer redeemer
     * @param receiver receiver address
     * @return ScriptTx
     */
    public ScriptTx mintAsset(PlutusScript script, List<Asset> assets, PlutusData redeemer, String receiver) {
        return mintAsset(script, assets, redeemer, receiver, null);
    }

    /**
     * Mint assets with given script and redeemer. The minted assets will be sent to the given receiver address with output datum
     *
     * @param script      plutus script
     * @param assets      assets to mint
     * @param redeemer    redeemer
     * @param receiver    receiver address
     * @param outputDatum output datum
     * @return ScriptTx
     */

    public ScriptTx mintAsset(PlutusScript script, List<Asset> assets, PlutusData redeemer, String receiver, PlutusData outputDatum) {
        // Record a script minting intention; intention will add mint + witnesses + optional receiver output
        ScriptMintingIntent intention = null;
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

    public ScriptTx mintAsset(String policyId, Asset asset, PlutusData redeemer) {
        return mintAsset(policyId, List.of(asset), redeemer, null, null);
    }

    public ScriptTx mintAsset(String policyId, List<Asset> assets, PlutusData redeemer) {
        return mintAsset(policyId, assets, redeemer, null, null);
    }

    public ScriptTx mintAsset(String policyId, Asset asset, PlutusData redeemer, String receiver) {
        return mintAsset(policyId, List.of(asset), redeemer, receiver, null);
    }

    public ScriptTx mintAsset(String policyId, List<Asset> assets, PlutusData redeemer, String receiver) {
        return mintAsset(policyId, assets, redeemer, receiver, null);
    }

    public ScriptTx mintAsset(String policyId, List<Asset> assets, PlutusData redeemer, String receiver, PlutusData outputDatum) {
        // Record a script minting intention; intention will add mint + witnesses + optional receiver output
        ScriptMintingIntent intention =
                ScriptMintingIntent.of(policyId, assets, redeemer, receiver, outputDatum);
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(intention);

        hasMultiAssetMinting = true;
        return this;
    }

    /**
     * Attach a spending validator script to the transaction
     *
     * @param plutusScript
     * @return ScriptTx
     */
    public ScriptTx attachSpendingValidator(PlutusScript plutusScript) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(ScriptValidatorAttachmentIntent
                .of(RedeemerTag.Spend, plutusScript));
        return this;
    }

    /**
     * Attach a minting validator script to the transaction. This method is called from the mintAssets methods.
     *
     * @param plutusScript plutus script
     * @return ScriptTx
     */
    public ScriptTx attachMintValidator(PlutusScript plutusScript) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(ScriptValidatorAttachmentIntent
                .of(RedeemerTag.Mint, plutusScript));
        return this;
    }

    /**
     * Attach a certificate validator script to the transaction
     *
     * @param plutusScript plutus script
     * @return ScriptTx
     */
    public ScriptTx attachCertificateValidator(PlutusScript plutusScript) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(ScriptValidatorAttachmentIntent
                .of(com.bloxbean.cardano.client.plutus.spec.RedeemerTag.Cert, plutusScript));
        return this;
    }

    /**
     * Attach a reward validator script to the transaction
     *
     * @param plutusScript plutus script
     * @return ScriptTx
     */
    public ScriptTx attachRewardValidator(PlutusScript plutusScript) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(ScriptValidatorAttachmentIntent
                .of(com.bloxbean.cardano.client.plutus.spec.RedeemerTag.Reward, plutusScript));
        return this;
    }

    /**
     * Attach a proposing validator script to the transaction
     *
     * @param plutusScript plutus script
     * @return ScriptTx
     */
    public ScriptTx attachProposingValidator(PlutusScript plutusScript) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(ScriptValidatorAttachmentIntent
                .of(com.bloxbean.cardano.client.plutus.spec.RedeemerTag.Proposing, plutusScript));
        return this;
    }

    /**
     * Attach a voting validator script to the transaction
     *
     * @param plutusScript plutus script
     * @return ScriptTx
     */
    public ScriptTx attachVotingValidator(PlutusScript plutusScript) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(ScriptValidatorAttachmentIntent
                .of(com.bloxbean.cardano.client.plutus.spec.RedeemerTag.Voting, plutusScript));
        return this;
    }

    /**
     * Send change to the change address with the output datum.
     *
     * @param changeAddress change address
     * @param plutusData    output datum
     * @return ScriptTx
     */
    public ScriptTx withChangeAddress(String changeAddress, PlutusData plutusData) {
        if (changeDatahash != null)
            throw new TxBuildException("Change data hash already set. Cannot set change data");
        this.changeAddress = changeAddress;
        this.changeData = plutusData;
        return this;
    }

    /**
     * De-register stake address. The key deposit will be refunded to the change address or fee payer if change address is not specified.
     *
     * @param address  address to de-register. Address should have delegation credential. So it should be a base address or stake address.
     * @param redeemer redeemer to use if the address is a script address
     * @return ScriptTx
     */
    public ScriptTx deregisterStakeAddress(@NonNull String address, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeDeregistrationIntent
                .deregister(address)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * De-register stake address. The key deposit will be refunded to the change address or fee payer if change address is not specified.
     *
     * @param address  address to de-register. Address should have delegation credential. So it should be a base address or stake address.
     * @param redeemer redeemer to use if the address is a script address
     * @return ScriptTx
     */
    public ScriptTx deregisterStakeAddress(@NonNull Address address, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeDeregistrationIntent
                .deregister(address.toBech32())
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * De-register stake address. The key deposit will be refunded to the refund address.
     *
     * @param address    address to de-register. Address should have delegation credential. So it should be a base address or stake address.
     * @param redeemer   redeemer to use if the address is a script address
     * @param refundAddr refund address
     * @return ScriptTx
     */
    public ScriptTx deregisterStakeAddress(@NonNull String address, PlutusData redeemer, String refundAddr) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeDeregistrationIntent
                .deregister(address, refundAddr)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Delegate stake address to a stake pool
     *
     * @param address  address to delegate. Address should have delegation credential. So it should be a base address or stake address.
     * @param poolId   stake pool id Bech32 or hex encoded
     * @param redeemer redeemer to use if the address is a script address
     * @return ScriptTx
     */
    public ScriptTx delegateTo(@NonNull String address, @NonNull String poolId, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeDelegationIntent
                .delegateTo(address, poolId)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Delegate stake address to a stake pool
     *
     * @param address  address to delegate. Address should have delegation credential. So it should be a base address or stake address.
     * @param poolId   stake pool id Bech32 or hex encoded
     * @param redeemer redeemer to use if the address is a script address
     * @return ScriptTx
     */
    public ScriptTx delegateTo(@NonNull Address address, @NonNull String poolId, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeDelegationIntent
                .delegateTo(address.toBech32(), poolId)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Withdraw rewards from a stake address
     *
     * @param rewardAddress stake address to withdraw rewards from
     * @param amount        amount to withdraw
     * @param redeemer      redeemer
     * @return ScriptTx
     */
    public ScriptTx withdraw(@NonNull String rewardAddress, @NonNull BigInteger amount, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeWithdrawalIntent
                .withdraw(rewardAddress, amount)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Withdraw rewards from a stake address
     *
     * @param rewardAddress stake address to withdraw rewards from
     * @param amount        amount to withdraw
     * @param redeemer      redeemer
     * @return ScriptTx
     */
    public ScriptTx withdraw(@NonNull Address rewardAddress, @NonNull BigInteger amount, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeWithdrawalIntent
                .withdraw(rewardAddress.toBech32(), amount)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Withdraw rewards from a stake address
     *
     * @param rewardAddress stake address to withdraw rewards from
     * @param amount        amount to withdraw
     * @param redeemer      redeemer
     * @param receiver      receiver address
     * @return
     */
    public ScriptTx withdraw(@NonNull String rewardAddress, @NonNull BigInteger amount, PlutusData redeemer, String receiver) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeWithdrawalIntent
                .withdraw(rewardAddress, amount, receiver)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Withdraw rewards from a stake address
     *
     * @param rewardAddress stake address to withdraw rewards from
     * @param amount        amount to withdraw
     * @param redeemer      redeemer
     * @param receiver      receiver address
     * @return ScriptTx
     */
    public ScriptTx withdraw(@NonNull Address rewardAddress, @NonNull BigInteger amount, PlutusData redeemer, String receiver) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(StakeWithdrawalIntent
                .withdraw(rewardAddress.toBech32(), amount, receiver)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Register DRep
     *
     * @param drepCredential DRep credential
     * @param anchor         anchor
     * @param redeemer       redeemer
     * @return ScriptTx
     */
    public ScriptTx registerDRep(@NonNull Credential drepCredential, Anchor anchor, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(DRepRegistrationIntent
                .register(drepCredential, anchor)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Register DRep
     *
     * @param drepCredential - DRep credential
     * @param redeemer       - redeemer
     * @return ScriptTx
     */
    public ScriptTx registerDRep(@NonNull Credential drepCredential, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(DRepRegistrationIntent
                .register(drepCredential)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Unregister DRep
     *
     * @param drepCredential DRep credential
     * @param refundAddress  refund address
     * @param refundAmount   refund amount
     * @param redeemer       redeemer
     * @return ScriptTx
     */
    public ScriptTx unRegisterDRep(@NonNull Credential drepCredential, String refundAddress, BigInteger refundAmount, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(DRepDeregistrationIntent
                .deregister(drepCredential, refundAddress, refundAmount)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Unregister DRep
     *
     * @param drepCredential DRep credential
     * @param refundAddress  refund address
     * @param redeemer       redeemer
     * @return ScriptTx
     */
    public ScriptTx unRegisterDRep(@NonNull Credential drepCredential, String refundAddress, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(DRepDeregistrationIntent
                .deregister(drepCredential, refundAddress)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Update DRep
     *
     * @param drepCredential DRep credential
     * @param anchor         anchor
     * @param redeemer       redeemer
     * @return ScriptTx
     */
    public ScriptTx updateDRep(@NonNull Credential drepCredential, Anchor anchor, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(DRepUpdateIntent
                .update(drepCredential, anchor)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Create a proposal
     *
     * @param govAction     gov action
     * @param returnAddress return address
     * @param anchor        anchor
     * @param redeemer      redeemer
     * @return ScriptTx
     */
    public ScriptTx createProposal(GovAction govAction, @NonNull String returnAddress, Anchor anchor, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(GovernanceProposalIntent
                .create(govAction, returnAddress, anchor)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Create a vote
     *
     * @param voter       voter
     * @param govActionId gov action id
     * @param vote        vote
     * @param anchor      anchor
     * @param redeemer    redeemer
     * @return ScriptTx
     */
    public ScriptTx createVote(@NonNull Voter voter, @NonNull GovActionId govActionId, @NonNull Vote vote, Anchor anchor, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(VotingIntent
                .vote(voter, govActionId, vote, anchor)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Delegate voting power to a DRep
     *
     * @param address  address to delegate
     * @param drep     DRep
     * @param redeemer redeemer
     * @return
     */
    public ScriptTx delegateVotingPowerTo(@NonNull Address address, @NonNull DRep drep, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(VotingDelegationIntent
                .delegate(address, drep)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Send change to the change address with the output datum hash.
     *
     * @param changeAddress change address
     * @param datumHash     output datum hash
     * @return ScriptTx
     */
    public ScriptTx withChangeAddress(String changeAddress, String datumHash) {
        if (changeData != null)
            throw new TxBuildException("Change data already set. Cannot set change data hash");
        this.changeAddress = changeAddress;
        this.changeDatahash = datumHash;
        return this;
    }

    /**
     * Get change address
     *
     * @return
     */
    @Override
    protected String getChangeAddress() {
        if (changeAddress != null)
            return changeAddress;
        else
            return null;
    }

    /**
     * Get from address
     *
     * @return
     */
    @Override
    protected String getFromAddress() {
        return fromAddress;
    }

    @Override
    protected Wallet getFromWallet() {
        return fromWallet;
    }

    void from(String address) {
        this.fromAddress = address;
    }

    void from(Wallet wallet) {
        this.fromWallet = wallet;
        // TODO fromAddress is not used in this scenarios, but it must be set to avoid breaking other things.
        this.fromAddress = this.fromWallet.getBaseAddressString(0);
    }

    @Override
    protected void postBalanceTx(Transaction transaction) {
//        if (spendingContexts != null && !spendingContexts.isEmpty()) {
        //Verify if redeemer indexes are correct, if not set the correct index
        verifyAndAdjustRedeemerIndexes(transaction);
//        }
    }

    @Override
    protected void preTxEvaluation(Transaction transaction) {
//        if (spendingContexts != null && !spendingContexts.isEmpty()) {
        //Verify if redeemer indexes are correct, if not set the correct index
        verifyAndAdjustRedeemerIndexes(transaction);
//        }
    }

    private void verifyAndAdjustRedeemerIndexes(Transaction transaction) {
        for (Redeemer redeemer : transaction.getWitnessSet().getRedeemers()) {
            if (redeemer.getTag() != RedeemerTag.Spend)
                continue;
            Optional<Utxo> scriptUtxo = getUtxoForRedeemer(redeemer);
            if (scriptUtxo.isPresent()) {
                int scriptInputIndex = RedeemerUtil.getScriptInputIndex(scriptUtxo.get(), transaction);
                if (redeemer.getIndex().intValue() != scriptInputIndex && scriptInputIndex != -1) {
                    redeemer.setIndex(scriptInputIndex);
                }
                log.debug("Sorting done for redeemer : " + redeemer);
            } else
                log.warn("No utxo found for redeemer. Something went wrong." + redeemer);
        }
    }

    //TODO -- check how to do this ??
    private Optional<Utxo> getUtxoForRedeemer(Redeemer redeemer) {
        return intentions.stream()
                .filter(intention -> intention instanceof ScriptCollectFromIntent)
                .map(intention -> ((ScriptCollectFromIntent) intention).getUtxoForRedeemer(redeemer))
                .findFirst().orElse(Optional.empty());

//        return spendingContexts.stream()
//                .filter(spendingContext -> spendingContext.getRedeemer() == redeemer) //object reference comparison
//                .findFirst()
//                .map(spendingContext -> spendingContext.getScriptUtxo());
//        return Optional.empty();
    }

    @Override
    protected void verifyData() {

    }

    @Override
    protected String getFeePayer() {
        return null;
    }

    @Override
    TxBuilder complete() {
        TxBuilder txBuilder = super.complete();
        return txBuilder;
    }

    /**
     * Get lazy UTXO strategies for resolution during execution.
     * This method is used by the watcher framework to resolve predicate-based UTXO selections.
     *
     * @return list of lazy UTXO strategies
     */
    public List<LazyUtxoStrategy> getLazyStrategies() {
        return lazyStrategies;
    }

}
