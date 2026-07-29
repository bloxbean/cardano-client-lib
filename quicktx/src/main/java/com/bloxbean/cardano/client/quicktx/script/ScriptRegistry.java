package com.bloxbean.cardano.client.quicktx.script;

import com.bloxbean.cardano.client.spec.Script;

import java.util.Optional;

/**
 * Registry for resolving runtime-held script material by logical reference or script hash.
 */
public interface ScriptRegistry {

    /**
     * Resolve a script by a logical runtime reference.
     *
     * @param ref logical script reference
     * @return resolved script, if available
     */
    Optional<Script> resolve(String ref);

    /**
     * Resolve a script by its script hash.
     *
     * @param scriptHash script hash in hex
     * @return resolved script, if available
     */
    default Optional<Script> resolveByHash(String scriptHash) {
        return Optional.empty();
    }
}
