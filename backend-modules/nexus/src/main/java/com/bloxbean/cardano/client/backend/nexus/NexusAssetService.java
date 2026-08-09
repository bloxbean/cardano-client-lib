package com.bloxbean.cardano.client.backend.nexus;

import adlabs.nexus.client.backend.api.asset.model.AssetHolder;
import adlabs.nexus.client.util.Network;
import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.AssetService;
import com.bloxbean.cardano.client.backend.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Nexus Asset Service. {@link #getAsset(String)} and the asset-addresses methods are backed by the
 * SDK; policy-assets and asset transactions have no Nexus SDK equivalent yet.
 */
public class NexusAssetService implements AssetService {

    // policy id is a blake2b-224 hash: 28 bytes = 56 hex chars.
    private static final int POLICY_ID_HEX_LENGTH = 56;

    private final adlabs.nexus.client.backend.api.asset.AssetService assetService;
    private final Network network;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NexusAssetService(adlabs.nexus.client.backend.api.asset.AssetService assetService, Network network) {
        this.assetService = assetService;
        this.network = network;
    }

    @Override
    public Result<Asset> getAsset(String unit) throws ApiException {
        String[] parts = splitUnit(unit);
        try {
            return NexusResultMapper.map(assetService.getAssetDetailedInformation(network, parts[0], parts[1]),
                    info -> toAsset(unit, info));
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    // unit is policy_id (56 hex chars) + hex-encoded asset_name; asset_name is empty when unit is policy-id-only.
    private String[] splitUnit(String unit) throws ApiException {
        if (unit == null || unit.length() < POLICY_ID_HEX_LENGTH) {
            throw new ApiException("Invalid asset unit: " + unit);
        }
        return new String[]{unit.substring(0, POLICY_ID_HEX_LENGTH), unit.substring(POLICY_ID_HEX_LENGTH)};
    }

    private Asset toAsset(String unit, adlabs.nexus.client.backend.api.asset.model.AssetDetailedInformation info) {
        Asset asset = new Asset();
        asset.setAsset(unit);
        asset.setPolicyId(info.getPolicyId());
        asset.setAssetName(info.getAssetName());
        asset.setFingerprint(info.getFingerprint());
        asset.setQuantity(info.getQuantity());
        asset.setInitialMintTxHash(info.getInitialMintTxHash());
        asset.setMintOrBurnCount(info.getMintOrBurnCount());
        asset.setOnchainMetadata(parseOnchainMetadata(info.getOnchainMetadata()));
        if (info.getMetadata() != null) {
            asset.setMetadata(objectMapper.convertValue(info.getMetadata(), JsonNode.class));
        }
        return asset;
    }

    // SDK carries onchain metadata (CIP-25/label-721) as a raw JSON string; bloxbean wants a JsonNode.
    private JsonNode parseOnchainMetadata(String onchainMetadata) {
        if (onchainMetadata == null || onchainMetadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readTree(onchainMetadata);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    // Max page size the Nexus holders endpoint accepts.
    private static final int HOLDERS_MAX_PAGE_SIZE = 100;

    @Override
    public Result<List<AssetAddress>> getAllAssetAddresses(String asset) throws ApiException {
        try {
            return NexusResultMapper.map(assetService.getAssetHolders(network, asset, 1, HOLDERS_MAX_PAGE_SIZE),
                    this::toAssetAddresses);
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<AssetAddress>> getAssetAddresses(String asset, int count, int page) throws ApiException {
        return getAssetAddresses(asset, count, page, null);
    }

    // Holders endpoint is paginated server-side; Nexus has no order param, so order is ignored.
    @Override
    public Result<List<AssetAddress>> getAssetAddresses(String asset, int count, int page, OrderEnum order) throws ApiException {
        try {
            return NexusResultMapper.map(assetService.getAssetHolders(network, asset, page, count),
                    this::toAssetAddresses);
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    // The holders endpoint carries per-address quantity (unlike the old NFT-address endpoint).
    private List<AssetAddress> toAssetAddresses(List<AssetHolder> holders) {
        List<AssetAddress> result = new ArrayList<>();
        for (AssetHolder h : holders) {
            result.add(AssetAddress.builder().address(h.getAddress()).quantity(h.getQuantity()).build());
        }
        return result;
    }

    @Override
    public Result<List<PolicyAsset>> getAllPolicyAssets(String policyId) throws ApiException {
        throw new UnsupportedOperationException("getAllPolicyAssets not supported by Nexus");
    }

    @Override
    public Result<List<PolicyAsset>> getPolicyAssets(String policyId, int count, int page, OrderEnum order) throws ApiException {
        throw new UnsupportedOperationException("getPolicyAssets not supported by Nexus");
    }

    @Override
    public Result<List<PolicyAsset>> getPolicyAssets(String policyId, int count, int page) throws ApiException {
        throw new UnsupportedOperationException("getPolicyAssets not supported by Nexus");
    }

    @Override
    public Result<List<AssetTransactionContent>> getTransactions(String asset, int count, int page, OrderEnum order) throws ApiException {
        throw new UnsupportedOperationException("getTransactions not supported by Nexus");
    }
}
