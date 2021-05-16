package com.bloxbean.cardano.client.backend.impl.blockfrost.service.http;

import com.bloxbean.cardano.client.backend.model.Asset;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;

public interface AssetsApi {
    @GET("assets/{asset}")
    Call<Asset> getAsset(@Header("project_id")  String projectId, @Path("asset") String assetId);
}
