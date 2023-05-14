package com.bloxbean.cardano.client.function.helper;

import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.plutus.spec.Redeemer;

import java.util.List;

public class ScriptCostEvaluators {

    //TODO -- Unit tests pending
    public static TxBuilder evaluateScriptCost(TransactionProcessor transactionProcessor) {
        return (ctx, transaction) -> {
            if (transaction.getWitnessSet().getRedeemers() == null ||
                    transaction.getWitnessSet().getRedeemers().isEmpty())
                return; //non-script transaction

            try {
                Result<List<EvaluationResult>> evaluationResult = transactionProcessor.evaluateTx(transaction.serialize());
                if (!evaluationResult.isSuccessful())
                    throw new TxBuildException("Failed to compute script cost : " + evaluationResult.getResponse());

                List<EvaluationResult> evaluationResults = evaluationResult.getValue();
                for (Redeemer redeemer : transaction.getWitnessSet().getRedeemers()) {
                    for (EvaluationResult evalRes : evaluationResults) {
                        if (redeemer.getIndex().intValue() == evalRes.getIndex() && redeemer.getTag() == evalRes.getRedeemerTag()) {
                            redeemer.getExUnits()
                                    .setMem(evalRes.getExUnits().getMem());
                            redeemer.getExUnits()
                                    .setSteps(evalRes.getExUnits().getSteps());
                            break;
                        }
                    }
                }
            } catch (CborSerializationException | ApiException e) {
                throw new TxBuildException("Failed to compute script cost", e);
            }
        };
    }
}
