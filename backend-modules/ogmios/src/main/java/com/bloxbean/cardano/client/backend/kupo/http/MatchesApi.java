package com.bloxbean.cardano.client.backend.kupo.http;

import com.bloxbean.cardano.client.backend.kupo.model.KupoUtxo;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

import java.util.List;

public interface MatchesApi {

    @GET("v1/matches/{pattern}?unspent")
    Call<List<KupoUtxo>> getUnspentMatches(@Path("pattern") String pattern);
}
