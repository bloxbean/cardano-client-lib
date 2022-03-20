package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;

public class DefaultTransactionProcessor implements TransactionProcessor {
    private TransactionService transactionService;

    public DefaultTransactionProcessor(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Override
    public Result<String> submitTransaction(byte[] cborData) throws ApiException {
        return transactionService.submitTransaction(cborData);
    }
}
