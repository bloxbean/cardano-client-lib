package com.bloxbean.cardano.client.api;

import com.bloxbean.cardano.client.api.model.ResultWrapper;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;

/**
 * Implement this interface to provide PlutusScript
 */
@FunctionalInterface
public interface ScriptSupplier {
    ResultWrapper<PlutusScript> getScript(String scriptHash);
}
