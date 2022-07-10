package com.bloxbean.cardano.client.function.helper;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.config.Configuration;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.function.helper.model.ScriptCallContext;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.transaction.util.ScriptDataHashGenerator;

import java.math.BigInteger;
import java.util.Objects;

import static com.bloxbean.cardano.client.function.helper.RedeemerUtil.getScriptInputIndex;

/**
 * Provides helper methods to add plutus script specific data
 */
public class ScriptCallContextProviders {

    /**
     * Function to add plutus script specific data to a <code>{@link Transaction}</code> object
     * @param sc required data as <code>{@link ScriptCallContext}</code>
     * @return <code>TxBuilder</code> function
     * @throws CborRuntimeException if cbor serialization/de-serialization error
     */
    public static TxBuilder createFromScriptCallContext(ScriptCallContext sc) {
        return scriptCallContext(sc.getScript(), sc.getUtxo(), sc.getDatum(), sc.getRedeemer(), sc.getRedeemerTag(), sc.getExUnits());
    }

    /**
     * Function to add plutus script specific data to a <code>{@link Transaction}</code> object.
     * <br>
     * <br>If custom Java objects are passed as redeemer and datum, it converts them to <code>{@link PlutusData}</code>
     * <br>Add redeemer and datum objects to <code>{@link TransactionWitnessSet}</code>
     * <br>Add plutus script to <code>{@link TransactionWitnessSet}</code>
     * <br>Compute script datahash and set it in <code>{@link TransactionBody}</code>
     *
     * @param plutusScript Plutus Script
     * @param utxo Script Utxo
     * @param datum Datum as PlutusData or custom Java object (with <code>{@link com.bloxbean.cardano.client.plutus.annotation.Constr}</code> annotation)
     * @param redeemerData Redeemer as PlutusData or custom Java object (with <code>{@link com.bloxbean.cardano.client.plutus.annotation.Constr}</code> annotation)
     * @param tag Redeemer tag
     * @param exUnits Execution Units
     * @param <T> Datum class type
     * @param <K> Redeemer class type
     * @return <code>TxBuilder</code> function
     * @throws CborRuntimeException if cbor serialization/de-serialization error
     */
    public static <T, K> TxBuilder scriptCallContext(PlutusScript plutusScript, Utxo utxo, T datum, K redeemerData,
                                                     RedeemerTag tag, ExUnits exUnits) {
        return (context, transaction) -> {
            int scriptInputIndex = -1;
            if (utxo != null) {
                scriptInputIndex = getScriptInputIndex(utxo, transaction);
                if (scriptInputIndex == -1)
                    throw new TxBuildException("Script utxo is not found in transaction inputs : " + utxo.getTxHash());
            }

            scriptCallContext(plutusScript, scriptInputIndex, datum, redeemerData, tag, exUnits).build(context, transaction);
        };
    }

    /**
     * Function to add plutus script specific data to a <code>{@link Transaction}</code> object.
     * <br>
     * <br>If custom Java objects are passed as redeemer and datum, it converts them to <code>{@link PlutusData}</code>
     * <br>Add redeemer and datum objects to <code>{@link TransactionWitnessSet}</code>
     * <br>Add plutus script to <code>{@link TransactionWitnessSet}</code>
     * <br>Compute script datahash and set it in <code>{@link TransactionBody}</code>
     *
     * @param plutusScript Plutus Script
     * @param scriptInputIndex Script index in transaction input list
     * @param datum Datum as PlutusData or custom Java object (with <code>{@link com.bloxbean.cardano.client.plutus.annotation.Constr}</code> annotation)
     * @param redeemerData Redeemer as PlutusData or custom Java object (with <code>{@link com.bloxbean.cardano.client.plutus.annotation.Constr}</code> annotation)
     * @param tag Redeemer tag
     * @param exUnits Execution Units
     * @param <T> Datum class type
     * @param <K> Redeemer class type
     * @return <code>TxBuilder</code> function
     * @throws CborRuntimeException if cbor serialization/de-serialization error
     */
    public static <T, K> TxBuilder scriptCallContext(PlutusScript plutusScript, int scriptInputIndex, T datum, K redeemerData,
                                                     RedeemerTag tag, ExUnits exUnits) {
        Objects.requireNonNull(plutusScript);
        Objects.requireNonNull(tag);
        Objects.requireNonNull(exUnits);

        return (context, transaction) -> {
            if (transaction.getWitnessSet() == null) {
                transaction.setWitnessSet(new TransactionWitnessSet());
            }

            //Datum
            if (datum != null) {
                PlutusData datumPlutusData;
                if (datum instanceof PlutusData)
                    datumPlutusData = (PlutusData) datum;
                else
                    datumPlutusData = Configuration.INSTANCE.getPlutusObjectConverter().toPlutusData(datum);

                transaction.getWitnessSet().getPlutusDataList().add(datumPlutusData);
            }

            //redeemer
            if (redeemerData != null) {
                //redeemer
                PlutusData redeemerPlutusData;
                if (redeemerData instanceof PlutusData)
                    redeemerPlutusData = (PlutusData) redeemerData;
                else
                    redeemerPlutusData = Configuration.INSTANCE.getPlutusObjectConverter().toPlutusData(redeemerData);

                Redeemer redeemer = Redeemer.builder()
                        .tag(tag)
                        .data(redeemerPlutusData)
                        .index(scriptInputIndex != -1? BigInteger.valueOf(scriptInputIndex): BigInteger.ZERO) //TODO -- check
                        .exUnits(exUnits).build();

                transaction.getWitnessSet().getRedeemers().add(redeemer);
            }

            if (plutusScript instanceof PlutusV1Script) {
                if (!transaction.getWitnessSet().getPlutusV1Scripts().contains(plutusScript)) //To avoid duplicate script in list
                    transaction.getWitnessSet().getPlutusV1Scripts().add((PlutusV1Script) plutusScript);
            } else if (plutusScript instanceof PlutusV2Script) {
                if (!transaction.getWitnessSet().getPlutusV2Scripts().contains(plutusScript)) //To avoid duplicate script in list
                    transaction.getWitnessSet().getPlutusV2Scripts().add((PlutusV2Script) plutusScript);
            }

            //Script data hash
            byte[] scriptDataHash;
            try {
                CostMdls costMdls = context.getCostMdls();
                byte[] languageViews = costMdls.getLanguageViewEncoding();

                scriptDataHash = ScriptDataHashGenerator.generate(transaction.getWitnessSet().getRedeemers(),
                        transaction.getWitnessSet().getPlutusDataList(), languageViews);
            } catch (CborSerializationException | CborException e) {
                throw new CborRuntimeException("Error getting scriptDataHash ", e);
            }
            transaction.getBody().setScriptDataHash(scriptDataHash);
        };
    }

}
