package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.backend.model.Utxo;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.transaction.spec.TransactionBody;
import com.bloxbean.cardano.client.transaction.spec.TransactionInput;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Provides helper methods to create <code>{@link TxBuilder}</code> function to add collateral to a transaction
 */
public class CollateralBuilders {

    /**
     * Function to create collateral from list of <code>{@link Utxo}</code>
     *
     * @param utxos list of <code>Utxo</code>
     * @return <code>{@link TxBuilder}</code> function
     */
    public static TxBuilder collateralFrom(List<Utxo> utxos) {
        return (context, transaction) -> {
            checkTransactionBodyForNull(transaction);

            utxos.forEach(utxo -> {
                TransactionInput input = TransactionInput.builder()
                        .transactionId(utxo.getTxHash())
                        .index(utxo.getOutputIndex())
                        .build();
                transaction.getBody().getCollateral().add(input);
            });
        };
    }

    /**
     * Function to create collateral from list of <code>{@link Utxo}</code>
     *
     * @param supplier <code>Supplier</code> function to provide list of <code>{@link Utxo}</code>
     * @return <code>{@link TxBuilder}</code> function
     */
    public static TxBuilder collateralFrom(Supplier<List<Utxo>> supplier) {
        return (context, transaction) -> {
            checkTransactionBodyForNull(transaction);

            supplier.get().forEach(utxo -> {
                TransactionInput input = TransactionInput.builder()
                        .transactionId(utxo.getTxHash())
                        .index(utxo.getOutputIndex())
                        .build();
                transaction.getBody().getCollateral().add(input);
            });
        };
    }

    /**
     * Function to create collateral from transaction hash and index
     *
     * @param txHash  transaction Hash
     * @param txIndex index
     * @return <code>{@link TxBuilder}</code> function
     */
    public static TxBuilder collateralFrom(String txHash, int txIndex) {
        return (context, transaction) -> {
            checkTransactionBodyForNull(transaction);

            TransactionInput input = TransactionInput.builder()
                    .transactionId(txHash)
                    .index(txIndex)
                    .build();
            transaction.getBody().getCollateral().add(input);

        };
    }

    private static void checkTransactionBodyForNull(Transaction transaction) {
        if (transaction.getBody() == null)
            transaction.setBody(new TransactionBody());

        if (transaction.getBody().getInputs() == null)
            transaction.getBody().setInputs(new ArrayList<>());

        if (transaction.getBody().getCollateral() == null)
            transaction.getBody().setCollateral(new ArrayList<>());
    }
}
