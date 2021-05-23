package com.bloxbean.cardano.client.backend.impl.blockfrost.service.http;

import com.bloxbean.cardano.client.backend.model.AddressContent;
import com.bloxbean.cardano.client.backend.model.Utxo;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;
import retrofit2.http.Query;

import java.util.List;

public interface AddressesApi {
    @GET("addresses/{address}/utxos")
    Call<List<Utxo>> getUtxos(@Header("project_id") String projectId, @Path("address") String address,
                              @Query("count") int count, @Query("page") int page, @Query("order") String order);

    @GET("addresses/{address}")
    Call<AddressContent> getAddressInfo(@Header("project_id") String projectId, @Path("address") String address);

    @GET("addresses/{address}/txs")
    Call<List<String>> getTransactions(@Header("project_id") String projectId, @Path("address") String address,
                              @Query("count") int count, @Query("page") int page, @Query("order") String order);
}
