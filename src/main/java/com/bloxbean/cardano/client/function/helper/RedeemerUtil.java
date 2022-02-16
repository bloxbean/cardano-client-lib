package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.cardano.client.transaction.spec.Redeemer;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;

import java.util.List;
import java.util.stream.Collectors;

class RedeemerUtil {

    public static TransactionInput getScriptInputFromRedeemer(Redeemer redeemer, Transaction transaction) {
        //TODO -- check if sorting is required
        List<TransactionInput> copyInputs = transaction.getBody().getInputs()
                .stream()
                .collect(Collectors.toList());
        copyInputs.sort((o1, o2) -> (o1.getTransactionId() + "#" + o1.getIndex()).compareTo(o2.getTransactionId() + "#" + o2.getIndex()));

        int index = redeemer.getIndex().intValue();
        return copyInputs.get(index);
    }

    public static int getScriptInputIndex(Utxo utxo, Transaction transaction) {
        //TODO -- check if sorting is required
        List<TransactionInput> copyInputs = transaction.getBody().getInputs()
                .stream()
                .collect(Collectors.toList());
        copyInputs.sort((o1, o2) -> (o1.getTransactionId() + "#" + o1.getIndex()).compareTo(o2.getTransactionId() + "#" + o2.getIndex()));

        int index = copyInputs.indexOf(new TransactionInput(utxo.getTxHash(), utxo.getOutputIndex()));
        return index;
    }

    public static int getScriptInputIndex(TransactionInput input, Transaction transaction) {
        //TODO -- check if sorting is required
        List<TransactionInput> copyInputs = transaction.getBody().getInputs()
                .stream()
                .collect(Collectors.toList());
        copyInputs.sort((o1, o2) -> (o1.getTransactionId() + "#" + o1.getIndex()).compareTo(o2.getTransactionId() + "#" + o2.getIndex()));

        int index = copyInputs.indexOf(input);
        return index;
    }
}
