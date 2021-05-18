package com.bloxbean.cardano.client.backend.impl.blockfrost.service.http;

import com.bloxbean.cardano.client.backend.model.Block;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;

import java.math.BigInteger;

public interface BlockApi {

    @GET("blocks/latest")
    Call<Block> getLatestBlock(@Header("project_id") String projectId);

    @GET("blocks/{hash}")
    Call<Block> getBlockByHash(@Header("project_id") String projectId, @Path("hash") String hash);

    @GET("blocks/{number}")
    Call<Block> getBlockByNumber(@Header("project_id") String projectId, @Path("number") BigInteger number);
}
