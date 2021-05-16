package com.bloxbean.cardano.client.backend.impl.blockfrost.service.http;

import com.bloxbean.cardano.client.backend.model.Genesis;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;

public interface CardanoLedgerApi {
    @GET("genesis")
    Call<Genesis> genesis(@Header("project_id") String projectId);
}
