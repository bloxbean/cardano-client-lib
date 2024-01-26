package com.bloxbean.cardano.client.supplier.ogmios;

import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.supplier.ogmios.dto.BaseRequestDto;
import com.bloxbean.cardano.client.supplier.ogmios.dto.EvaluateTransactionResponeDto;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.util.*;

@Slf4j
public class OgmiosTransactionProcessor extends OgmiosBaseService implements TransactionProcessor {
    private final static String SUBMIT_TRANSACTION_METHOD = "submitTransaction";
    private final static String EVALUATE_TRANSACTION_METHOD = "evaluateTransaction";
    private final OgmiosHTTPApi ogmiosHTTPApi;

    private ObjectMapper objectMapper = new ObjectMapper();

    public OgmiosTransactionProcessor(String baseUrl) {
        super(baseUrl);
        this.ogmiosHTTPApi = getRetrofit().create(OgmiosHTTPApi.class);
    }

    /**
     * @param cbor       - CBOR serialized transaction
     * @param inputUtxos - This is ignored for Ogmios
     * @return
     * @throws ApiException
     */
    @Override
    public Result<List<EvaluationResult>> evaluateTx(byte[] cbor, Set<Utxo> inputUtxos) throws ApiException {
        return evaluateTx(cbor);
    }

    @Override
    public Result<List<EvaluationResult>> evaluateTx(byte[] cbor) throws ApiException {
        Map<String, Map<String, String>> params = new HashMap();
        params.put("transaction",
                new HashMap() {{
                    put("cbor", HexUtil.encodeHexString(cbor));
                }});

        BaseRequestDto request = new BaseRequestDto(EVALUATE_TRANSACTION_METHOD, params);
        return evalTx(request);
    }

    @Override
    public Result<List<EvaluationResult>> evaluateTx(Transaction transaction, Set<Utxo> inputUtxos) throws ApiException {
        try {
            return evaluateTx(transaction.serialize(), inputUtxos);
        } catch (CborSerializationException e) {
            throw new RuntimeException("Couldn't deserialize Transaction", e);
        }
    }

    @Override
    public Result<String> submitTransaction(byte[] cborData) throws ApiException {
        Map<String, Map<String, String>> params = new HashMap();
        params.put("transaction",
                new HashMap() {{
                    put("cbor", HexUtil.encodeHexString(cborData));
                }});

        BaseRequestDto request = new BaseRequestDto(SUBMIT_TRANSACTION_METHOD, params);

        Call<BaseRequestDto<Map<String, Map<String, String>>>> call = ogmiosHTTPApi.submitTransaction(request);
        try {
            Response<BaseRequestDto<Map<String, Map<String, String>>>> response = call.execute();
            if (response.isSuccessful()) {
                String id = response.body().getResult().get("transaction").get("id");
                return Result.success(response.toString()).withValue(id).code(response.code());
            } else
                return Result.error(response.errorBody().string()).code(response.code());

        } catch (IOException e) {
            throw new ApiException("Error getting latest epoch", e);
        }
    }

    private Result evalTx(BaseRequestDto request) throws ApiException {
        Call<BaseRequestDto<List<EvaluateTransactionResponeDto>>> call = ogmiosHTTPApi.evaluateTransaction(request);
        try {
            Response<BaseRequestDto<List<EvaluateTransactionResponeDto>>> response = call.execute();
            if (response.isSuccessful()) {
                List<EvaluationResult> evaluationResultList = new ArrayList<>();
                for (EvaluateTransactionResponeDto executionUnitDTO : response.body().getResult()) {
                    evaluationResultList.add(executionUnitDTO.toEvaluationResult());
                }
                return Result.success(response.body().toString()).withValue(evaluationResultList).code(response.code());
            } else {
                String error = null;
                String errorBody = response.errorBody().string();
                try {
                    var jsonNode = objectMapper.readTree(errorBody);
                    if (jsonNode != null)
                        error = jsonNode.get("error").toString();
                    else
                        error = errorBody;
                } catch (Exception e) {
                    log.error("Error parsing error response", e);
                }

                return Result.error(error).code(response.code());
            }

        } catch (IOException e) {
            throw new ApiException("Error getting latest epoch", e);
        }
    }
}
