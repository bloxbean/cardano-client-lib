package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.address.Address;
import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.function.helper.MintUtil;
import com.bloxbean.cardano.client.function.helper.RedeemerUtil;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.transaction.spec.governance.DRep;
import com.bloxbean.cardano.client.transaction.spec.governance.Vote;
import com.bloxbean.cardano.client.transaction.spec.governance.Voter;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovAction;
import com.bloxbean.cardano.client.transaction.spec.governance.actions.GovActionId;
import com.bloxbean.cardano.client.util.Tuple;
import com.bloxbean.cardano.hdwallet.Wallet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class ScriptTx extends AbstractTx<ScriptTx> {
    protected List<PlutusScript> spendingValidators;
    protected List<PlutusScript> mintingValidators;
    protected List<PlutusScript> certValidators;
    protected List<PlutusScript> rewardValidators;
    protected List<PlutusScript> proposingValidators;
    protected List<PlutusScript> votingValidators;

    protected List<SpendingContext> spendingContexts;
    protected List<MintingContext> mintingContexts;

    protected List<TransactionInput> referenceInputs;

    protected String fromAddress;
    private StakeTx stakeTx;
    private GovTx govTx;

    public ScriptTx() {
        spendingContexts = new ArrayList<>();
        mintingContexts = new ArrayList<>();
        spendingValidators = new ArrayList<>();
        mintingValidators = new ArrayList<>();
        certValidators = new ArrayList<>();
        rewardValidators = new ArrayList<>();
        proposingValidators = new ArrayList<>();
        votingValidators = new ArrayList<>();

        stakeTx = new StakeTx();
        govTx = new GovTx();
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
        if (inputUtxos == null)
            inputUtxos = new ArrayList<>();
        inputUtxos.add(utxo);

        Redeemer _redeemer = Redeemer.builder()
                .tag(RedeemerTag.Spend)
                .data(redeemerData)
                .index(BigInteger.valueOf(1)) //dummy value
                .exUnits(ExUnits.builder()
                        .mem(BigInteger.valueOf(10000)) // Some dummy value
                        .steps(BigInteger.valueOf(10000))
                        .build())
                .build();

        SpendingContext spendingContext = new SpendingContext(utxo, _redeemer, datum);
        spendingContexts.add(spendingContext);
        return this;
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
        if (utxos == null)
            return this;

        utxos.forEach(utxo -> collectFrom(utxo, redeemerData, datum));
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
        if (utxos == null)
            return this;

        utxos.forEach(utxo -> collectFrom(utxo, redeemerData, null));
        return this;
    }

    /**
     * Add this utxo as input of the transaction.
     * This method doesn't add any redeemer. The utxo will only be added as input and balance will be calculated.
     *
     * @param utxo Utxo
     * @return ScriptTx
     */
    public ScriptTx collectFrom(Utxo utxo) {
        if (inputUtxos == null)
            inputUtxos = new ArrayList<>();
        inputUtxos.add(utxo);

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
        if (utxos == null)
            return this;

        utxos.forEach(utxo -> collectFrom(utxo));
        return this;
    }

    /**
     * Add utxo(s) as reference input(s) of the transaction.
     *
     * @param utxos
     * @return ScriptTx
     */
    public ScriptTx readFrom(Utxo... utxos) {
        for (Utxo utxo : utxos) {
            readFrom(utxo.getTxHash(), utxo.getOutputIndex());
        }
        return this;
    }

    /**
     * Add transaction input(s) as reference input(s) of the transaction.
     *
     * @param transactionInputs
     * @return ScriptTx
     */
    public ScriptTx readFrom(TransactionInput... transactionInputs) {
        for (TransactionInput transactionInput : transactionInputs) {
            readFrom(transactionInput.getTransactionId(), transactionInput.getIndex());
        }
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
        TransactionInput transactionInput = new TransactionInput(txHash, outputIndex);
        if (referenceInputs == null)
            referenceInputs = new ArrayList<>();

        if (!referenceInputs.contains(transactionInput))
            referenceInputs.add(transactionInput);
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
        Redeemer _redeemer = Redeemer.builder()
                .tag(RedeemerTag.Mint)
                .data(redeemer)
                .exUnits(ExUnits.builder()
                        .mem(BigInteger.valueOf(10000)) // Some dummy value
                        .steps(BigInteger.valueOf(10000))
                        .build())
                .build();

        MintingContext mintingContext = null;
        String policyId;
        try {
            policyId = script.getPolicyId();
            mintingContext = new MintingContext(policyId, assets, _redeemer);
        } catch (CborSerializationException e) {
            throw new TxBuildException("Error getting policy id from script", e);
        }

        mintingContexts.add(mintingContext);

        List<Amount> amounts = assets.stream()
                .map(asset -> Amount.asset(policyId, asset.getName(), asset.getValue()))
                .collect(Collectors.toList());

        if (receiver != null) {
            if (outputDatum != null)
                payToContract(receiver, amounts, outputDatum);
            else
                payToAddress(receiver, amounts);
        }

        addToMultiAssetList(script, assets);
        attachMintValidator(script);
        return this;
    }

    /**
     * Attach a spending validator script to the transaction
     *
     * @param plutusScript
     * @return ScriptTx
     */
    public ScriptTx attachSpendingValidator(PlutusScript plutusScript) {
        spendingValidators.add(plutusScript);
        return this;
    }

    /**
     * Attach a minting validator script to the transaction. This method is called from the mintAssets methods.
     *
     * @param plutusScript plutus script
     * @return ScriptTx
     */
    private ScriptTx attachMintValidator(PlutusScript plutusScript) {
        mintingValidators.add(plutusScript);
        return this;
    }

    /**
     * Attach a certificate validator script to the transaction
     * @param plutusScript plutus script
     * @return ScriptTx
     */
    public ScriptTx attachCertificateValidator(PlutusScript plutusScript) {
        certValidators.add(plutusScript);
        return this;
    }

    /**
     * Attach a reward validator script to the transaction
     * @param plutusScript plutus script
     * @return ScriptTx
     */
    public ScriptTx attachRewardValidator(PlutusScript plutusScript) {
        rewardValidators.add(plutusScript);
        return this;
    }

    /**
     * Attach a proposing validator script to the transaction
     * @param plutusScript plutus script
     * @return ScriptTx
     */
    public ScriptTx attachProposingValidator(PlutusScript plutusScript) {
        proposingValidators.add(plutusScript);
        return this;
    }

    /**
     * Attach a voting validator script to the transaction
     * @param plutusScript plutus script
     * @return ScriptTx
     */
    public ScriptTx attachVotingValidator(PlutusScript plutusScript) {
        votingValidators.add(plutusScript);
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
        stakeTx.deregisterStakeAddress(new Address(address), redeemer, null);
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
        stakeTx.deregisterStakeAddress(address, redeemer, null);
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
        stakeTx.deregisterStakeAddress(new Address(address), redeemer, refundAddr);
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
        stakeTx.delegateTo(new Address(address), poolId, redeemer);
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
        stakeTx.delegateTo(address, poolId, redeemer);
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
        stakeTx.withdraw(new Address(rewardAddress), amount, redeemer, null);
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
        stakeTx.withdraw(rewardAddress, amount, redeemer, null);
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
        stakeTx.withdraw(new Address(rewardAddress), amount, redeemer, receiver);
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
        stakeTx.withdraw(rewardAddress, amount, redeemer, receiver);
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
        govTx.registerDRep(drepCredential, anchor, redeemer);
        return this;
    }

    /**
     * Register DRep
     * @param drepCredential - DRep credential
     * @param redeemer - redeemer
     * @return ScriptTx
     */
    public ScriptTx registerDRep(@NonNull Credential drepCredential, PlutusData redeemer) {
        govTx.registerDRep(drepCredential, null, redeemer);
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
        govTx.unregisterDRep(drepCredential, refundAddress, refundAmount, redeemer);
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
        govTx.unregisterDRep(drepCredential, refundAddress, null, redeemer);
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
        govTx.updateDRep(drepCredential, anchor, redeemer);
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
        govTx.createProposal(govAction, returnAddress, anchor, redeemer);
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
        govTx.createVote(voter, govActionId, vote, anchor, redeemer);
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
        govTx.delegateVotingPowerTo(address, drep, redeemer);
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
        return null;
    }

    void from(String address) {
        this.fromAddress = address;
    }

    @Override
    protected void postBalanceTx(Transaction transaction) {
        if (spendingContexts != null && !spendingContexts.isEmpty()) {
            //Verify if redeemer indexes are correct, if not set the correct index
            verifyAndAdjustRedeemerIndexes(transaction);
        }
    }

    @Override
    protected void preTxEvaluation(Transaction transaction) {
        if (spendingContexts != null && !spendingContexts.isEmpty()) {
            //Verify if redeemer indexes are correct, if not set the correct index
            verifyAndAdjustRedeemerIndexes(transaction);
        }
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

    private Optional<Utxo> getUtxoForRedeemer(Redeemer redeemer) {
        return spendingContexts.stream()
                .filter(spendingContext -> spendingContext.getRedeemer() == redeemer) //object reference comparison
                .findFirst()
                .map(spendingContext -> spendingContext.getScriptUtxo());
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

        //Invoke common complete logic
        TxBuilder txBuilder = super.complete();

        txBuilder = txBuilder.andThen(prepareScriptCallContext());

        //stake, gov related
        txBuilder = txBuilder.andThen(stakeBuildTuple._2)
                .andThen(govBuildTuple._2);

        return txBuilder;
    }

    protected TxBuilder prepareScriptCallContext() {
        TxBuilder txBuilder = (context, txn) -> {
        };

        txBuilder = addReferenceInputs(txBuilder);

        txBuilder = txBuilderFromSpendingValidators(txBuilder);
        txBuilder = txBuilderFromMintingValidators(txBuilder);
        txBuilder = txBuilderFromValidators(txBuilder, certValidators);//txBuilderFromCertValidators(txBuilder);
        txBuilder = txBuilderFromValidators(txBuilder, rewardValidators); //txBuilderFromRewardValidators(txBuilder);
        txBuilder = txBuilderFromValidators(txBuilder, proposingValidators);//txBuilderFromProposingValidators(txBuilder);
        txBuilder = txBuilderFromValidators(txBuilder, votingValidators);

        return txBuilder;
    }

    private TxBuilder addReferenceInputs(TxBuilder txBuilder) {
        if (referenceInputs != null) {
            txBuilder = txBuilder.andThen((context, txn) -> {
                List<TransactionInput> txRefInputs = txn.getBody().getReferenceInputs();
                if (txRefInputs == null)
                    txn.getBody().setReferenceInputs(referenceInputs);
                else
                    txRefInputs.addAll(referenceInputs);
            });
        }
        return txBuilder;
    }

    private TxBuilder txBuilderFromSpendingValidators(TxBuilder txBuilder) {
        for (PlutusScript plutusScript : spendingValidators) {
            txBuilder =
                    txBuilder.andThen(((context, transaction) -> {
                        if (transaction.getWitnessSet() == null)
                            transaction.setWitnessSet(new TransactionWitnessSet());
                        if (plutusScript instanceof PlutusV1Script) {
                            if (!transaction.getWitnessSet().getPlutusV1Scripts().contains(plutusScript)) //To avoid duplicate script in list
                                transaction.getWitnessSet().getPlutusV1Scripts().add((PlutusV1Script) plutusScript);
                        } else if (plutusScript instanceof PlutusV2Script) {
                            if (!transaction.getWitnessSet().getPlutusV2Scripts().contains(plutusScript)) //To avoid duplicate script in list
                                transaction.getWitnessSet().getPlutusV2Scripts().add((PlutusV2Script) plutusScript);
                        } else if (plutusScript instanceof PlutusV3Script) {
                            if (!transaction.getWitnessSet().getPlutusV3Scripts().contains(plutusScript))
                                transaction.getWitnessSet().getPlutusV3Scripts().add((PlutusV3Script) plutusScript);
                        }
                    }));
        }

        for (SpendingContext spendingContext : spendingContexts) {
            txBuilder = txBuilder.andThen(((context, transaction) -> {
                if (transaction.getWitnessSet() == null) {
                    transaction.setWitnessSet(new TransactionWitnessSet());
                }
                if (spendingContext.datum != null) {
                    if (!transaction.getWitnessSet().getPlutusDataList().contains(spendingContext.datum))
                        transaction.getWitnessSet().getPlutusDataList().add(spendingContext.datum);
                }

                if (spendingContext.redeemer != null) {
                    int scriptInputIndex = RedeemerUtil.getScriptInputIndex(spendingContext.scriptUtxo, transaction);
                    if (scriptInputIndex == -1)
                        throw new TxBuildException("Script utxo is not found in transaction inputs : " + spendingContext.scriptUtxo.getTxHash());

                    //update script input index
                    spendingContext.getRedeemer().setIndex(scriptInputIndex);
                    transaction.getWitnessSet().getRedeemers().add(spendingContext.redeemer);
                }
            }));
        }
        return txBuilder;
    }

    private TxBuilder txBuilderFromMintingValidators(TxBuilder txBuilder) {
        for (PlutusScript plutusScript : mintingValidators) {
            txBuilder =
                    txBuilder.andThen(((context, transaction) -> {
                        if (transaction.getWitnessSet() == null)
                            transaction.setWitnessSet(new TransactionWitnessSet());
                        if (plutusScript instanceof PlutusV1Script) {
                            if (!transaction.getWitnessSet().getPlutusV1Scripts().contains(plutusScript)) //To avoid duplicate script in list
                                transaction.getWitnessSet().getPlutusV1Scripts().add((PlutusV1Script) plutusScript);
                        } else if (plutusScript instanceof PlutusV2Script) {
                            if (!transaction.getWitnessSet().getPlutusV2Scripts().contains(plutusScript)) //To avoid duplicate script in list
                                transaction.getWitnessSet().getPlutusV2Scripts().add((PlutusV2Script) plutusScript);
                        } else if (plutusScript instanceof PlutusV3Script) {
                            if (!transaction.getWitnessSet().getPlutusV3Scripts().contains(plutusScript))
                                transaction.getWitnessSet().getPlutusV3Scripts().add((PlutusV3Script) plutusScript);
                        }
                    }));
        }

        //Sort mint field in the transaction
        txBuilder = txBuilder.andThen((context, txn) -> {
            if (txn.getBody().getMint() != null) {
                List<MultiAsset> multiAssets = MintUtil.getSortedMultiAssets(txn.getBody().getMint());
                txn.getBody().setMint(multiAssets);
            }
        });

        //Add the redeemer to the transaction witness set
        for (MintingContext mintingContext : mintingContexts) {
            txBuilder = txBuilder.andThen(((context, transaction) -> {
                if (transaction.getWitnessSet() == null) {
                    transaction.setWitnessSet(new TransactionWitnessSet());
                }

                if (mintingContext.redeemer != null) {
                    List<MultiAsset> multiAssets = transaction.getBody().getMint();
                    int index = IntStream.range(0, multiAssets.size())
                            .filter(i -> mintingContext.getPolicyId().equals(multiAssets.get(i).getPolicyId()))
                            .findFirst()
                            .orElse(-1);

                    if (index == -1)
                        throw new TxBuildException("Policy id is not found in transaction mint : " + mintingContext.getPolicyId());

                    //update script input index
                    mintingContext.getRedeemer().setIndex(index);

                    transaction.getWitnessSet().getRedeemers()
                            .stream().filter(redeemer -> redeemer.getTag() == mintingContext.getRedeemer().getTag()
                                    && redeemer.getIndex() == mintingContext.getRedeemer().getIndex())
                            .findFirst()
                            .ifPresentOrElse(redeemer -> {
                                //Do nothing
                            }, () -> {
                                transaction.getWitnessSet().getRedeemers().add(mintingContext.redeemer);
                            });
                }
            }));
        }

        return txBuilder;
    }

    private TxBuilder txBuilderFromValidators(TxBuilder txBuilder, List<PlutusScript> validators) {
        if (validators == null)
            return txBuilder;

        for (PlutusScript plutusScript : validators) {
            txBuilder =
                    txBuilder.andThen(((context, transaction) -> {
                        if (transaction.getWitnessSet() == null)
                            transaction.setWitnessSet(new TransactionWitnessSet());
                        if (plutusScript instanceof PlutusV1Script) {
                            if (!transaction.getWitnessSet().getPlutusV1Scripts().contains(plutusScript)) //To avoid duplicate script in list
                                transaction.getWitnessSet().getPlutusV1Scripts().add((PlutusV1Script) plutusScript);
                        } else if (plutusScript instanceof PlutusV2Script) {
                            if (!transaction.getWitnessSet().getPlutusV2Scripts().contains(plutusScript)) //To avoid duplicate script in list
                                transaction.getWitnessSet().getPlutusV2Scripts().add((PlutusV2Script) plutusScript);
                        } else if (plutusScript instanceof PlutusV3Script) {
                            if (!transaction.getWitnessSet().getPlutusV3Scripts().contains(plutusScript))
                                transaction.getWitnessSet().getPlutusV3Scripts().add((PlutusV3Script) plutusScript);
                        }
                    }));
        }

        return txBuilder;
    }

    @Data
    @AllArgsConstructor
    static class SpendingContext {
        private Utxo scriptUtxo;
        private Redeemer redeemer;
        private PlutusData datum;
    }

    @Data
    @AllArgsConstructor
    static class MintingContext {
        private String policyId;
        private List<Asset> assets;
        private Redeemer redeemer;
    }
}
