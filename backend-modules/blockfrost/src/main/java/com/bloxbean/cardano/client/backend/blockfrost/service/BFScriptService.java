package com.bloxbean.cardano.client.backend.blockfrost.service;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.ScriptService;
import com.bloxbean.cardano.client.backend.blockfrost.service.http.ScriptApi;
import com.bloxbean.cardano.client.backend.model.ScriptDatum;
import com.bloxbean.cardano.client.backend.model.ScriptDatumCbor;
import java.io.IOException;
import retrofit2.Call;
import retrofit2.Response;

public class BFScriptService extends BFBaseService implements ScriptService {

  private ScriptApi scriptApi;

  public BFScriptService(String baseUrl, String projectId) {
    super(baseUrl, projectId);
    this.scriptApi = getRetrofit().create(ScriptApi.class);
  }

  @Override
  public Result<ScriptDatum> getScriptDatum(String datumHash) throws ApiException {
    Call<ScriptDatum> call = scriptApi.getDatumValue(getProjectId(), datumHash);
    try {
      Response<ScriptDatum> response = call.execute();
      return processResponse(response);
    } catch (IOException e) {
      throw new ApiException("Exception while fetching script datum for hash: " + datumHash, e);
    }
  }

  @Override
  public Result<ScriptDatumCbor> getScriptDatumCbor(String datumHash) throws ApiException {
    Call<ScriptDatumCbor> call = scriptApi.getDatumValueCbor(getProjectId(), datumHash);
    try {
      Response<ScriptDatumCbor> response = call.execute();
      return processResponse(response);
    } catch (IOException e) {
      throw new ApiException("Exception while fetching script datum cbor for hash: " + datumHash, e);
    }
  }
}
