package com.bloxbean.cardano.client.backend.impl.blockfrost.service;

import com.bloxbean.cardano.client.backend.api.EpochService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.impl.blockfrost.service.http.EpochApi;
import com.bloxbean.cardano.client.backend.model.EpochContent;
import com.bloxbean.cardano.client.backend.model.ProtocolParams;
import com.bloxbean.cardano.client.backend.model.Result;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;

public class BFEpochService extends BFBaseService implements EpochService {

    private EpochApi epochApi;

    public BFEpochService(String baseUrl, String projectId) {
        super(baseUrl, projectId);
        this.epochApi = getRetrofit().create(EpochApi.class);
    }

    @Override
    public Result<EpochContent> getLatestEpoch() throws ApiException {
        Call<EpochContent> call = epochApi.getLatestEpoch(getProjectId());

        try {
            Response<EpochContent> response = call.execute();
            if (response.isSuccessful())
                return Result.success(response.toString()).withValue(response.body()).code(response.code());
            else
                return Result.error(response.errorBody().string()).code(response.code());

        } catch (IOException e) {
            throw new ApiException("Error getting latest epoch", e);
        }
    }

    @Override
    public Result<EpochContent> getEpoch(Integer epoch) throws ApiException {
        Call<EpochContent> call = epochApi.getEpochByNumber(getProjectId(), epoch);

        try {
            Response<EpochContent> response = call.execute();
            if (response.isSuccessful())
                return Result.success(response.toString()).withValue(response.body()).code(response.code());
            else
                return Result.error(response.errorBody().string()).code(response.code());

        } catch (IOException e) {
            throw new ApiException("Error getting epoch by number : " + epoch, e);
        }
    }

    @Override
    public Result<ProtocolParams> getProtocolParameters(Integer epoch) throws ApiException {
        Call<ProtocolParams> call = epochApi.getProtocolParameters(getProjectId(), epoch);

        try {
            Response<ProtocolParams> response = call.execute();
            if (response.isSuccessful())
                return Result.success(response.toString()).withValue(response.body()).code(response.code());
            else
                return Result.error(response.errorBody().string()).code(response.code());

        } catch (IOException e) {
            throw new ApiException("Error getting protocol parameters by number : " + epoch, e);
        }
    }

    @Override
    public Result<ProtocolParams> getProtocolParameters() throws ApiException {
        Result<EpochContent> epochContentResult = getLatestEpoch();
        if(!epochContentResult.isSuccessful())
            throw new ApiException("Unable to get latest epoch info to get protocol parameters");

        EpochContent epochContent = epochContentResult.getValue();
        if(epochContent == null)
            throw new ApiException("Unable to get latest epoch info to get protocol parameters");

        return getProtocolParameters(epochContent.getEpoch());
    }
}
