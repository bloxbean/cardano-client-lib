package com.bloxbean.cardano.client.api.util;

import com.bloxbean.cardano.client.api.ScriptSupplier;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.util.Try;
import com.bloxbean.cardano.client.transaction.spec.Transaction;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

    /**
     * Use {@link UtxoSupplier} to get the utxos for reference inputs and then use {@link ScriptSupplier} to get script(s) for the reference script hash
     * @param utxoSupplier utxo supplier
     * @param scriptSupplier script supplier
     * @param transaction transaction
     * @return list of {@link PlutusScript}
     */
    public static List<PlutusScript> resolveReferenceScripts(UtxoSupplier utxoSupplier, ScriptSupplier scriptSupplier, Transaction transaction) {
        return transaction.getBody().getReferenceInputs()
                .stream()
                .flatMap(refInput -> utxoSupplier.getTxOutput(refInput.getTransactionId(), refInput.getIndex()).stream()
                        .map(utxo -> scriptSupplier.getScript(utxo.getReferenceScriptHash()))
                        .flatMap(Optional::stream))
                .collect(Collectors.toList());
    }

}
