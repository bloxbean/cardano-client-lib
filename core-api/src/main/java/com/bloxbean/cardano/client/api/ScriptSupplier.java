package com.bloxbean.cardano.client.api;

import com.bloxbean.cardano.client.plutus.spec.PlutusScript;

import java.util.Optional;

/**
 * Implement this interface to provide PlutusScript
 */
@FunctionalInterface
public interface ScriptSupplier {
    Optional<PlutusScript> getScript(String scriptHash);
}
