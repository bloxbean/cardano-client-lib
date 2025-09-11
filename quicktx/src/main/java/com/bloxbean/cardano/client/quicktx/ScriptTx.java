package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.function.helper.MintUtil;
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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;

@Slf4j
public class ScriptTx extends AbstractTx<ScriptTx> {
//    protected List<PlutusScript> spendingValidators;
//    protected List<PlutusScript> mintingValidators;
//    protected List<PlutusScript> certValidators;
//    protected List<PlutusScript> rewardValidators;
//    protected List<PlutusScript> proposingValidators;
//    protected List<PlutusScript> votingValidators;
//
//    protected List<SpendingContext> spendingContexts;
//    protected List<MintingContext> mintingContexts;

    protected List<LazyUtxoStrategy> lazyStrategies;

//    protected List<TransactionInput> referenceInputs;

    protected String fromAddress;
    protected Wallet fromWallet;
//    private StakeTx stakeTx;
//    private GovTx govTx;

    public ScriptTx() {
//        spendingContexts = new ArrayList<>();
//        mintingContexts = new ArrayList<>();
        lazyStrategies = new ArrayList<>();
//        spendingValidators = new ArrayList<>();
//        mintingValidators = new ArrayList<>();
//        certValidators = new ArrayList<>();
//        rewardValidators = new ArrayList<>();
//        proposingValidators = new ArrayList<>();
//        votingValidators = new ArrayList<>();

//        stakeTx = new StakeTx();
//        govTx = new GovTx();
    }

    /**
     * Enable intention recording for all sub-transactions.
     * This allows capturing stake and governance operations for YAML serialization.
     */
//    public void enableIntentionRecording() {
//        stakeTx.enableIntentionRecording();
//        govTx.enableIntentionRecording();
//    }

    /**
     * Get all intentions from this transaction and its sub-transactions.
     * @return combined list of all intentions
     */
    @Override
    public java.util.List<TxIntention> getIntentions() {
        java.util.List<TxIntention> all = new java.util.ArrayList<>();
        if (this.intentions != null) all.addAll(this.intentions);
//        if (stakeTx != null && stakeTx.getIntentions() != null) all.addAll(stakeTx.getIntentions());
//        if (govTx != null && govTx.getIntentions() != null) all.addAll(govTx.getIntentions());
        return all;
    }

    /**
     * Add given script utxo as input of the transaction.
     *
     * @param utxo Script utxo
     * @param redeemerData Redeemer data
     * @param datum Datum object. This will be added to witness list
     * @return ScriptTx
     */
    public ScriptTx collectFrom(Utxo utxo, PlutusData redeemerData, PlutusData datum) {
        return collectFrom(List.of(utxo), redeemerData, datum);
    }

    /**
     * Add given script utxos as inputs of the transaction.
     *
     * @param utxos Script utxos
     * @param redeemerData Redeemer data
     * @param datum Datum object. This will be added to witness list
     * @return ScriptTx
     */
    public ScriptTx collectFrom(List<Utxo> utxos, PlutusData redeemerData, PlutusData datum) {
        if (utxos == null || utxos.isEmpty())
            return this;

        // Create intention using the factory method that stores original objects
        ScriptCollectFromIntention intention = ScriptCollectFromIntention.collectFrom(utxos, redeemerData, datum);

        if (intentions == null) {
            intentions = new ArrayList<>();
        }
        intentions.add(intention);

        return this;
    }

    /**
     * Add given script utxo as input of the transaction.
     *
     * @param utxo     Script utxo
     * @param redeemerData Redeemer data
     * @return ScriptTx
     */
    public ScriptTx collectFrom(Utxo utxo, PlutusData redeemerData) {
        return collectFrom(utxo, redeemerData, null);
    }

    /**
     * Add given script utxos as inputs of the transaction.
     *
     * @param utxos    Script utxos
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
        ScriptCollectFromIntention intention = ScriptCollectFromIntention.collectFrom(utxo);
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
        ScriptCollectFromIntention intention = ScriptCollectFromIntention.collectFrom(utxos, null, null);
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
     * @param redeemerData redeemer data
     * @param datum datum object
     * @return ScriptTx
     */
    public ScriptTx collectFrom(String scriptAddress, Predicate<Utxo> utxoPredicate, PlutusData redeemerData, PlutusData datum) {
        // TODO(yaml): Predicate-based collectFrom uses runtime-only lazy strategies and is not serialized to YAML yet.
        // Consider adding a predicate registry or DSL in a future phase for YAML support.
        this.lazyStrategies.add(new SingleUtxoPredicateStrategy(scriptAddress, utxoPredicate, redeemerData, datum));
        return this;
    }

    /**
     * Add script UTXOs selected by predicate as inputs of the transaction.
     * The predicate will be applied to UTXOs at the script address during execution.
     *
     * @param scriptAddress the script address to query UTXOs from
     * @param utxoPredicate predicate to select UTXOs
     * @param redeemerData redeemer data
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
     * @param redeemerData redeemer data
     * @param datum datum object
     * @return ScriptTx
     */
    public ScriptTx collectFromList(String scriptAddress, Predicate<List<Utxo>> listPredicate, PlutusData redeemerData, PlutusData datum) {
        // TODO(yaml): Predicate-based collectFromList uses runtime-only lazy strategies and is not serialized to YAML yet.
        // Consider registry/DSL or snapshot+hints for future YAML support.
        this.lazyStrategies.add(new ListUtxoPredicateStrategy(scriptAddress, listPredicate, redeemerData, datum));
        return this;
    }

    /**
     * Add script UTXOs selected by list predicate as inputs of the transaction.
     * The list predicate receives all UTXOs at the script address and returns filtered selection.
     *
     * @param scriptAddress the script address to query UTXOs from
     * @param listPredicate predicate to select UTXOs from the complete list
     * @param redeemerData redeemer data
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
        com.bloxbean.cardano.client.quicktx.intent.ReferenceInputIntention intention = new com.bloxbean.cardano.client.quicktx.intent.ReferenceInputIntention();
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
        com.bloxbean.cardano.client.quicktx.intent.ReferenceInputIntention intention = new com.bloxbean.cardano.client.quicktx.intent.ReferenceInputIntention();
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
        com.bloxbean.cardano.client.quicktx.intent.ReferenceInputIntention intention = new com.bloxbean.cardano.client.quicktx.intent.ReferenceInputIntention();
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
     * @param script plutus script
     * @param assets assets to mint or burn
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
        ScriptMintingIntention intention =
                ScriptMintingIntention.of(script, assets, redeemer, receiver, outputDatum);
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
        intentions.add(ScriptValidatorAttachmentIntention
                .of(RedeemerTag.Spend, plutusScript));
        return this;
    }

    /**
     * Attach a minting validator script to the transaction. This method is called from the mintAssets methods.
     *
     * @param plutusScript plutus script
     * @return ScriptTx
     */
    private ScriptTx attachMintValidator(PlutusScript plutusScript) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(com.bloxbean.cardano.client.quicktx.intent.ScriptValidatorAttachmentIntention
                .of(RedeemerTag.Mint, plutusScript));
        return this;
    }

    /**
     * Attach a certificate validator script to the transaction
     * @param plutusScript plutus script
     * @return ScriptTx
     */
    public ScriptTx attachCertificateValidator(PlutusScript plutusScript) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(com.bloxbean.cardano.client.quicktx.intent.ScriptValidatorAttachmentIntention
                .of(com.bloxbean.cardano.client.plutus.spec.RedeemerTag.Cert, plutusScript));
        return this;
    }

    /**
     * Attach a reward validator script to the transaction
     * @param plutusScript plutus script
     * @return ScriptTx
     */
    public ScriptTx attachRewardValidator(PlutusScript plutusScript) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(com.bloxbean.cardano.client.quicktx.intent.ScriptValidatorAttachmentIntention
                .of(com.bloxbean.cardano.client.plutus.spec.RedeemerTag.Reward, plutusScript));
        return this;
    }

    /**
     * Attach a proposing validator script to the transaction
     * @param plutusScript plutus script
     * @return ScriptTx
     */
    public ScriptTx attachProposingValidator(PlutusScript plutusScript) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(com.bloxbean.cardano.client.quicktx.intent.ScriptValidatorAttachmentIntention
                .of(com.bloxbean.cardano.client.plutus.spec.RedeemerTag.Proposing, plutusScript));
        return this;
    }

    /**
     * Attach a voting validator script to the transaction
     * @param plutusScript plutus script
     * @return ScriptTx
     */
    public ScriptTx attachVotingValidator(PlutusScript plutusScript) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(com.bloxbean.cardano.client.quicktx.intent.ScriptValidatorAttachmentIntention
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
        intentions.add(StakeDeregistrationIntention
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
        intentions.add(StakeDeregistrationIntention
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
        intentions.add(StakeDeregistrationIntention
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
        intentions.add(com.bloxbean.cardano.client.quicktx.intent.StakeDelegationIntention
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
        intentions.add(com.bloxbean.cardano.client.quicktx.intent.StakeDelegationIntention
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
        intentions.add(StakeWithdrawalIntention
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
        intentions.add(StakeWithdrawalIntention
                .withdraw(rewardAddress.toBech32(), amount)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Withdraw rewards from a stake address
     * @param rewardAddress stake address to withdraw rewards from
     * @param amount   amount to withdraw
     * @param redeemer redeemer
     * @param receiver receiver address
     * @return
     */
    public ScriptTx withdraw(@NonNull String rewardAddress, @NonNull BigInteger amount, PlutusData redeemer, String receiver) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(com.bloxbean.cardano.client.quicktx.intent.StakeWithdrawalIntention
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
        intentions.add(StakeWithdrawalIntention
                .withdraw(rewardAddress.toBech32(), amount, receiver)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Register DRep
     * @param drepCredential DRep credential
     * @param anchor anchor
     * @param redeemer redeemer
     * @return ScriptTx
     */
    public ScriptTx registerDRep(@NonNull Credential drepCredential, Anchor anchor, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(DRepRegistrationIntention
                .register(drepCredential, anchor)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Register DRep
     * @param drepCredential - DRep credential
     * @param redeemer - redeemer
     * @return ScriptTx
     */
    public ScriptTx registerDRep(@NonNull Credential drepCredential, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(DRepRegistrationIntention
                .register(drepCredential)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Unregister DRep
     * @param drepCredential DRep credential
     * @param refundAddress refund address
     * @param refundAmount refund amount
     * @param redeemer redeemer
     * @return ScriptTx
     */
    public ScriptTx unRegisterDRep(@NonNull Credential drepCredential, String refundAddress, BigInteger refundAmount, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(DRepDeregistrationIntention
                .deregister(drepCredential, refundAddress, refundAmount)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Unregister DRep
     * @param drepCredential DRep credential
     * @param refundAddress refund address
     * @param redeemer redeemer
     * @return ScriptTx
     */
    public ScriptTx unRegisterDRep(@NonNull Credential drepCredential, String refundAddress, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(com.bloxbean.cardano.client.quicktx.intent.DRepDeregistrationIntention
                .deregister(drepCredential, refundAddress)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Update DRep
     * @param drepCredential DRep credential
     * @param anchor anchor
     * @param redeemer redeemer
     * @return ScriptTx
     */
    public ScriptTx updateDRep(@NonNull Credential drepCredential, Anchor anchor, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(com.bloxbean.cardano.client.quicktx.intent.DRepUpdateIntention
                .update(drepCredential, anchor)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Create a proposal
     * @param govAction gov action
     * @param returnAddress return address
     * @param anchor anchor
     * @param redeemer redeemer
     * @return ScriptTx
     */
    public ScriptTx createProposal(GovAction govAction, @NonNull String returnAddress, Anchor anchor, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(com.bloxbean.cardano.client.quicktx.intent.GovernanceProposalIntention
                .create(govAction, returnAddress, anchor)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Create a vote
     * @param voter voter
     * @param govActionId gov action id
     * @param vote vote
     * @param anchor anchor
     * @param redeemer redeemer
     * @return ScriptTx
     */
    public ScriptTx createVote(@NonNull Voter voter, @NonNull GovActionId govActionId, @NonNull Vote vote, Anchor anchor, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(com.bloxbean.cardano.client.quicktx.intent.VotingIntention
                .vote(voter, govActionId, vote, anchor)
                .toBuilder()
                .redeemer(redeemer)
                .build());
        return this;
    }

    /**
     * Delegate voting power to a DRep
     * @param address address to delegate
     * @param drep DRep
     * @param redeemer redeemer
     * @return
     */
    public ScriptTx delegateVotingPowerTo(@NonNull Address address, @NonNull DRep drep, PlutusData redeemer) {
        if (intentions == null) intentions = new ArrayList<>();
        intentions.add(com.bloxbean.cardano.client.quicktx.intent.VotingDelegationIntention
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
     * @param datumHash output datum hash
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
//        return spendingContexts.stream()
//                .filter(spendingContext -> spendingContext.getRedeemer() == redeemer) //object reference comparison
//                .findFirst()
//                .map(spendingContext -> spendingContext.getScriptUtxo());
        return Optional.empty();
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

//        if (this.intentions != null && !this.intentions.isEmpty()) {
//            //Check TxInputIntentions for lazy UTXO strategies
//            var lazyUtxoStrategies = this.intentions.stream()
//                    .filter(it -> it instanceof TxInputIntention)
//                    .map(it -> (TxInputIntention) it)
//                    .map(txInputIntention -> txInputIntention.utxoStrategy())
//                    .collect(java.util.stream.Collectors.toList());
//            if (!lazyUtxoStrategies.isEmpty()) {
//                this.lazyStrategies.addAll(lazyUtxoStrategies);
//            }
//        }
//
//        if (lazyStrategies != null && !lazyStrategies.isEmpty()) {
//            this.lazyUtxoResolver = (supplier) -> {
//                List<Utxo> resolved = new ArrayList<>();
//                for (LazyUtxoStrategy strategy : new ArrayList<>(lazyStrategies)) {
//                    try {
//                        List<Utxo> strategyUtxos = strategy.resolve(supplier);
//                        if (!strategyUtxos.isEmpty()) {
//                            resolved.addAll(strategyUtxos);
//                            // Add resolved UTXOs to ScriptTx using regular collectFrom
//                           // this.collectFrom(strategyUtxos, strategy.getRedeemer(), strategy.getDatum());
//                        }
//                    } catch (Exception e) {
//                        throw new TxBuildException("Failed to resolve lazy UTXO strategy: " + e.getMessage(), e);
//                    }
//                }
//                // Clear lazy strategies after resolution
//                lazyStrategies.clear();
//                return resolved;
//            };
//        }

        /**
        // Pre-process script collect intentions to set inputs and spending contexts before input selection
        if (this.intentions != null && !this.intentions.isEmpty()) {
            java.util.List<com.bloxbean.cardano.client.quicktx.plan.TxIntention> collectIntents = this.intentions.stream()
                    .filter(it -> "script_collect_from".equals(it.getType()))
                    .collect(java.util.stream.Collectors.toList());
            if (!collectIntents.isEmpty()) {
                IntentProcessor.processIntentions(this, collectIntents);
            }
        }

        //stake related
        Tuple<List<DepositRefundContext>, TxBuilder> stakeBuildTuple =
                stakeTx.build(getFromAddress(), getChangeAddress());

        //Add stake deposit refund context
        addDepositRefundContext(stakeBuildTuple._1);

        //gov related
        Tuple<List<DepositRefundContext>, TxBuilder> govBuildTuple =
                govTx.build(getFromAddress(), getChangeAddress());

        //Add gov deposit refund context
        addDepositRefundContext(govBuildTuple._1);

        // Set up lazy UTXO resolver if we have lazy strategies
        if (lazyStrategies != null && !lazyStrategies.isEmpty()) {
            this.lazyUtxoResolver = (supplier) -> {
                List<Utxo> resolved = new ArrayList<>();
                for (LazyUtxoStrategy strategy : new ArrayList<>(lazyStrategies)) {
                    try {
                        List<Utxo> strategyUtxos = strategy.resolve(supplier);
                        if (!strategyUtxos.isEmpty()) {
                            resolved.addAll(strategyUtxos);
                            // Add resolved UTXOs to ScriptTx using regular collectFrom
                            this.collectFrom(strategyUtxos, strategy.getRedeemer(), strategy.getDatum());
                        }
                    } catch (Exception e) {
                        throw new TxBuildException("Failed to resolve lazy UTXO strategy: " + e.getMessage(), e);
                    }
                }
                // Clear lazy strategies after resolution
                lazyStrategies.clear();
                return resolved;
            };
        }
         **/

        //Invoke common complete logic - AbstractTx will handle lazyUtxoResolver
        TxBuilder txBuilder = super.complete();

        /**
        // Continue with script-specific building
        txBuilder = txBuilder.andThen(prepareScriptCallContext());

        //stake, gov related
        txBuilder = txBuilder.andThen(stakeBuildTuple._2)
                .andThen(govBuildTuple._2);
        **/
        return txBuilder;
    }

//    protected TxBuilder prepareScriptCallContext() {
//        TxBuilder txBuilder = (context, txn) -> {
//        };
//
//        txBuilder = addReferenceInputs(txBuilder);
//
//       // txBuilder = txBuilderFromSpendingValidators(txBuilder);
////        txBuilder = txBuilderFromMintingValidators(txBuilder);
////        txBuilder = txBuilderFromValidators(txBuilder, certValidators);//txBuilderFromCertValidators(txBuilder);
////        txBuilder = txBuilderFromValidators(txBuilder, rewardValidators); //txBuilderFromRewardValidators(txBuilder);
////        txBuilder = txBuilderFromValidators(txBuilder, proposingValidators);//txBuilderFromProposingValidators(txBuilder);
////        txBuilder = txBuilderFromValidators(txBuilder, votingValidators);
//
//        return txBuilder;
//    }

//    private TxBuilder addReferenceInputs(TxBuilder txBuilder) {
//        if (referenceInputs != null) {
//            txBuilder = txBuilder.andThen((context, txn) -> {
//                List<TransactionInput> txRefInputs = txn.getBody().getReferenceInputs();
//                if (txRefInputs == null)
//                    txn.getBody().setReferenceInputs(referenceInputs);
//                else
//                    txRefInputs.addAll(referenceInputs);
//            });
//        }
//        return txBuilder;
//    }

//    private TxBuilder txBuilderFromSpendingValidators(TxBuilder txBuilder) {
//        /**
//        for (PlutusScript plutusScript : spendingValidators) {
//            txBuilder =
//                    txBuilder.andThen(((context, transaction) -> {
//                        if (transaction.getWitnessSet() == null)
//                            transaction.setWitnessSet(new TransactionWitnessSet());
//                        if (plutusScript instanceof PlutusV1Script) {
//                            if (!transaction.getWitnessSet().getPlutusV1Scripts().contains(plutusScript)) //To avoid duplicate script in list
//                                transaction.getWitnessSet().getPlutusV1Scripts().add((PlutusV1Script) plutusScript);
//                        } else if (plutusScript instanceof PlutusV2Script) {
//                            if (!transaction.getWitnessSet().getPlutusV2Scripts().contains(plutusScript)) //To avoid duplicate script in list
//                                transaction.getWitnessSet().getPlutusV2Scripts().add((PlutusV2Script) plutusScript);
//                        } else if (plutusScript instanceof PlutusV3Script) {
//                            if (!transaction.getWitnessSet().getPlutusV3Scripts().contains(plutusScript))
//                                transaction.getWitnessSet().getPlutusV3Scripts().add((PlutusV3Script) plutusScript);
//                        }
//                    }));
//        }
//
//
//        txBuilder = txBuilder.andThen(((context, transaction) -> {
//            for (SpendingContext spendingContext : spendingContexts) {
//                if (transaction.getWitnessSet() == null) {
//                    transaction.setWitnessSet(new TransactionWitnessSet());
//                }
//                if (spendingContext.datum != null) {
//                    if (!transaction.getWitnessSet().getPlutusDataList().contains(spendingContext.datum))
//                        transaction.getWitnessSet().getPlutusDataList().add(spendingContext.datum);
//                }
//
//                if (spendingContext.redeemer != null) {
//                    int scriptInputIndex = RedeemerUtil.getScriptInputIndex(spendingContext.scriptUtxo, transaction);
//                    if (scriptInputIndex == -1)
//                        throw new TxBuildException("Script utxo is not found in transaction inputs : " + spendingContext.scriptUtxo.getTxHash());
//
//                    //update script input index
//                    spendingContext.getRedeemer().setIndex(scriptInputIndex);
//                    transaction.getWitnessSet().getRedeemers().add(spendingContext.redeemer);
//                }
//            }
//        }));
//         **/
//
//        return txBuilder;
//    }

//    private TxBuilder txBuilderFromMintingValidators(TxBuilder txBuilder) {
//        for (PlutusScript plutusScript : mintingValidators) {
//            txBuilder =
//                    txBuilder.andThen(((context, transaction) -> {
//                        if (transaction.getWitnessSet() == null)
//                            transaction.setWitnessSet(new TransactionWitnessSet());
//                        if (plutusScript instanceof PlutusV1Script) {
//                            if (!transaction.getWitnessSet().getPlutusV1Scripts().contains(plutusScript)) //To avoid duplicate script in list
//                                transaction.getWitnessSet().getPlutusV1Scripts().add((PlutusV1Script) plutusScript);
//                        } else if (plutusScript instanceof PlutusV2Script) {
//                            if (!transaction.getWitnessSet().getPlutusV2Scripts().contains(plutusScript)) //To avoid duplicate script in list
//                                transaction.getWitnessSet().getPlutusV2Scripts().add((PlutusV2Script) plutusScript);
//                        } else if (plutusScript instanceof PlutusV3Script) {
//                            if (!transaction.getWitnessSet().getPlutusV3Scripts().contains(plutusScript))
//                                transaction.getWitnessSet().getPlutusV3Scripts().add((PlutusV3Script) plutusScript);
//                        }
//                    }));
//        }
//
//        //Sort mint field in the transaction
//        txBuilder = txBuilder.andThen((context, txn) -> {
//            if (txn.getBody().getMint() != null) {
//                List<MultiAsset> multiAssets = MintUtil.getSortedMultiAssets(txn.getBody().getMint());
//                txn.getBody().setMint(multiAssets);
//            }
//        });
//
//        //Add the redeemer to the transaction witness set
//        for (MintingContext mintingContext : mintingContexts) {
//            txBuilder = txBuilder.andThen(((context, transaction) -> {
//                if (transaction.getWitnessSet() == null) {
//                    transaction.setWitnessSet(new TransactionWitnessSet());
//                }
//
//                if (mintingContext.redeemer != null) {
//                    List<MultiAsset> multiAssets = transaction.getBody().getMint();
//                    int index = IntStream.range(0, multiAssets.size())
//                            .filter(i -> mintingContext.getPolicyId().equals(multiAssets.get(i).getPolicyId()))
//                            .findFirst()
//                            .orElse(-1);
//
//                    if (index == -1)
//                        throw new TxBuildException("Policy id is not found in transaction mint : " + mintingContext.getPolicyId());
//
//                    //update script input index
//                    mintingContext.getRedeemer().setIndex(index);
//
//                    transaction.getWitnessSet().getRedeemers()
//                            .stream().filter(redeemer -> redeemer.getTag() == mintingContext.getRedeemer().getTag()
//                                    && redeemer.getIndex() == mintingContext.getRedeemer().getIndex())
//                            .findFirst()
//                            .ifPresentOrElse(redeemer -> {
//                                //Do nothing
//                            }, () -> {
//                                transaction.getWitnessSet().getRedeemers().add(mintingContext.redeemer);
//                            });
//                }
//            }));
//        }
//
//        return txBuilder;
//    }
//
//    private TxBuilder txBuilderFromValidators(TxBuilder txBuilder, List<PlutusScript> validators) {
//        if (validators == null)
//            return txBuilder;
//
//        for (PlutusScript plutusScript : validators) {
//            txBuilder =
//                    txBuilder.andThen(((context, transaction) -> {
//                        if (transaction.getWitnessSet() == null)
//                            transaction.setWitnessSet(new TransactionWitnessSet());
//                        if (plutusScript instanceof PlutusV1Script) {
//                            if (!transaction.getWitnessSet().getPlutusV1Scripts().contains(plutusScript)) //To avoid duplicate script in list
//                                transaction.getWitnessSet().getPlutusV1Scripts().add((PlutusV1Script) plutusScript);
//                        } else if (plutusScript instanceof PlutusV2Script) {
//                            if (!transaction.getWitnessSet().getPlutusV2Scripts().contains(plutusScript)) //To avoid duplicate script in list
//                                transaction.getWitnessSet().getPlutusV2Scripts().add((PlutusV2Script) plutusScript);
//                        } else if (plutusScript instanceof PlutusV3Script) {
//                            if (!transaction.getWitnessSet().getPlutusV3Scripts().contains(plutusScript))
//                                transaction.getWitnessSet().getPlutusV3Scripts().add((PlutusV3Script) plutusScript);
//                        }
//                    }));
//        }
//
//        return txBuilder;
//    }

    /**
     * Get lazy UTXO strategies for resolution during execution.
     * This method is used by the watcher framework to resolve predicate-based UTXO selections.
     *
     * @return list of lazy UTXO strategies
     */
    public List<LazyUtxoStrategy> getLazyStrategies() {
        return lazyStrategies;
    }


//    @Data
//    @AllArgsConstructor
//    static class SpendingContext {
//        private Utxo scriptUtxo;
//        private Redeemer redeemer;
//        private PlutusData datum;
//    }
//
//    @Data
//    @AllArgsConstructor
//    static class MintingContext {
//        private String policyId;
//        private List<Asset> assets;
//        private Redeemer redeemer;
//    }
}
