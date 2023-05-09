package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.api.model.Result;

import java.util.List;

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
    public Result<List<EvaluationResult>> evaluateTx(byte[] cborData) throws ApiException {
        return transactionService.evaluateTx(cborData);
    }
}
