package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.plutus.UtxoUtil;
import com.bloxbean.cardano.client.transaction.spec.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
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

    /**
     * Function to create collateral and unbalanced collateral outputs from list of utxos
     * @param collateralReturnAddress
     * @param utxos List of utxos for collateral inputs
     * @return <code>{@link TxBuilder}</code> function
     */
    public static TxBuilder collateralOutputs(String collateralReturnAddress, List<Utxo> utxos) {
        return (context, transaction) -> {
            checkTransactionBodyForNull(transaction);

            TransactionOutput collateralOutput = TransactionOutput.builder()
                    .address(collateralReturnAddress)
                    .value(Value.builder().coin(BigInteger.ZERO).build())
                    .build();

            utxos.forEach(utxo -> {
                TransactionInput input = TransactionInput.builder()
                        .transactionId(utxo.getTxHash())
                        .index(utxo.getOutputIndex())
                        .build();
                transaction.getBody().getCollateral().add(input);

                //Create collateral output
                UtxoUtil.copyUtxoValuesToOutput(collateralOutput, utxo);
            });

            transaction.getBody().setCollateralReturn(collateralOutput);
            //Set total collateral to some dummy value. So that fee calculation will be correct.
            transaction.getBody().setTotalCollateral(BigInteger.valueOf(1000000));
        };
    }

    /**
     * Balance the collateral outputs. This function should be called after fee calcuation.
     * Using collateral percent (protocol prameter) and fee, it calculates total collateral amount.
     * @return
     */
    public static TxBuilder balanceCollateralOutputs() {
        return (context, transaction) -> {
            BigDecimal collateralPercent = context.getProtocolParams().getCollateralPercent();
            //balance collateral output.
            BigInteger totalCollateral = new BigDecimal(transaction.getBody().getFee())
                    .multiply(collateralPercent.divide(BigDecimal.valueOf(100)))
                            .setScale(0, RoundingMode.CEILING).toBigInteger();

            TransactionOutput collateralReturn = transaction.getBody().getCollateralReturn();
            if (collateralReturn == null)
                throw new TxBuildException("Unable to do balance. No collateral output found");

            //substract totalCollateral value
            BigInteger remainingCoin = collateralReturn.getValue().getCoin().subtract(totalCollateral);
            Value newValue = collateralReturn.getValue().toBuilder()
                    .coin(remainingCoin)
                    .build();

            TransactionOutput balancedCollateralReturn = collateralReturn.toBuilder()
                    .value(newValue)
                    .build();

            transaction.getBody().setCollateralReturn(balancedCollateralReturn);
            //set total collateral
            transaction.getBody().setTotalCollateral(totalCollateral);
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
