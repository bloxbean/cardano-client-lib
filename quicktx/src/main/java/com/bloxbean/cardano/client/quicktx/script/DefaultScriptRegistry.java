package com.bloxbean.cardano.client.quicktx.script;

import com.bloxbean.cardano.client.api.ScriptSupplier;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.plutus.spec.PlutusScript;
import com.bloxbean.cardano.client.spec.Script;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;

/**
 * Default in-memory script registry.
 * <p>
 * Scripts added to this registry are indexed by logical reference and computed
 * script hash. A configured ScriptSupplier is used only as a Plutus script_hash
 * fallback; supplied scripts are hash-verified and cached before being returned.
 */
public class DefaultScriptRegistry implements ScriptRegistry {
    private final Map<String, Script> scriptsByRef = new ConcurrentHashMap<>();
    private final Map<String, Script> scriptsByHash = new ConcurrentHashMap<>();
    private ScriptSupplier scriptSupplier;

    public DefaultScriptRegistry addScript(String ref, Script script) {
        if (ref == null || ref.isBlank())
            throw new TxBuildException("script ref cannot be null or blank");
        if (script == null)
            throw new TxBuildException("script cannot be null");

        String normalizedRef = ref.trim();
        scriptsByRef.put(normalizedRef, script);
        scriptsByHash.put(scriptHash(script), script);
        return this;
    }

    public DefaultScriptRegistry addPlutusScript(String ref, PlutusScript script) {
        return addScript(ref, script);
    }

    public DefaultScriptRegistry addNativeScript(String ref, NativeScript script) {
        return addScript(ref, script);
    }

    public DefaultScriptRegistry withScriptSupplier(ScriptSupplier scriptSupplier) {
        this.scriptSupplier = scriptSupplier;
        return this;
    }

    @Override
    public Optional<Script> resolve(String ref) {
        if (ref == null || ref.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(scriptsByRef.get(ref.trim()));
    }

    @Override
    public Optional<Script> resolveByHash(String scriptHash) {
        String normalizedHash = normalizeHash(scriptHash);
        Script script = scriptsByHash.get(normalizedHash);
        if (script != null)
            return Optional.of(script);

        if (scriptSupplier != null) {
            Optional<PlutusScript> suppliedScript = scriptSupplier.getScript(normalizedHash);
            if (suppliedScript.isPresent()) {
                Script resolvedScript = suppliedScript.get();
                String resolvedHash = scriptHash(resolvedScript);
                if (!normalizedHash.equalsIgnoreCase(resolvedHash)) {
                    throw new TxBuildException("Resolved script hash mismatch. Expected: " + normalizedHash + ", actual: " + resolvedHash);
                }
                scriptsByHash.put(normalizedHash, resolvedScript);
                return Optional.of(resolvedScript);
            }
        }

        return Optional.empty();
    }

    private String scriptHash(Script script) {
        try {
            return normalizeHash(script.getPolicyId());
        } catch (Exception e) {
            throw new TxBuildException("Unable to calculate script hash", e);
        }
    }

    private String normalizeHash(String scriptHash) {
        if (scriptHash == null || scriptHash.isBlank())
            throw new TxBuildException("script hash cannot be null or blank");
        return scriptHash.trim().toLowerCase(Locale.ROOT);
    }
}
