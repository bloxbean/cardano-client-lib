package com.bloxbean.cardano.client.backend.blockfrost.service;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.AssetService;
import com.bloxbean.cardano.client.backend.blockfrost.service.http.AssetsApi;
import com.bloxbean.cardano.client.backend.model.Asset;
import com.bloxbean.cardano.client.backend.model.AssetAddress;
import com.bloxbean.cardano.client.backend.model.AssetTransactionContent;
import com.bloxbean.cardano.client.backend.model.PolicyAsset;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
            return processResponse(response);
        } catch (IOException e) {
            throw new ApiException("Error getting asset info", e);
        }
    }

    @Override
    public Result<List<AssetAddress>> getAllAssetAddresses(String asset) throws ApiException {
        validateAsset(asset);
        List<AssetAddress> assetAddresses = new ArrayList<>();
        int page = 1;
        Result<List<AssetAddress>> assetAddressesResult = getAssetAddresses(asset, 100, page);
        while (assetAddressesResult.isSuccessful()) {
            assetAddresses.addAll(assetAddressesResult.getValue());
            if (assetAddressesResult.getValue().size() != 100) {
                break;
            } else {
                page++;
                assetAddressesResult = getAssetAddresses(asset, 100, page);
            }
        }
        if (!assetAddressesResult.isSuccessful()) {
            return assetAddressesResult;
        } else {
            return Result.success(assetAddressesResult.toString()).withValue(assetAddresses).code(assetAddressesResult.code());
        }
    }

    @Override
    public Result<List<AssetAddress>> getAssetAddresses(String asset, int count, int page, OrderEnum order) throws ApiException {
        validateAsset(asset);
        Call<List<AssetAddress>> assetAddressCall = assetsApi.assetsAssetAddressesGet(getProjectId(), asset, count, page, order.name());
        try {
            Response<List<AssetAddress>> assetAddressResponse = assetAddressCall.execute();
            return processResponse(assetAddressResponse);
        } catch (IOException exp) {
            throw new ApiException("Exception while fetching addresses for asset: " + asset, exp);
        }
    }

    private void validateAsset(String asset) throws ApiException {
        if (asset == null || asset.equals("")) {
            throw new ApiException("Asset cannot be null or empty");
        }
    }

    @Override
    public Result<List<AssetAddress>> getAssetAddresses(String asset, int count, int page) throws ApiException {
        return getAssetAddresses(asset, count, page, OrderEnum.asc);
    }

    @Override
    public Result<List<PolicyAsset>> getAllPolicyAssets(String policyId) throws ApiException {
        List<PolicyAsset> policyAssets = new ArrayList<>();
        int page = 1;
        Result<List<PolicyAsset>> policyAssetsResult = getPolicyAssets(policyId, 100, page);
        while (policyAssetsResult.isSuccessful()) {
            policyAssets.addAll(policyAssetsResult.getValue());
            if (policyAssetsResult.getValue().size() != 100) {
                break;
            } else {
                page++;
                policyAssetsResult = getPolicyAssets(policyId, 100, page);
            }
        }
        if (!policyAssetsResult.isSuccessful()) {
            return policyAssetsResult;
        } else {
            return Result.success(policyAssetsResult.toString()).withValue(policyAssets).code(policyAssetsResult.code());
        }
    }

    @Override
    public Result<List<PolicyAsset>> getPolicyAssets(String policyId, int count, int page, OrderEnum order) throws ApiException {
        if (policyId == null || policyId.equals("")) {
            throw new ApiException("PolicyId cannot be null or empty");
        }
        Call<List<PolicyAsset>> policyAssetsCall = assetsApi.assetsPolicyPolicyIdGet(getProjectId(), policyId, count, page, order.name());
        try {
            Response<List<PolicyAsset>> policyAssetsResponse = policyAssetsCall.execute();
            return processResponse(policyAssetsResponse);
        } catch (IOException exp) {
            throw new ApiException("Exception while fetching assets for policy: " + policyId, exp);
        }
    }

    @Override
    public Result<List<PolicyAsset>> getPolicyAssets(String policyId, int count, int page) throws ApiException {
        return getPolicyAssets(policyId, count, page, OrderEnum.asc);
    }

    @Override
    public Result<List<AssetTransactionContent>> getTransactions(String unit, int count, int page, OrderEnum order) throws ApiException {
        if(order == null)
            order = OrderEnum.asc;

        Call<List<AssetTransactionContent>> call = assetsApi.getTransactions(getProjectId(), unit, count, page, order.toString());

        try {
            Response<List<AssetTransactionContent>> response = call.execute();
            return processResponse(response);
        } catch (IOException e) {
            throw new ApiException("Error getting asset transactions for " + unit , e);
        }
    }
}
