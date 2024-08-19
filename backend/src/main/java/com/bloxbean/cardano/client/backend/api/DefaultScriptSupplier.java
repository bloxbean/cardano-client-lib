package com.bloxbean.cardano.client.backend.api;

import com.bloxbean.cardano.client.api.ScriptSupplier;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.exception.ApiRuntimeException;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class DefaultScriptSupplier implements ScriptSupplier {
    private final static Logger log = LoggerFactory.getLogger(DefaultScriptSupplier.class);

    private ScriptService scriptService;

    public DefaultScriptSupplier(ScriptService scriptService) {
        this.scriptService = scriptService;
    }

    @Override
    public Optional<PlutusScript> getScript(String scriptHash) {
        try {
            var result = scriptService.getPlutusScript(scriptHash);
            if (result.isSuccessful())
                return Optional.of(result.getValue());
            else {
                log.debug("Error fetching script for hash: {}, {}", scriptHash, result.getResponse());
                return Optional.empty();
            }
        } catch (ApiException e) {
            log.debug("Error fetching script for hash: {}", scriptHash, e);
            throw new ApiRuntimeException(e);
        }
    }
}
