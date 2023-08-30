package com.bloxbean.cardano.client.backend.blockfrost.service;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.blockfrost.service.http.TransactionApi;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.backend.model.TxContentRedeemers;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;
import com.bloxbean.cardano.client.util.HexUtil;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
    public Result<List<TransactionContent>> getTransactions(List<String> txnHashCollection) throws ApiException {
        List<TransactionContent> transactionContentList = new ArrayList<>();
        for (String txnHash : txnHashCollection) {
            if (!txnHash.isEmpty() && !txnHash.matches("^[\\da-fA-F]+$")) {
                throw new ApiException("Invalid Transaction Hash Format");
            }
            Result<TransactionContent> result = getTransaction(txnHash);
            if (result.isSuccessful()) {
                transactionContentList.add(result.getValue());
            } else {
                return Result.error(result.getResponse()).code(result.code());
            }
        }
        return Result.success("OK").withValue(transactionContentList).code(200);
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

    @Override
    public Result<List<TxContentRedeemers>> getTransactionRedeemers(String txnHash) throws ApiException {
        Call<List<TxContentRedeemers>> txnCall = transactionApi.getTransactionRedeemers(getProjectId(), txnHash);
        try {
            Response<List<TxContentRedeemers>> response = txnCall.execute();
            return processResponse(response);

        } catch (IOException e) {
            throw new ApiException("Error getting transaction redeemers for id : " + txnHash, e);
        }
    }

    @Override
    public Result<List<EvaluationResult>> evaluateTx(byte[] cborData) throws ApiException {

        RequestBody requestBody = RequestBody.create(MediaType.parse("application/cbor"), HexUtil.encodeHexString(cborData));

        Call<Object> evalCall = transactionApi.evaluateTx(getProjectId(), requestBody);
        try {
            Response<Object> response = evalCall.execute();
            return OgmiosTxResponseParser.processEvaluateResponse(response);

        } catch (IOException e) {
            throw new ApiException("Error evaluating script cost for transaction", e);
        }
    }

}
