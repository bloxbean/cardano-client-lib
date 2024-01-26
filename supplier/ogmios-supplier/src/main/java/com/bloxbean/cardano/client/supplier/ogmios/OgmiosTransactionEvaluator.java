package com.bloxbean.cardano.client.supplier.ogmios;

import com.bloxbean.cardano.client.api.TransactionEvaluator;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.transaction.spec.Transaction;

import java.util.List;
import java.util.Set;

public class OgmiosTransactionEvaluator implements TransactionEvaluator {
    private final OgmiosTransactionProcessor ogmiosTransactionProcessor;

    public OgmiosTransactionEvaluator(String baseUrl) {
        this.ogmiosTransactionProcessor = new OgmiosTransactionProcessor(baseUrl);
    }

    @Override
    public Result<List<EvaluationResult>> evaluateTx(byte[] cbor, Set<Utxo> inputUtxos) throws ApiException {
        return ogmiosTransactionProcessor.evaluateTx(cbor, inputUtxos);
    }

    @Override
    public Result<List<EvaluationResult>> evaluateTx(byte[] cbor) throws ApiException {
        return ogmiosTransactionProcessor.evaluateTx(cbor);
    }

    @Override
    public Result<List<EvaluationResult>> evaluateTx(Transaction transaction, Set<Utxo> inputUtxos) throws ApiException {
        return ogmiosTransactionProcessor.evaluateTx(transaction, inputUtxos);
    }
}
