package com.bloxbean.cardano.client.backend.blockfrost.service.http;

import com.bloxbean.cardano.client.backend.model.ScriptDatum;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Path;

public interface ScriptApi {


  /**
   * Datum value
   * Query JSON value of a datum by its hash
   *
   * @param datumHash Hash of the datum. (required)
   * @return Call&lt;ScriptDatum&gt;
   */
  @GET("scripts/datum/{datum_hash}")
  Call<ScriptDatum> getDatumValue(
      @Header("project_id") String projectId,
      @Path("datum_hash") String datumHash
  );
}
