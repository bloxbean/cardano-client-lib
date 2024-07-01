package com.bloxbean.cardano.client.api;

import com.bloxbean.cardano.client.plutus.spec.PlutusScript;

/**
 * Implement this interface to provide PlutusScript
 */
@FunctionalInterface
public interface ScriptSupplier {
    PlutusScript getScript(String scriptHash);
}
