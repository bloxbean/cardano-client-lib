package com.bloxbean.cardano.client.api.util;

import com.bloxbean.cardano.client.api.ScriptSupplier;
import com.bloxbean.cardano.client.api.UtxoSupplier;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;
import com.bloxbean.cardano.client.util.Try;
import com.bloxbean.cardano.client.transaction.spec.Transaction;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for handling reference scripts in transactions.
 */
public class ReferenceScriptUtil {

    /**
     * Fetches the reference scripts from the utxo supplier and calculates the size of the reference scripts
     *
     * @param utxoSupplier utxo supplier
     * @param scriptSupplier script supplier
     * @param transaction transaction
     * @return size of the reference scripts in transaction's reference inputs
     */
    public static long totalRefScriptsSizeInRefInputs(UtxoSupplier utxoSupplier, ScriptSupplier scriptSupplier, Transaction transaction) {
        return totalRefScriptsSizeInInputs(utxoSupplier, scriptSupplier, new HashSet<>(transaction.getBody().getReferenceInputs()));
    }

    /**
     * Fetches and calculates the size of reference scripts in given inputs using UTXO supplier and script supplier.
     *
     * @param utxoSupplier - utxo supplier
     * @param scriptSupplier - script supplier
     * @param inputs the set of transaction inputs for which to fetch and calculate reference script sizes
     * @return the total size of the reference scripts in bytes
     */
    public static long totalRefScriptsSizeInInputs(UtxoSupplier utxoSupplier, ScriptSupplier scriptSupplier, Set<TransactionInput> inputs) {
        return inputs.stream()
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
    public static List<PlutusScript> resolveReferenceScripts(UtxoSupplier utxoSupplier, ScriptSupplier scriptSupplier, Transaction transaction, Set<Utxo> inputUtxos) {
        var refInputPlutusScripts = transaction.getBody().getReferenceInputs()
                .stream()
                .flatMap(refInput -> utxoSupplier.getTxOutput(refInput.getTransactionId(), refInput.getIndex()).stream()
                        .map(utxo -> scriptSupplier.getScript(utxo.getReferenceScriptHash()))
                        .flatMap(Optional::stream))
                .collect(Collectors.toList());

        var inputPlutusScripts = transaction.getBody().getInputs()
                .stream()
                .flatMap(input -> inputUtxos.stream()
                        .filter(utxo -> utxo.getTxHash().equals(input.getTransactionId())
                                && utxo.getOutputIndex() == input.getIndex()
                                && utxo.getReferenceScriptHash() != null)
                        .findFirst()
                        .map(utxo -> scriptSupplier.getScript(utxo.getReferenceScriptHash())).stream()
                    )
                .flatMap(Optional::stream)
                .collect(Collectors.toList());

        if (refInputPlutusScripts.isEmpty() && inputPlutusScripts.isEmpty())
            return Collections.emptyList();
        else {
            Set<PlutusScript> allPlutusScripts = new HashSet<>();
            allPlutusScripts.addAll(refInputPlutusScripts);
            allPlutusScripts.addAll(inputPlutusScripts);

            return allPlutusScripts.stream().collect(Collectors.toList());
        }
    }

}
