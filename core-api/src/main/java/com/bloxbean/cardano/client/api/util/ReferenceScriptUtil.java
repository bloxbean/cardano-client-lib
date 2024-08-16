package com.bloxbean.cardano.client.api.util;

import com.bloxbean.cardano.client.api.ScriptSupplier;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.util.Try;
import com.bloxbean.cardano.client.transaction.spec.Transaction;

import java.util.Optional;

public class ReferenceScriptUtil {

    /**
     * Fetches the reference scripts from the utxo supplier and calculates the size of the reference scripts
     *
     * @param utxoSupplier utxo supplier
     * @param scriptSupplier script supplier
     * @param transaction transaction
     * @return size of the reference scripts
     */
    public static long fetchAndCalculateReferenceScriptsSize(UtxoSupplier utxoSupplier, ScriptSupplier scriptSupplier, Transaction transaction) {
        return transaction.getBody().getReferenceInputs()
                .stream()
                .flatMap(refInput -> utxoSupplier.getTxOutput(refInput.getTransactionId(), refInput.getIndex()).stream()
                        .map(utxo -> scriptSupplier.getScript(utxo.getReferenceScriptHash()))
                        .flatMap(Optional::stream))
                .map(script -> Try.of(() -> script.scriptRefBytes().length))
                .filter(Try::isSuccess)
                .mapToLong(Try::get)
                .sum();

    }

}
