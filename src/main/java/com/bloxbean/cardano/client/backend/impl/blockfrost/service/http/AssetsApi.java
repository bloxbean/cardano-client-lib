package com.bloxbean.cardano.client.backend.impl.blockfrost.service.http;

import com.bloxbean.cardano.client.backend.model.Asset;
import com.bloxbean.cardano.client.backend.model.AssetAddress;
import com.bloxbean.cardano.client.backend.model.PolicyAsset;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;
import retrofit2.http.Query;

import java.util.List;

public interface AssetsApi {

    /**
     * Specific asset
     * Information about a specific asset
     *
     * @param assetId Concatenation of the policy_id and hex-encoded asset_name (required)
     * @return Call&lt;Asset&gt;
     */
    @GET("assets/{asset}")
    Call<Asset> getAsset(@Header("project_id") String projectId, @Path("asset") String assetId);

    /**
     * Asset addresses
     * List of a addresses containing a specific asset
     *
     * @param asset Concatenation of the policy_id and hex-encoded asset_name (required)
     * @param count The number of results displayed on one page. (optional, default to 100)
     * @param page  The page number for listing the results. (optional, default to 1)
     * @param order The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  (optional, default to asc)
     * @return Call&lt;List&lt;Object&gt;&gt;
     */
    @GET("assets/{asset}/addresses")
    Call<List<AssetAddress>> assetsAssetAddressesGet(@Header("project_id") String projectId, @Path("asset") String asset, @Query("count") Integer count, @Query("page") Integer page, @Query("order") String order);

    /**
     * Assets of a specific policy
     * List of asset minted under a specific policy
     *
     * @param policyId Specific policy_id (required)
     * @param count    The number of results displayed on one page. (optional, default to 100)
     * @param page     The page number for listing the results. (optional, default to 1)
     * @param order    The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  (optional, default to asc)
     * @return Call&lt;List&lt;PolicyAsset&gt;&gt;
     */
    @GET("assets/policy/{policy_id}")
    Call<List<PolicyAsset>> assetsPolicyPolicyIdGet(@Header("project_id") String projectId, @Path("policy_id") String policyId, @Query("count") Integer count, @Query("page") Integer page, @Query("order") String order);
}