package com.bloxbean.cardano.client.backend.blockfrost.service.http;

import com.bloxbean.cardano.client.backend.model.*;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;
import retrofit2.http.Query;

import java.util.List;

public interface AccountApi {

    @GET("accounts/{stake_address}")
    Call<AccountInformation> getAccountInformation(@Header("project_id") String projectId, @Path("stake_address") String stakeAddress);

    @GET("accounts/{stake_address}/rewards")
    Call<List<AccountRewardsHistory>> getAccountRewardsHistory(@Header("project_id") String projectId, @Path("stake_address") String stakeAddress,
                                                               @Query("count") int count, @Query("page") int page, @Query("order") String order);

    @GET("accounts/{stake_address}/history")
    Call<List<AccountHistory>> getAccountHistory(@Header("project_id") String projectId, @Path("stake_address") String stakeAddress,
                                                 @Query("count") int count, @Query("page") int page, @Query("order") String order);

    @GET("accounts/{stake_address}/addresses")
    Call<List<AccountAddress>> getAccountAddresses(@Header("project_id") String projectId, @Path("stake_address") String stakeAddress,
                                                   @Query("count") int count, @Query("page") int page, @Query("order") String order);

    @GET("accounts/{stake_address}/addresses/assets")
    Call<List<AccountAsset>> getAccountAssets(@Header("project_id") String projectId, @Path("stake_address") String stakeAddress,
                                              @Query("count") int count, @Query("page") int page, @Query("order") String order);
}
