package com.bloxbean.cardano.client.backend.impl.blockfrost.service;

import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.impl.blockfrost.service.http.TransactionApi;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;

public class BFTransactionService extends BFBaseService implements TransactionService {
    private TransactionApi transactionApi;

    public BFTransactionService(String baseUrl, String projectId) {
        super(baseUrl, projectId);
        this.transactionApi = getRetrofit().create(TransactionApi.class);
    }

    @Override
    public Result<String> submitTransaction(byte[] cborData) throws ApiException {

        RequestBody requestBody = RequestBody.create(MediaType.parse("application/cbor"), cborData);

        Call<String> txnCall = transactionApi.submit(getProjectId(), requestBody);
        try {
            Response<String> response = txnCall.execute();
            return processResponse(response);

        } catch (IOException e) {
            throw new ApiException("Error submit transaction", e);
        }
    }

    @Override
    public Result<TransactionContent> getTransaction(String txnHash) throws ApiException {
        Call<TransactionContent> txnCall = transactionApi.getTransaction(getProjectId(), txnHash);
        try {
            Response<TransactionContent> response = txnCall.execute();
            return processResponse(response);

        } catch (IOException e) {
            throw new ApiException("Error getting transaction for id : " + txnHash, e);
        }
    }

    @Override
    public Result<TxContentUtxo> getTransactionUtxos(String txnHash) throws ApiException {
        Call<TxContentUtxo> txnCall = transactionApi.getTransactionUtxos(getProjectId(), txnHash);
        try {
            Response<TxContentUtxo> response = txnCall.execute();
            return processResponse(response);

        } catch (IOException e) {
            throw new ApiException("Error getting transaction utxos for id : " + txnHash, e);
        }
    }
}
