package com.bloxbean.cardano.client.supplier.ogmios;

import com.bloxbean.cardano.client.api.TransactionProcessor;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.EvaluationResult;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.supplier.ogmios.dto.AdditionalUtxoDTO;
import com.bloxbean.cardano.client.supplier.ogmios.dto.BaseRequestDTO;
import com.bloxbean.cardano.client.supplier.ogmios.dto.ValidateTransactionDTO;
import com.bloxbean.cardano.client.transaction.spec.Transaction;
import com.bloxbean.cardano.client.util.HexUtil;
import org.jetbrains.annotations.NotNull;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.util.*;

public class OgmiosTransactionProcessor extends OgmiosBaseService implements TransactionProcessor {
    private final static String SUBMIT_TRANSACTION_METHOD = "submitTransaction";
    private final static String EVALUATE_TRANSACTION_METHOD = "evaluateTransaction";
    private final OgmiosHTTPApi ogmiosHTTPApi;

    public OgmiosTransactionProcessor(String baseUrl) {
        super(baseUrl);
        this.ogmiosHTTPApi = getRetrofit().create(OgmiosHTTPApi.class);
    }

    @Override
    public Result<List<EvaluationResult>> evaluateTx(byte[] cbor, Set<Utxo> inputUtxos) throws ApiException {
        Map<String, Map<String, String>> params = new HashMap();
        params.put("transaction",
                new HashMap() {{
                    put("cbor", HexUtil.encodeHexString(cbor));
                    put("additionalUtxos", getAdditionalUtxoDTOS(inputUtxos));
                }});

        BaseRequestDTO request = new BaseRequestDTO(EVALUATE_TRANSACTION_METHOD, params);
        return evalTx(request);
    }

    @Override
    public Result<List<EvaluationResult>> evaluateTx(byte[] cbor) throws ApiException {
        Map<String, Map<String, String>> params = new HashMap();
        params.put("transaction",
                new HashMap() {{
                    put("cbor", HexUtil.encodeHexString(cbor));
                }});

        BaseRequestDTO request = new BaseRequestDTO(EVALUATE_TRANSACTION_METHOD, params);
        return evalTx(request);
    }

    @Override
    public Result<List<EvaluationResult>> evaluateTx(Transaction transaction, Set<Utxo> inputUtxos) throws ApiException {
        Map<String, Map<String, String>> params = new HashMap();
        params.put("transaction",
                new HashMap() {{
                    try {
                        put("cbor", HexUtil.encodeHexString(transaction.serialize()));
                    } catch (CborSerializationException e) {
                        throw new RuntimeException("Couldn't deserialize Transaction", e);
                    }
                    put("additionalUtxos", getAdditionalUtxoDTOS(inputUtxos));
                }});

        BaseRequestDTO request = new BaseRequestDTO(EVALUATE_TRANSACTION_METHOD, params);
        return evalTx(request);
    }

    @NotNull
    private static List<AdditionalUtxoDTO> getAdditionalUtxoDTOS(Set<Utxo> inputUtxos) {
        List<AdditionalUtxoDTO> additionalUtxos = new ArrayList<>();
        for (Utxo utxo : inputUtxos) {
            additionalUtxos.add(AdditionalUtxoDTO.fromUtxo(utxo));
        }
        return additionalUtxos;
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



    private Result evalTx(BaseRequestDTO request) throws ApiException {
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
