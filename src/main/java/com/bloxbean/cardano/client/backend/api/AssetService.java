package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.backend.common.OrderEnum;
import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Asset;
import com.bloxbean.cardano.client.backend.model.AssetAddress;
import com.bloxbean.cardano.client.backend.model.PolicyAsset;
import com.bloxbean.cardano.client.backend.model.Result;

import java.util.List;

public interface AssetService {
    /**
     *
     * @param unit Concatenation of the policy_id and hex-encoded asset_name
     * @return
     * @throws ApiException
     */
    Result<Asset> getAsset(String unit) throws ApiException;

    /**
     * Asset addresses
     * List of addresses containing a specific asset
     *
     * @param asset Concatenation of the policy_id and hex-encoded asset_name (required)
     * @param count The number of results displayed on one page. (&lt;=100).
     * @param page  The page number for listing the results.
     * @param order The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last
     * @return List&lt;AssetAddress&gt;
     */
    Result<List<AssetAddress>> getAssetAddresses(String asset, int count, int page, OrderEnum order) throws ApiException;

    /**
     * Asset addresses
     * List of addresses containing a specific asset ordered ascending from the point of view of the blockchain, not the page listing itself.
     *
     * @param asset Concatenation of the policy_id and hex-encoded asset_name (required)
     * @param count The number of results displayed on one page. (&lt;=100).
     * @param page  The page number for listing the results.
     * @return List&lt;AssetAddress&gt;
     */
    Result<List<AssetAddress>> getAssetAddresses(String asset, int count, int page) throws ApiException;

    /**
     * Assets of a specific policy
     * List of asset minted under a specific policy
     *
     * @param policyId Specific policy_id (required)
     * @param count    The number of results displayed on one page. (&lt;=100).
     * @param page     The page number for listing the results.
     * @param order    The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.
     * @return List&lt;PolicyAsset&gt;&gt;
     */
    Result<List<PolicyAsset>> getPolicyAssets(String policyId, int count, int page, OrderEnum order) throws ApiException;

    /**
     * Assets of a specific policy
     * List of asset minted under a specific policy ordered ascending from the point of view of the blockchain, not the page listing itself.
     *
     * @param policyId Specific policy_id (required)
     * @param count    The number of results displayed on one page. (&lt;=100).
     * @param page     The page number for listing the results.
     * @return List&lt;PolicyAsset&gt;&gt;
     */
    Result<List<PolicyAsset>> getPolicyAssets(String policyId, int count, int page) throws ApiException;
}