package com.bloxbean.cardano.client.backend.blockfrost.service;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.ScriptService;
import com.bloxbean.cardano.client.backend.blockfrost.service.http.ScriptApi;
import com.bloxbean.cardano.client.backend.model.ScriptDatum;
import com.bloxbean.cardano.client.backend.model.ScriptDatumCbor;
import com.fasterxml.jackson.databind.JsonNode;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;

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

    @Override
    public Result<JsonNode> getNativeScriptJson(String scriptHash) throws ApiException {
        Call<JsonNode> call = scriptApi.getScriptJson(getProjectId(), scriptHash);
        try {
            Response<JsonNode> response = call.execute();
            Result<JsonNode> result = processResponse(response);
            if (result.isSuccessful() && result.getValue() != null) {
                JsonNode rootNode = result.getValue();
                JsonNode jsonNode = rootNode.get("json");
                String jsonNodeStr = jsonNode != null ? jsonNode.toString() : "";

                return Result.success(jsonNodeStr).withValue(jsonNode).code(result.code());
            } else {
                return result;
            }
        } catch (IOException e) {
            throw new ApiException("Exception while fetching script for hash: " + scriptHash, e);
        }
    }

    @Override
    public Result<String> getPlutusScriptCbor(String scriptHash) throws ApiException {
        Call<JsonNode> call = scriptApi.getScriptCbor(getProjectId(), scriptHash);
        try {
            Response<JsonNode> response = call.execute();
            Result<JsonNode> result = processResponse(response);
            if (result.isSuccessful() && result.getValue() != null) {
                JsonNode rootNode = result.getValue();
                JsonNode cborNode = rootNode.get("cbor");
                String cbor = cborNode != null ? cborNode.asText() : "";

                return Result.success(cbor).withValue(cbor).code(result.code());
            } else {
                return Result.error("cbor not found").code(result.code());
            }
        } catch (IOException e) {
            throw new ApiException("Exception while fetching script for hash: " + scriptHash, e);
        }
    }
}
