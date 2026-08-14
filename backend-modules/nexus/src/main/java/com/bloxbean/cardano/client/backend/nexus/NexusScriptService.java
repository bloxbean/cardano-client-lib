package com.bloxbean.cardano.client.backend.nexus;

import adlabs.nexus.client.util.Network;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.ScriptService;
import com.bloxbean.cardano.client.backend.model.ScriptDatum;
import com.bloxbean.cardano.client.backend.model.ScriptDatumCbor;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Nexus Script Service. getNativeScript/getPlutusScript are inherited default methods
 * built on top of getNativeScriptJson/getPlutusScriptCbor.
 */
public class NexusScriptService implements ScriptService {

    private final adlabs.nexus.client.backend.api.script.ScriptService scriptService;
    private final Network network;

    public NexusScriptService(adlabs.nexus.client.backend.api.script.ScriptService scriptService, Network network) {
        this.scriptService = scriptService;
        this.network = network;
    }

    @Override
    public Result<ScriptDatum> getScriptDatum(String datumHash) throws ApiException {
        try {
            return NexusResultMapper.map(scriptService.getDatumByHash(network, datumHash),
                    datum -> {
                        ScriptDatum scriptDatum = new ScriptDatum();
                        scriptDatum.setJsonValue(datum.getJson());
                        return scriptDatum;
                    });
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<ScriptDatumCbor> getScriptDatumCbor(String datumHash) throws ApiException {
        try {
            return NexusResultMapper.map(scriptService.getDatumByHash(network, datumHash),
                    datum -> {
                        ScriptDatumCbor scriptDatumCbor = new ScriptDatumCbor();
                        scriptDatumCbor.setCbor(datum.getCbor());
                        return scriptDatumCbor;
                    });
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<JsonNode> getNativeScriptJson(String scriptHash) throws ApiException {
        try {
            return NexusResultMapper.map(scriptService.getScriptByHash(network, scriptHash),
                    adlabs.nexus.client.backend.api.script.model.ScriptDetail::getJson);
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    @Override
    public Result<String> getPlutusScriptCbor(String scriptHash) throws ApiException {
        try {
            return NexusResultMapper.map(scriptService.getScriptByHash(network, scriptHash),
                    adlabs.nexus.client.backend.api.script.model.ScriptDetail::getCbor);
        } catch (adlabs.nexus.client.backend.api.base.exception.ApiException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }
}
