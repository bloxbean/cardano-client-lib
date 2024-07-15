package com.bloxbean.cardano.client.api.impl;

import com.bloxbean.cardano.client.api.TransactionEvaluator;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.plutus.spec.ExUnits;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.JsonUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A static implementation of {@link TransactionEvaluator} which returns a static list of {@link EvaluationResult}
 */
public class StaticTransactionEvaluator implements TransactionEvaluator {
    private List<ExUnits> exUnits;

    /**
     * Constructor
     * @param exUnits List of {@link ExUnits} to be returned as evaluation result. The number of ExUnits should match the
     *                number of redeemers in the transaction in the order they appear in the transaction
     */
    public StaticTransactionEvaluator(List<ExUnits> exUnits) {
        this.exUnits = exUnits;
    }

    @Override
    public Result<List<EvaluationResult>> evaluateTx(byte[] cbor, Set<Utxo> inputUtxos) throws ApiException {
        var evaluationResults = getEvaluationResults(cbor);
        return Result.success(JsonUtil.getPrettyJson(evaluationResults)).withValue(evaluationResults);
    }

    @Override
    public Result<List<EvaluationResult>> evaluateTx(byte[] cbor) throws ApiException {
        var evaluationResults = getEvaluationResults(cbor);
        return Result.success(JsonUtil.getPrettyJson(evaluationResults)).withValue(evaluationResults);
    }

    @Override
    public Result<List<EvaluationResult>> evaluateTx(Transaction transaction, Set<Utxo> inputUtxos) throws ApiException {
        var evaluationResults = getEvaluationResults(transaction);
        return Result.success(JsonUtil.getPrettyJson(evaluationResults)).withValue(evaluationResults);
    }

    private List<EvaluationResult> getEvaluationResults(byte[] txCbor) {
        Transaction transaction = null;
        try {
            transaction = Transaction.deserialize(txCbor);
        } catch (CborDeserializationException e) {
            throw new IllegalArgumentException("Invalid transaction cbor. Cost cannot be returned");
        }
        return getEvaluationResults(transaction);
    }

    private List<EvaluationResult> getEvaluationResults(Transaction transaction) {
        if (transaction.getWitnessSet().getRedeemers() == null)
            throw new IllegalArgumentException("Transaction doesn't have redeemers. Cost cannot be returned");

        if (transaction.getWitnessSet().getRedeemers().size() != exUnits.size())
            throw new IllegalArgumentException("Number of redeemers in the transaction doesn't match the number of exUnits provided");

        List<EvaluationResult> evaluationResults = new ArrayList<>();
        for (int i = 0; i < transaction.getWitnessSet().getRedeemers().size(); i++) {
            var redeemer = transaction.getWitnessSet().getRedeemers().get(i);
            var evaluationResult = new EvaluationResult();
            evaluationResult.setExUnits(exUnits.get(i));
            evaluationResult.setIndex(redeemer.getIndex().intValue());
            evaluationResult.setRedeemerTag(redeemer.getTag());

            evaluationResults.add(evaluationResult);
        }

        return evaluationResults;
    }
}
