package com.bloxbean.cardano.client.backend.ogmios.http;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.backend.model.TxContentRedeemers;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;

import com.bloxbean.cardano.client.supplier.ogmios.OgmiosTransactionProcessor;

import java.util.List;

public class OgmiosTransactionService implements TransactionService {

    private final OgmiosTransactionProcessor ogmiosTransactionProcessor;

    public OgmiosTransactionService(String baseUrl) {
        this.ogmiosTransactionProcessor = new OgmiosTransactionProcessor(baseUrl);
    }

    @Override
    public Result<String> submitTransaction(byte[] cborData) throws ApiException {
        return ogmiosTransactionProcessor.submitTransaction(cborData);
    }

    @Override
    public Result<TransactionContent> getTransaction(String txnHash) throws ApiException {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Result<List<TransactionContent>> getTransactions(List<String> txnHashCollection) throws ApiException {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Result<TxContentUtxo> getTransactionUtxos(String txnHash) throws ApiException {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Result<List<TxContentRedeemers>> getTransactionRedeemers(String txnHash) throws ApiException {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Result<Utxo> getTransactionOutput(String txnHash, int outputIndex) throws ApiException {
        throw new UnsupportedOperationException("Not supported yet");
    }

    @Override
    public Result<List<EvaluationResult>> evaluateTx(byte[] cborData) throws ApiException {
        return ogmiosTransactionProcessor.evaluateTx(cborData);
    }


}
