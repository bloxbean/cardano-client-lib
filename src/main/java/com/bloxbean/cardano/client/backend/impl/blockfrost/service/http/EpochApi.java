package com.bloxbean.cardano.client.backend.impl.blockfrost.service.http;

import com.bloxbean.cardano.client.backend.model.EpochContent;
import com.bloxbean.cardano.client.backend.model.ProtocolParams;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;

public interface EpochApi {

    @GET("epochs/latest")
    Call<EpochContent> getLatestEpoch(@Header("project_id")  String projectId);

    @GET("epochs/{number}")
    Call<EpochContent> getEpochByNumber(@Header("project_id")  String projectId, @Path("number") Integer number);

    @GET("epochs/{number}/parameters")
    Call<ProtocolParams> getProtocolParameters(@Header("project_id")  String projectId, @Path("number") Integer number);

}
