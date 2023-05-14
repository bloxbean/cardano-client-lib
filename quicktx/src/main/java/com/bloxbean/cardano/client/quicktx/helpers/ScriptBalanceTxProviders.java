package com.bloxbean.cardano.client.quicktx.helpers;

import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.helper.BalanceTxBuilders;
import com.bloxbean.cardano.client.function.helper.ScriptCostEvaluators;
import com.bloxbean.cardano.client.transaction.spec.Value;

import java.math.BigInteger;

public class ScriptBalanceTxProviders {

    //TODO -- Unit tests pending
    public static TxBuilder balanceTx(String feePayer, int additionalSigners, boolean containsScript,
                                      TransactionProcessor transactionProcessor) {
        return (ctx, transaction) -> {
            int inputSize = transaction.getBody().getInputs().size();
            BalanceTxBuilders.balanceTxWithAdditionalSigners(feePayer, additionalSigners).apply(ctx, transaction);
            int newInputSize = transaction.getBody().getInputs().size();

            if (!containsScript || (newInputSize == inputSize)) { //TODO -- check for contains script
                return;
            }

            //As new inputs are added, the cost of the transaction will increase
            //So, we need to recompute the script cost and fee
            //Add fee back to the fee payer's output
            BigInteger fee = transaction.getBody().getFee();
            transaction.getBody().setFee(BigInteger.valueOf(170000));
            transaction.getBody().getOutputs().stream()
                    .filter(output -> feePayer.equals(output.getAddress()))
                    .max((to1, to2) -> to1.getValue().getCoin().compareTo(to2.getValue().getCoin()))
                    .ifPresent(transactionOutput -> transactionOutput.setValue(transactionOutput.getValue().plus(Value.builder().coin(fee).build())));

            Value newCollateralReturnValue = transaction.getBody().getCollateralReturn()
                    .getValue().plus(Value.builder().coin(transaction.getBody().getTotalCollateral()).build());
            transaction.getBody().getCollateralReturn().setValue(newCollateralReturnValue);
            transaction.getBody().setTotalCollateral(BigInteger.valueOf(1000000)); //reset total collateral. some dummy value

            //Recompute script cost
            ScriptCostEvaluators.evaluateScriptCost(transactionProcessor).apply(ctx, transaction);
            BalanceTxBuilders.balanceTxWithAdditionalSigners(feePayer, additionalSigners).apply(ctx, transaction);
        };
    }
}
