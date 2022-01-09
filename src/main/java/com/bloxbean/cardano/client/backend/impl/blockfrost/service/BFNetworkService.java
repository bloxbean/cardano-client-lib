package com.bloxbean.cardano.client.backend.impl.blockfrost.service;

import com.bloxbean.cardano.client.backend.api.NetworkInfoService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.impl.blockfrost.service.http.CardanoLedgerApi;
import com.bloxbean.cardano.client.backend.model.Genesis;
import com.bloxbean.cardano.client.backend.model.Result;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;

public class BFNetworkService extends BFBaseService implements NetworkInfoService {

    private final CardanoLedgerApi ledgerApi;

    public BFNetworkService(String baseUrl, String projectId) {
        super(baseUrl, projectId);
        this.ledgerApi = getRetrofit().create(CardanoLedgerApi.class);
    }

    @Override
    public Result<Genesis> getNetworkInfo() throws ApiException {
        Call<Genesis> genesisCall = ledgerApi.genesis(getProjectId());
        try {
            Response<Genesis> response = genesisCall.execute();
            if (response.isSuccessful())
                return Result.success(response.toString()).withValue(response.body()).code(response.code());
            else
                return Result.error(response.errorBody().string()).code(response.code());

        } catch (IOException e) {
            throw new ApiException("Error getting genesis info", e);
        }
    }
}
