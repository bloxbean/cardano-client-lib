package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.model.ScriptDatum;
import com.bloxbean.cardano.client.backend.model.ScriptDatumCbor;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.plutus.util.PlutusUtil;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;

public interface ScriptService {

    /**
     * Datum value
     * Query JSON value of a datum by its hash.
     *
     * @param datumHash Hash of the datum. (required)
     * @return ScriptDatum
     */
    Result<ScriptDatum> getScriptDatum(String datumHash) throws ApiException;

    /**
     * Datum value CBOR
     * Query CBOR value of a datum by its hash.
     *
     * @param datumHash Hash of the datum. (required)
     * @return ScriptDatumCbor
     */
    Result<ScriptDatumCbor> getScriptDatumCbor(String datumHash) throws ApiException;

    /**
     * Get native script json by script hash
     *
     * @param scriptHash Script hash
     * @return JsonNode
     */
    Result<JsonNode> getNativeScriptJson(String scriptHash) throws ApiException;

    /**
     * Get plutus script CBOR by script hash
     *
     * @param scriptHash Script hash
     * @return CBOR in hex string
     */
    Result<String> getPlutusScriptCbor(String scriptHash) throws ApiException;

    /**
     * Get NativeScript by script hash
     *
     * @param scriptHash script hash
     * @return NativeScript
     * @throws ApiException
     */
    default Result<NativeScript> getNativeScript(String scriptHash) throws ApiException {
        Result<JsonNode> contentResult = getNativeScriptJson(scriptHash);
        if (!contentResult.isSuccessful())
            return Result.error(contentResult.getResponse()).code(contentResult.code());

        try {
            NativeScript script = NativeScript.deserialize(contentResult.getValue());
            return Result.success(JsonUtil.getPrettyJson(script)).withValue(script);
        } catch (CborDeserializationException e) {
            throw new ApiException("Error deserializing native script content " + contentResult.getValue(), e);
        }
    }

    /**
     * Get PlutusScript by script hash
     *
     * @param scriptHash script hash
     * @return PlutusScript
     * @throws ApiException
     */
    default Result<PlutusScript> getPlutusScript(String scriptHash) throws ApiException {
        Result<String> cborResult = getPlutusScriptCbor(scriptHash);
        if (!cborResult.isSuccessful())
            return Result.error(cborResult.getResponse()).code(cborResult.code());

        try {
            return PlutusUtil.getPlutusScript(scriptHash, cborResult.getValue())
                    .map(script -> Result.success(JsonUtil.getPrettyJson(script))
                            .withValue(script))
                    .orElse(Result.error("Error getting plutus script for hash: " + scriptHash));
        } catch (Exception e) {
            throw new ApiException("Error getting plutus script" + cborResult.getValue(), e);
        }
    }
}
