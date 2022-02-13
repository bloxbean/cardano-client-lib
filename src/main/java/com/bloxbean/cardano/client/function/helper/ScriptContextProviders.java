package com.bloxbean.cardano.client.function.helper;

import co.nstant.in.cbor.CborException;
import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.cardano.client.config.Configuration;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.transaction.spec.*;
import com.bloxbean.cardano.client.transaction.util.CostModelConstants;
import com.bloxbean.cardano.client.transaction.util.ScriptDataHashGenerator;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

//TODO -- Unit tests pending

/**
 * Provides helper method to add script specific data
 */
public class ScriptContextProviders {

    public static <T, K> TxBuilder scriptContext(PlutusScript plutusScript, Utxo utxo, T datum, K redeemerData,
                                                 RedeemerTag tag, ExUnits exUnits) {
        return (context, transaction) -> {
            int scriptInputIndex = getScriptInputIndex(utxo, transaction);
            scriptContext(plutusScript, scriptInputIndex, datum, redeemerData, tag, exUnits).accept(context, transaction);
        };
    }

    public static <T, K> TxBuilder scriptContext(PlutusScript plutusScript, int scriptInputIndex, T datum, K redeemerData,
                                                 RedeemerTag tag, ExUnits exUnits) {
        return (context, transaction) -> {
            if (transaction.getWitnessSet() == null) {
                transaction.setWitnessSet(new TransactionWitnessSet());
            }

            //Datum
            PlutusData datumPlutusData;
            if (datum instanceof PlutusData)
                datumPlutusData = (PlutusData) datum;
            else
                datumPlutusData = Configuration.INSTANCE.getPlutusObjectConverter().toPlutusData(datum);

            //redeemer
            PlutusData redeemerPlutusData;
            if (redeemerData instanceof PlutusData)
                redeemerPlutusData = (PlutusData) redeemerData;
            else
                redeemerPlutusData = Configuration.INSTANCE.getPlutusObjectConverter().toPlutusData(redeemerData);

            Redeemer redeemer = Redeemer.builder()
                    .tag(tag)
                    .data(redeemerPlutusData)
                    .index(BigInteger.valueOf(scriptInputIndex)) //TODO -- check
                    .exUnits(exUnits).build();

            transaction.getWitnessSet().getPlutusScripts().add(plutusScript);
            transaction.getWitnessSet().getPlutusDataList().add(datumPlutusData);
            transaction.getWitnessSet().getRedeemers().add(redeemer);

            //Script data hash
            byte[] scriptDataHash;
            try {
                scriptDataHash = ScriptDataHashGenerator.generate(transaction.getWitnessSet().getRedeemers(),
                        transaction.getWitnessSet().getPlutusDataList(), CostModelConstants.LANGUAGE_VIEWS);
            } catch (CborSerializationException | CborException e) {
                throw new TxBuildException("Error getting scriptDataHash ", e);
            }
            transaction.getBody().setScriptDataHash(scriptDataHash);
        };
    }

    private static int getScriptInputIndex(Utxo utxo, Transaction transaction) {
        //TODO -- check if sorting is required
        List<TransactionInput> copyInputs = transaction.getBody().getInputs()
                .stream()
                .collect(Collectors.toList());
        copyInputs.sort((o1, o2) -> o1.getTransactionId().compareTo(o2.getTransactionId()));

        int index = copyInputs.indexOf(new TransactionInput(utxo.getTxHash(), utxo.getOutputIndex()));
        return index;
    }

}
