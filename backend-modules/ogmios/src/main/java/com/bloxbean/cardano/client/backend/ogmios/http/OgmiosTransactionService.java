package com.bloxbean.cardano.client.backend.ogmios.http;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.api.TransactionService;
import com.bloxbean.cardano.client.backend.model.TransactionContent;
import com.bloxbean.cardano.client.backend.model.TxContentRedeemers;
import com.bloxbean.cardano.client.backend.model.TxContentUtxo;
import com.bloxbean.cardano.client.backend.ogmios.http.dto.BaseRequestDTO;
import com.bloxbean.cardano.client.backend.ogmios.http.dto.ValidateTransactionDTO;
import com.bloxbean.cardano.client.util.HexUtil;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.util.*;

public class OgmiosTransactionService extends OgmiosBaseService implements TransactionService {
    private final static String SUBMIT_TRANSACTION_METHOD = "submitTransaction";
    private final static String EVALUATE_TRANSACTION_METHOD = "evaluateTransaction";
    private final OgmiosHTTPApi ogmiosHTTPApi;

    public OgmiosTransactionService(String baseUrl) {
        super(baseUrl);
        this.ogmiosHTTPApi = getRetrofit().create(OgmiosHTTPApi.class);
    }

    @Override
    public Result<String> submitTransaction(byte[] cborData) throws ApiException {
        Map<String, Map<String, String>> params = new HashMap();
        params.put("transaction",
        new HashMap() {{
            put("cbor", HexUtil.encodeHexString(cborData));
        }});

        BaseRequestDTO request = new BaseRequestDTO(SUBMIT_TRANSACTION_METHOD, params);

        Call<BaseRequestDTO<Map<String, Map<String, String>>>> call = ogmiosHTTPApi.submitTransaction(request);
        try {
            Response<BaseRequestDTO<Map<String, Map<String, String>>>> response = call.execute();
            if (response.isSuccessful()) {
                String id = response.body().getResult().get("transaction").get("id");
                return Result.success(response.toString()).withValue(id).code(response.code());
            }
            else
                return Result.error(response.errorBody().string()).code(response.code());

        } catch (IOException e) {
            throw new ApiException("Error getting latest epoch", e);
        }
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
        Map<String, Map<String, String>> params = new HashMap();
        params.put("transaction",
                new HashMap() {{
                    put("cbor", HexUtil.encodeHexString(cborData));
                }});

        BaseRequestDTO request = new BaseRequestDTO(EVALUATE_TRANSACTION_METHOD, params);

        Call<BaseRequestDTO<List<ValidateTransactionDTO>>> call = ogmiosHTTPApi.validateTransaction(request);
        try {
            Response<BaseRequestDTO<List<ValidateTransactionDTO>>> response = call.execute();
            if (response.isSuccessful()) {
                List<EvaluationResult> evaluationResultList = new ArrayList<>();
                for (ValidateTransactionDTO executionUnitDTO : response.body().getResult()) {
                    evaluationResultList.add(executionUnitDTO.toEvaluationResult());
                }
                return Result.success(response.toString()).withValue(evaluationResultList).code(response.code());
            }
            else
                return Result.error(response.errorBody().string()).code(response.code());

        } catch (IOException e) {
            throw new ApiException("Error getting latest epoch", e);
        }
    }
}
