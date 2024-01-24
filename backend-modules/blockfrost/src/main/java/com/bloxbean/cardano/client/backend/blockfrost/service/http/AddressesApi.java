package com.bloxbean.cardano.client.backend.blockfrost.service.http;

import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.backend.model.AddressContent;
import com.bloxbean.cardano.client.backend.model.AddressDetails;
import com.bloxbean.cardano.client.backend.model.AddressTransactionContent;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;
import retrofit2.http.Query;

import java.util.List;

public interface AddressesApi {

    @GET("addresses/{address}")
    Call<AddressContent> getAddressInfo(@Header("project_id") String projectId, @Path("address") String address);

    @GET("addresses/{address}/total")
    Call<AddressDetails> getAddressDetails(@Header("project_id") String projectId, @Path("address") String address);

    @GET("addresses/{address}/utxos")
    Call<List<Utxo>> getUtxos(@Header("project_id") String projectId, @Path("address") String address,
                              @Query("count") int count, @Query("page") int page, @Query("order") String order);

    @GET("addresses/{address}/utxos/{asset}")
    Call<List<Utxo>> getUtxosByAsset(@Header("project_id") String projectId, @Path("address") String address, @Path("asset") String asset,
                              @Query("count") int count, @Query("page") int page, @Query("order") String order);

    @GET("addresses/{address}/transactions")
    Call<List<AddressTransactionContent>> getTransactions(@Header("project_id") String projectId, @Path("address") String address,
                                                          @Query("count") int count, @Query("page") int page, @Query("order") String order,
                                                          @Query("from") String from, @Query("to") String to);
}
