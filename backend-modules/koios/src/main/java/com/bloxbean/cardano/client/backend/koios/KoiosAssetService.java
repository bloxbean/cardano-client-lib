package com.bloxbean.cardano.client.backend.koios;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.util.AssetUtil;
import com.bloxbean.cardano.client.backend.api.AssetService;
import com.bloxbean.cardano.client.backend.model.Asset;
import com.bloxbean.cardano.client.backend.model.AssetAddress;
import com.bloxbean.cardano.client.backend.model.AssetTransactionContent;
import com.bloxbean.cardano.client.backend.model.PolicyAsset;
import com.bloxbean.cardano.client.util.Tuple;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import rest.koios.client.backend.api.asset.model.AssetInformation;
import rest.koios.client.backend.factory.options.Limit;
import rest.koios.client.backend.factory.options.Offset;
import rest.koios.client.backend.factory.options.Options;

import java.util.ArrayList;
import java.util.List;

/**
 * Koios Asset Service
 */
public class KoiosAssetService implements AssetService {

    /**
     * Object Mapper
     */
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final rest.koios.client.backend.api.asset.AssetService assetService;

    /**
     * KoiosAssetService Constructor
     *
     * @param assetService assetService
     */
    public KoiosAssetService(rest.koios.client.backend.api.asset.AssetService assetService) {
        this.assetService = assetService;
    }

    @Override
    public Result<Asset> getAsset(String unit) throws ApiException {
        try {
            Tuple<String, String> assetTuple = AssetUtil.getPolicyIdAndAssetName(unit);
            rest.koios.client.backend.api.base.Result<AssetInformation> assetInformation = assetService.getAssetInformation(assetTuple._1, assetTuple._2.replace("0x", ""));
            if (!assetInformation.isSuccessful()) {
                return Result.error(assetInformation.getResponse()).code(assetInformation.getCode());
            }
            return convertToAsset(assetInformation.getValue());
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    private Result<Asset> convertToAsset(AssetInformation assetInformation) {
        Asset asset = new Asset();
        asset.setAsset(assetInformation.getPolicyId() + assetInformation.getAssetName());
        asset.setPolicyId(assetInformation.getPolicyId());
        asset.setAssetName(assetInformation.getAssetName());
        asset.setFingerprint(assetInformation.getFingerprint());
        asset.setQuantity(assetInformation.getTotalSupply());
        asset.setInitialMintTxHash(assetInformation.getMintingTxHash());
        asset.setMintOrBurnCount(assetInformation.getMintCnt() + assetInformation.getBurnCnt());
        if (assetInformation.getMintingTxMetadata() != null) {
            asset.setOnchainMetadata(assetInformation.getMintingTxMetadata());
        }
        if (assetInformation.getTokenRegistryMetadata() != null) {
            asset.setMetadata(objectMapper.convertValue(assetInformation.getTokenRegistryMetadata(), JsonNode.class));
        }
        return Result.success("OK").withValue(asset).code(200);
    }

    @Override
    public Result<List<AssetAddress>> getAllAssetAddresses(String asset) throws ApiException {
        validateAsset(asset);
        List<AssetAddress> assetAddresses = new ArrayList<>();
        int page = 1;
        Result<List<AssetAddress>> assetAddressesResult = getAssetAddresses(asset, 1000, page);
        while (assetAddressesResult.isSuccessful()) {
            assetAddresses.addAll(assetAddressesResult.getValue());
            if (assetAddressesResult.getValue().size() != 1000) {
                break;
            } else {
                page++;
                assetAddressesResult = getAssetAddresses(asset, 1000, page);
            }
        }
        if (!assetAddressesResult.isSuccessful()) {
            return assetAddressesResult;
        } else {
            return Result.success(assetAddressesResult.toString()).withValue(assetAddresses).code(assetAddressesResult.code());
        }
    }

    @Override
    public Result<List<AssetAddress>> getAssetAddresses(String unit, int count, int page, OrderEnum order) throws ApiException {
        try {
            Tuple<String, String> assetTuple = AssetUtil.getPolicyIdAndAssetName(unit);
            Options options = Options.builder()
                    .option(Limit.of(count))
                    .option(Offset.of((long) (page - 1) * count))
                    .build();
            rest.koios.client.backend.api.base.Result<List<rest.koios.client.backend.api.asset.model.AssetAddress>>
                    assetsAddressList = assetService.getAssetsAddresses(assetTuple._1, assetTuple._2.replace("0x", ""), options);
            if (!assetsAddressList.isSuccessful()) {
                return Result.error(assetsAddressList.getResponse()).code(assetsAddressList.getCode());
            }
            return convertToAssetAddresses(assetsAddressList.getValue());
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<AssetAddress>> getAssetAddresses(String asset, int count, int page) throws ApiException {
        return getAssetAddresses(asset, count, page, null);
    }

    private Result<List<AssetAddress>> convertToAssetAddresses(List<rest.koios.client.backend.api.asset.model.AssetAddress> assetAddresses) {
        List<AssetAddress> assetAddressList = new ArrayList<>();
        assetAddresses.forEach(element -> assetAddressList.add(new AssetAddress(element.getPaymentAddress(), element.getQuantity())));
        return Result.success("OK").withValue(assetAddressList).code(200);
    }

    @Override
    public Result<List<PolicyAsset>> getAllPolicyAssets(String policyId) throws ApiException {
        List<PolicyAsset> policyAssets = new ArrayList<>();
        int page = 1;
        Result<List<PolicyAsset>> policyAssetsResult = getPolicyAssets(policyId, 1000, page);
        while (policyAssetsResult.isSuccessful()) {
            policyAssets.addAll(policyAssetsResult.getValue());
            if (policyAssetsResult.getValue().size() != 1000) {
                break;
            } else {
                page++;
                policyAssetsResult = getPolicyAssets(policyId, 1000, page);
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
        try {
            Options options = Options.builder()
                    .option(Limit.of(count))
                    .option(Offset.of((long) (page - 1) * count))
                    .build();
            rest.koios.client.backend.api.base.Result<List<rest.koios.client.backend.api.asset.model.PolicyAssetInfo>> assetPolicyInfoResult = assetService.getPolicyAssetInformation(policyId, options);
            if (!assetPolicyInfoResult.isSuccessful()) {
                return Result.error(assetPolicyInfoResult.getResponse()).code(assetPolicyInfoResult.getCode());
            }
            return convertToPolicyAssetList(policyId, assetPolicyInfoResult.getValue());
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<PolicyAsset>> getPolicyAssets(String policyId, int count, int page) throws ApiException {
        return getPolicyAssets(policyId, count, page, null);
    }

    private Result<List<PolicyAsset>> convertToPolicyAssetList(String policyId, List<rest.koios.client.backend.api.asset.model.PolicyAssetInfo> policyAssets) {
        List<PolicyAsset> policyAssetList = new ArrayList<>();
        if (policyAssets != null) {
            policyAssets.forEach(policyAsset -> policyAssetList.add(new PolicyAsset(policyId + policyAsset.getAssetName(), policyAsset.getTotalSupply())));
        }
        return Result.success("OK").withValue(policyAssetList).code(200);
    }

    private void validateAsset(String asset) throws ApiException {
        if (asset == null || asset.equals("")) {
            throw new ApiException("Asset cannot be null or empty");
        }
    }

    @Override
    public Result<List<AssetTransactionContent>> getTransactions(String asset, int count, int page, OrderEnum order) throws ApiException {
        throw new UnsupportedOperationException("Not supported yet");
    }
}
