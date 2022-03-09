package com.bloxbean.cardano.client.backend.koios;

import com.bloxbean.cardano.client.backend.api.AssetService;
import com.bloxbean.cardano.client.backend.common.OrderEnum;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Asset;
import com.bloxbean.cardano.client.backend.model.AssetAddress;
import com.bloxbean.cardano.client.backend.model.PolicyAsset;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.cardano.client.util.AssetUtil;
import com.bloxbean.cardano.client.util.Tuple;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import rest.koios.client.backend.api.asset.model.AssetInformation;
import rest.koios.client.backend.api.asset.model.AssetPolicyInfo;
import rest.koios.client.backend.factory.options.Limit;
import rest.koios.client.backend.factory.options.Offset;
import rest.koios.client.backend.factory.options.Options;

import java.util.ArrayList;
import java.util.List;

public class KoiosAssetService implements AssetService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final rest.koios.client.backend.api.asset.AssetService assetService;

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
            JsonNode jsonNode = objectMapper.convertValue(assetInformation.getMintingTxMetadata().getJson(), JsonNode.class);
            if (jsonNode.get(assetInformation.getPolicyId()) != null && jsonNode.get(assetInformation.getPolicyId()).get(assetInformation.getAssetNameAscii()) != null) {
                asset.setOnchainMetadata(jsonNode.get(assetInformation.getPolicyId()).get(assetInformation.getAssetNameAscii()));
            }
        }
        if (assetInformation.getTokenRegistryMetadata() != null) {
            asset.setMetadata(objectMapper.convertValue(assetInformation.getTokenRegistryMetadata(), JsonNode.class));
        }
        return Result.success("OK").withValue(asset).code(200);
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
                    assetsAddressList = assetService.getAssetsAddressList(assetTuple._1, assetTuple._2.replace("0x", ""), options);
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
    public Result<List<PolicyAsset>> getPolicyAssets(String policyId, int count, int page, OrderEnum order) throws ApiException {
        try {
            rest.koios.client.backend.api.base.Result<AssetPolicyInfo> assetPolicyInfoResult = assetService.getAssetPolicyInformation(policyId);
            if (!assetPolicyInfoResult.isSuccessful()) {
                return Result.error(assetPolicyInfoResult.getResponse()).code(assetPolicyInfoResult.getCode());
            }
            return convertToPolicyAssetList(assetPolicyInfoResult.getValue());
        } catch (rest.koios.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<PolicyAsset>> getPolicyAssets(String policyId, int count, int page) throws ApiException {
        return getPolicyAssets(policyId, count, page, null);
    }

    private Result<List<PolicyAsset>> convertToPolicyAssetList(AssetPolicyInfo assetPolicyInfo) {
        List<PolicyAsset> policyAssetList = new ArrayList<>();
        if (assetPolicyInfo.getAssets() != null) {
            assetPolicyInfo.getAssets().forEach(policyAsset -> {
                policyAssetList.add(new PolicyAsset(assetPolicyInfo.getPolicyId() + policyAsset.getAssetName(), policyAsset.getTotalSupply()));
            });
        }
        return Result.success("OK").withValue(policyAssetList).code(200);
    }
}
