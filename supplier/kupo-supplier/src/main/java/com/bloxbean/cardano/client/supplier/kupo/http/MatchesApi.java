package com.bloxbean.cardano.client.supplier.kupo.http;

import com.bloxbean.cardano.client.supplier.kupo.model.KupoDatum;
import com.bloxbean.cardano.client.supplier.kupo.model.KupoUtxo;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

import java.util.List;

public interface MatchesApi {

    @GET("v1/matches/{pattern}?unspent")
    Call<List<KupoUtxo>> getUnspentMatches(@Path("pattern") String pattern);

    @GET("v1/matches/{pattern}")
    Call<List<KupoUtxo>> getMatches(@Path("pattern") String pattern);

    @GET("v1/datums/{datumHash}")
    Call<KupoDatum> getDatum(@Path("datumHash") String datumHash);
}
