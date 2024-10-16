package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.api.TransactionEvaluator;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.transaction.spec.Value;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;

/**
 * Helper class to balance a transaction with script.
 */
@Slf4j
public class ScriptBalanceTxProviders {

    //TODO -- Unit tests pending
    /**
     * Function to balance an unbalanced transaction using Automatic Utxo Discovery with Additional Signers.
     * This function invokes {@link BalanceTxBuilders#balanceTxWithAdditionalSigners(String, int)} to balance the transaction.
     * If any new inputs are added to the transaction during balancing, the script cost and fee will be recomputed.
     *
     * @param feePayer Fee payer address
     * @param additionalSigners No of Additional signers. This is required for accurate fee calculation.
     * @param containsScript If the transaction contains script
     * @return TxBuilder
     */
    public static TxBuilder balanceTx(String feePayer, int additionalSigners, boolean containsScript) {
        return (ctx, transaction) -> {
            int inputSize = transaction.getBody().getInputs().size();
            BalanceTxBuilders.balanceTxWithAdditionalSigners(feePayer, additionalSigners).apply(ctx, transaction);
            int newInputSize = transaction.getBody().getInputs().size();

            if (!containsScript || (newInputSize == inputSize)) { //TODO -- check for contains script
                return;
            }

            TransactionEvaluator transactionEvaluator = ctx.getTxnEvaluator();
            if (transactionEvaluator == null)
                throw new TxBuildException("Transaction evaluator is not set. Transaction evaluator is required to calculate script cost");

            //As new inputs are added, the cost of the transaction will increase
            //So, we need to recompute the script cost and fee
            //Add fee back to the fee payer's output
            BigInteger fee = transaction.getBody().getFee();
            transaction.getBody().setFee(BigInteger.valueOf(170000));
            transaction.getBody().getOutputs().stream()
                    .filter(output -> feePayer.equals(output.getAddress()))
                    .max((to1, to2) -> to1.getValue().getCoin().compareTo(to2.getValue().getCoin()))
                    .ifPresent(transactionOutput -> transactionOutput.setValue(transactionOutput.getValue().add(Value.builder().coin(fee).build())));

            Value newCollateralReturnValue = transaction.getBody().getCollateralReturn()
                    .getValue().add(Value.builder().coin(transaction.getBody().getTotalCollateral()).build());
            transaction.getBody().getCollateralReturn().setValue(newCollateralReturnValue);
            transaction.getBody().setTotalCollateral(BigInteger.valueOf(1000000)); //reset total collateral. some dummy value

            //Recompute script cost
            ScriptCostEvaluators.evaluateScriptCost().apply(ctx, transaction);
            BalanceTxBuilders.balanceTxWithAdditionalSigners(feePayer, additionalSigners).apply(ctx, transaction);
        };
    }
}
