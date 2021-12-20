package com.bloxbean.cardano.client.backend.impl.blockfrost.service.http;

import com.bloxbean.cardano.client.backend.model.metadata.MetadataCBORContent;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataJSONContent;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataLabel;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;
import retrofit2.http.Query;

import java.math.BigInteger;
import java.util.List;

public interface MetadataApi {

    @GET("txs/{hash}/metadata")
    Call<List<MetadataJSONContent>> getJSONMetadataByTxnHash(@Header("project_id")  String projectId, @Path("hash") String hash);

    @GET("txs/{hash}/metadata/cbor")
    Call<List<MetadataCBORContent>> getCBORMetadataByTxnHash(@Header("project_id")  String projectId, @Path("hash") String hash);

    @GET("metadata/txs/labels")
    Call<List<MetadataLabel>> getMetadataLabels(@Header("project_id")  String projectId,
                                                @Query("count") int count, @Query("page") int page, @Query("order") String order);

    @GET("metadata/txs/labels/{label}")
    Call<List<MetadataJSONContent>> getJSONMetadataByLabel(@Header("project_id")  String projectId, @Path("label") BigInteger label,
                                                       @Query("count") int count, @Query("page") int page, @Query("order") String order);

    @GET("metadata/txs/labels/{label}/cbor")
    Call<List<MetadataCBORContent>> getCBORMetadatabyLabel(@Header("project_id")  String projectId, @Path("label") BigInteger label,
                                                           @Query("count") int count, @Query("page") int page, @Query("order") String order);

}
