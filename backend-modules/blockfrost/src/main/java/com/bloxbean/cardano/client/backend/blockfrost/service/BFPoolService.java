package com.bloxbean.cardano.client.backend.blockfrost.service;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.PoolService;
import com.bloxbean.cardano.client.backend.blockfrost.service.http.PoolApi;
import com.bloxbean.cardano.client.backend.model.PoolInfo;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;

public class BFPoolService extends BFBaseService implements PoolService {

    private final PoolApi poolApi;

    public BFPoolService(String baseUrl, String projectId) {
        super(baseUrl, projectId);
        this.poolApi = getRetrofit().create(PoolApi.class);
    }

    @Override
    public Result<PoolInfo> getPoolInfo(String poolId) throws ApiException {
        Call<PoolInfo> poolInfoCall = poolApi.getPoolInfo(getProjectId(), poolId);
        try {
            Response<PoolInfo> response = poolInfoCall.execute();
            if (response.isSuccessful()) {
                return Result.success(response.toString()).withValue(response.body()).code(response.code());
            } else {
                return Result.error(response.errorBody().string()).code(response.code());
            }
        } catch (IOException e) {
            throw new ApiException("Error getting Pool Info by Pool Id", e);
        }
    }
}
