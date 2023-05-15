package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.function.helper.MintCreators;
import com.bloxbean.cardano.client.function.helper.RedeemerUtil;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ScriptTx extends AbstractTx<ScriptTx> {
    protected List<PlutusScript> spendingValidators;
    protected List<PlutusScript> mintingValidators;

    protected List<SpendingContext> spendingContexts;
    protected List<MintingContext> mintingContexts;

    public ScriptTx() {
        spendingContexts = new ArrayList<>();
        mintingContexts = new ArrayList<>();
        spendingValidators = new ArrayList<>();
        mintingValidators = new ArrayList<>();
    }

    /**
     * Add given script utxo as input of the transaction. T
     * @param utxo Script utxo
     * @param redeemer Redeemer
     * @return ScriptTx
     */
    public ScriptTx collectFrom(Utxo utxo, PlutusData redeemer) {
        if (inputUtxos == null)
            inputUtxos = new ArrayList<>();
        inputUtxos.add(utxo);

        Redeemer _redeemer = Redeemer.builder()
                .tag(RedeemerTag.Spend)
                .data(redeemer)
                .index(BigInteger.valueOf(1)) //dummy value
                .exUnits(ExUnits.builder()
                        .mem(BigInteger.valueOf(100000000)) // Some dummy value
                        .steps(BigInteger.valueOf(100000000))
                        .build())
                .build();

        SpendingContext spendingContext = new SpendingContext(utxo, _redeemer, null);
        spendingContexts.add(spendingContext);
        return this;
    }

    //TODO : collectFrom(List<Utxo> utxos, PlutusData redeemer)
    //TODO: read from utxo  readFrom(Utxo utxo)

    /**
     * Mint asset with given script and redeemer
     * @param script plutus script
     * @param asset asset to mint
     * @param redeemer redeemer
     * @return ScriptTx
     */
    public ScriptTx mintAsset(PlutusScript script, Asset asset, PlutusData redeemer) {
        return mintAsset(script, List.of(asset), redeemer, null, null);
    }

    /**
     * Mint asset with given script and redeemer. The minted asset will be sent to the given receiver address
     * @param script plutus script
     * @param asset asset to mint
     * @param redeemer redeemer
     * @param receiver receiver address
     * @return ScriptTx
     */
    public ScriptTx mintAsset(PlutusScript script, Asset asset, PlutusData redeemer, String receiver) {
        return mintAsset(script, List.of(asset), redeemer, receiver, null);
    }

    /**
     * Mint assets with given script and redeemer. The minted assets will be sent to the given receiver address
     * @param script plutus script
     * @param assets assets to mint
     * @param redeemer redeemer
     * @param receiver receiver address
     * @return ScriptTx
     */
    public ScriptTx mintAsset(PlutusScript script, List<Asset> assets, PlutusData redeemer, String receiver) {
        return mintAsset(script, assets, redeemer, receiver, null);
    }

    /**
     * Mint assets with given script and redeemer. The minted assets will be sent to the given receiver address with output datum
     * @param script plutus script
     * @param assets assets to mint
     * @param redeemer redeemer
     * @param receiver receiver address
     * @param outputDatum output datum
     * @return ScriptTx
     */
    public ScriptTx mintAsset(PlutusScript script, List<Asset> assets, PlutusData redeemer, String receiver, PlutusData outputDatum) {
        Redeemer _redeemer = Redeemer.builder()
                .tag(RedeemerTag.Mint)
                .data(redeemer)
                .exUnits(ExUnits.builder()
                        .mem(BigInteger.valueOf(100000000)) // Some dummy value
                        .steps(BigInteger.valueOf(100000000))
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

        attachMintValidator(script);
        return this;
    }

    /**
     * Attach a spending validator script to the transaction
     * @param plutusScript
     * @return ScriptTx
     */
    public ScriptTx attachSpendingValidator(PlutusScript plutusScript) {
        spendingValidators.add(plutusScript);
        return this;
    }

    /**
     * Attach a minting validator script to the transaction. This method is called from the mintAssets methods.
     * @param plutusScript plutus script
     * @return ScriptTx
     */
    private ScriptTx attachMintValidator(PlutusScript plutusScript) {
        mintingValidators.add(plutusScript);
        return this;
    }

//    public ScriptTx attachCertificateValidator(PlutusScript plutusScript) {
//        this.script = plutusScript;
//        this.redeemerTag = RedeemerTag.Cert;
//        return this;
//    }
//
//    public ScriptTx attachRewardValidator(PlutusScript plutusScript) {
//        this.script = plutusScript;
//        this.redeemerTag = RedeemerTag.Reward;
//        return this;
//    }

//    public ScriptTx withDatum(PlutusData datum) {
//        this.datum = datum;
//        return this;
//    }

    /**
     * Send change to the change address with the output datum.
     * @param changeAddress change address
     * @param plutusData output datum
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
     * Send change to the change address with the output datum hash.
     * @param changeAddress
     * @param datumHash
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
     * @return
     */
    @Override
    protected String getFromAddress() {
        return null;
    }

    @Override
    protected void postBalanceTx(Transaction transaction) {
        if (spendingValidators != null && !spendingValidators.isEmpty()) {
            //Verify if redeemer indexes are correct, if not set the correct index
            for (Redeemer redeemer : transaction.getWitnessSet().getRedeemers()) {
                if (redeemer.getTag() != RedeemerTag.Spend)
                    continue;
                Optional<Utxo> scriptUtxo = getUtxoForRedeemer(redeemer);
                if (scriptUtxo.isPresent()) {
                    int scriptInputIndex = RedeemerUtil.getScriptInputIndex(scriptUtxo.get(), transaction);
                    if (redeemer.getIndex().intValue() != scriptInputIndex && scriptInputIndex != -1) {
                        redeemer.setIndex(BigInteger.valueOf(scriptInputIndex));
                    }
                } else
                    throw new TxBuildException("No utxo found for redeemer. Something went wrong." + redeemer);
            }
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
        TxBuilder txBuilder = super.complete();
        return txBuilder.andThen(prepareScriptCallContext());
    }

    protected TxBuilder prepareScriptCallContext() {
        TxBuilder txBuilder = (context, txn) -> {
        };
        txBuilder = txBuilderFromSpendingValidators(txBuilder);
        txBuilder = txBuilderFromMintingValidators(txBuilder);

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
                        }
                    }));
        }

        for (SpendingContext spendingContext : spendingContexts) {
            txBuilder = txBuilder.andThen(((context, transaction) -> {
                if (transaction.getWitnessSet() == null) {
                    transaction.setWitnessSet(new TransactionWitnessSet());
                }
                if (spendingContext.datum != null)
                    transaction.getWitnessSet().getPlutusDataList().add(spendingContext.datum);

                if (spendingContext.redeemer != null) {
                    int scriptInputIndex = RedeemerUtil.getScriptInputIndex(spendingContext.scriptUtxo, transaction);
                    if (scriptInputIndex == -1)
                        throw new TxBuildException("Script utxo is not found in transaction inputs : " + spendingContext.scriptUtxo.getTxHash());

                    //update script input index
                    spendingContext.getRedeemer().setIndex(BigInteger.valueOf(scriptInputIndex));
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
                        }
                    }));
        }

        for (MintingContext mintingContext : mintingContexts) {
            txBuilder = txBuilder.andThen(((context, transaction) -> {
                if (transaction.getWitnessSet() == null) {
                    transaction.setWitnessSet(new TransactionWitnessSet());
                }

                if (mintingContext.redeemer != null) {
                    //update script input index
                    mintingContext.getRedeemer().setIndex(BigInteger.ZERO);
                    transaction.getWitnessSet().getRedeemers().add(mintingContext.redeemer);
                }
            }));

            //Find the minting validator for the policy id
            Optional<PlutusScript> mintingValidator = mintingValidators.stream()
                    .filter(plutusScript -> {
                        try {
                            return plutusScript.getPolicyId().equals(mintingContext.getPolicyId());
                        } catch (CborSerializationException e) {
                            throw new TxBuildException("Error getting policy id from the script");
                        }
                    }).findFirst();

            if (mintingValidator.isPresent()) {
                txBuilder = txBuilder.andThen(((context, txn) -> {
                    MultiAsset multiAsset = MultiAsset.builder()
                            .policyId(mintingContext.getPolicyId())
                            .assets(mintingContext.getAssets())
                            .build();
                    MintCreators.mintCreator(mintingValidator.get(), multiAsset, false).apply(context, txn);
                }));
            } else {
                throw new TxBuildException("No minting validator found for policy id : " + mintingContext.getPolicyId());
            }
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
