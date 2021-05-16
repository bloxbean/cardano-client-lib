package com.bloxbean.cardano.client.backend.impl.blockfrost.service;

import com.bloxbean.cardano.client.backend.api.AssetService;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.impl.blockfrost.service.http.AssetsApi;
import com.bloxbean.cardano.client.backend.model.Asset;
import com.bloxbean.cardano.client.backend.model.Result;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;

public class BFAssetService extends BFBaseService implements AssetService {

    private final AssetsApi assetsApi;

    public BFAssetService(String baseUrl, String projectId) {
        super(baseUrl, projectId);
        this.assetsApi = getRetrofit().create(AssetsApi.class);
    }

    @Override
    public Result<Asset> getAsset(String assetId) throws ApiException {
        Call<Asset> assetReponse = assetsApi.getAsset(getProjectId(), assetId);

        try {
            Response<Asset> response = assetReponse.execute();
            if(response.isSuccessful())
                return Result.success(response.toString()).withValue(response.body()).code(response.code());
            else
                return Result.error(response.errorBody().string()).code(response.code());

        } catch (IOException e) {
            throw new ApiException("Error getting asset info", e);
        }
    }
}
