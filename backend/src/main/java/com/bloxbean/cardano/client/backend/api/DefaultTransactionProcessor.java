package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;

import java.util.List;
import java.util.Set;

/**
 * Default implementation of TransactionProcessor which uses Backend service's TransactionService
 */
public class DefaultTransactionProcessor implements TransactionProcessor {
    private TransactionService transactionService;

    public DefaultTransactionProcessor(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Override
    public Result<String> submitTransaction(byte[] cborData) throws ApiException {
        return transactionService.submitTransaction(cborData);
    }

    @Override
    public Result<List<EvaluationResult>> evaluateTx(byte[] cborData, Set<Utxo> inputUtxos) throws ApiException {
        return transactionService.evaluateTx(cborData);
    }
}
