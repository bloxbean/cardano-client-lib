package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.function.helper.RedeemerUtil;
import com.bloxbean.cardano.client.function.helper.ScriptCallContextProviders;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.transaction.spec.Transaction;

import java.math.BigInteger;
import java.util.List;


public class ScriptTx extends AbstractTx<ScriptTx> {
    protected PlutusScript script;
    protected String scriptAddress;

    protected PlutusData datum;
    protected PlutusData redeemer;
    protected RedeemerTag redeemerTag = RedeemerTag.Spend;

    protected boolean mainnet;

    protected Utxo scriptUtxo;

    public ScriptTx() {

    }

    public ScriptTx collectFrom(Utxo utxo, PlutusData redeemer) {
        this.inputUtxos = List.of(utxo);
        this.scriptUtxo = utxo;
        this.redeemer = redeemer;
        return this;
    }

    public ScriptTx attachSpendingValidator(PlutusScript plutusScript) {
        this.script = plutusScript;
        this.redeemerTag = RedeemerTag.Spend;
        return this;
    }

    public ScriptTx attachMintValidator(PlutusScript plutusScript) {
        this.script = plutusScript;
        this.redeemerTag = RedeemerTag.Mint;
        return this;
    }

    public ScriptTx attachCertificateValidator(PlutusScript plutusScript) {
        this.script = plutusScript;
        this.redeemerTag = RedeemerTag.Cert;
        return this;
    }

    public ScriptTx attachRewardValidator(PlutusScript plutusScript) {
        this.script = plutusScript;
        this.redeemerTag = RedeemerTag.Reward;
        return this;
    }

    public ScriptTx withDatum(PlutusData datum) {
        this.datum = datum;
        return this;
    }

    public ScriptTx withChangeAddress(String changeAddress, PlutusData plutusData) {
        if (changeDatahash != null)
            throw new TxBuildException("Change data hash already set. Cannot set change data");
        this.changeAddress = changeAddress;
        this.changeData = plutusData;
        return this;
    }

    public ScriptTx withChangeAddress(String changeAddress, String datumHash) {
        if (changeData != null)
            throw new TxBuildException("Change data already set. Cannot set change data hash");
        this.changeAddress = changeAddress;
        this.changeDatahash = datumHash;
        return this;
    }

    @Override
    protected String getChangeAddress() {
        if (changeAddress != null)
            return changeAddress;
        else
            return scriptAddress;
    }

    @Override
    protected String getFromAddress() {
        return scriptAddress;
    }

    @Override
    protected void postBalanceTx(Transaction transaction) {
        //Verify if redeemer indexes are correct, if not set the correct index
        for (Redeemer redeemer : transaction.getWitnessSet().getRedeemers()) {
            int scriptInputIndex = RedeemerUtil.getScriptInputIndex(scriptUtxo, transaction);
            if (redeemer.getIndex().intValue() != scriptInputIndex && scriptInputIndex != -1) {
                redeemer.setIndex(BigInteger.valueOf(scriptInputIndex));
            }
        }
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
        //Some dummy value
        ExUnits exUnits = ExUnits.builder()
                .mem(BigInteger.valueOf(100000000))
                .steps(BigInteger.valueOf(100000000))
                .build();

        TxBuilder txBuilder = ScriptCallContextProviders.scriptCallContext(script, scriptUtxo, datum, redeemer, redeemerTag, exUnits);

        return txBuilder;
    }
}
