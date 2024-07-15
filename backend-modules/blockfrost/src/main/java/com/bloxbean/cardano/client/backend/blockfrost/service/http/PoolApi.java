package com.bloxbean.cardano.client.backend.blockfrost.service.http;

import com.bloxbean.cardano.client.backend.model.PoolInfo;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;

public interface PoolApi {

    @GET("pools/{pool_id}")
    Call<PoolInfo> getPoolInfo(@Header("project_id") String projectId, @Path("pool_id") String poolId);
}
