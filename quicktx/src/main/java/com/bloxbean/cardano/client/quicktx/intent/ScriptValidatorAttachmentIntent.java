package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.quicktx.ScriptRef;
import com.bloxbean.cardano.client.quicktx.serialization.VariableResolver;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Intention for attaching a validator script to the transaction witness set.
 * Captures the various ScriptTx.attach*Validator(...) calls.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScriptValidatorAttachmentIntent implements TxScriptAttachmentIntent {

    // Runtime field
    @JsonIgnore
    private PlutusScript script;

    @JsonIgnore
    private boolean scriptReferenceResolved;

    @JsonProperty("role")
    private RedeemerTag role;

    @JsonProperty("cbor_hex")
    private String scriptHex;

    @JsonProperty("script_ref")
    private String scriptRef;

    @JsonProperty("script_hash")
    private String scriptHash;

    // 1=V1, 2=V2, 3=V3
    @JsonProperty("version")
    private PlutusVersion scriptVersion;

    @Override
    public String getType() {
        return "validator";
    }

    @JsonProperty("cbor_hex")
    public String getScriptHex() {
        if (hasScriptReference()) {
            return null;
        }
        if (script != null) {
            try { return script.getCborHex(); } catch (Exception e) {}
        }
        return scriptHex;
    }

    @JsonProperty("version")
    public PlutusVersion getScriptVersion() {
        if (hasScriptReference()) {
            return null;
        }
        if (script != null) {
            if (script instanceof PlutusV1Script) return PlutusVersion.v1;
            if (script instanceof PlutusV2Script) return PlutusVersion.v2;
            if (script instanceof PlutusV3Script) return PlutusVersion.v3;
        }
        return scriptVersion;
    }

    @Override
    public void validate() {
        if (scriptRef != null && scriptRef.isBlank()) {
            throw new IllegalStateException("ValidatorAttachment script_ref cannot be blank");
        }
        if (scriptHash != null && scriptHash.isBlank()) {
            throw new IllegalStateException("ValidatorAttachment script_hash cannot be blank");
        }

        boolean hasScriptRef = hasScriptRef();
        boolean hasScriptHash = hasScriptHash();
        boolean hasScriptReference = hasScriptRef || hasScriptHash;
        boolean hasRuntimeScript = script != null;
        boolean hasScriptHex = hasScriptHexField();
        boolean hasScriptVersion = scriptVersion != null;

        if (hasScriptRef && hasScriptHash) {
            throw new IllegalStateException("ValidatorAttachment requires only one of script_ref or script_hash");
        }
        if (hasScriptReference && (hasScriptHex || hasScriptVersion)) {
            throw new IllegalStateException("ValidatorAttachment script_ref/script_hash cannot be combined with cbor_hex or version");
        }
        if (hasScriptReference && hasRuntimeScript && !scriptReferenceResolved) {
            throw new IllegalStateException("ValidatorAttachment script_ref/script_hash cannot be combined with a runtime script");
        }
        if (!hasScriptReference && (hasScriptHex != hasScriptVersion)) {
            throw new IllegalStateException("ValidatorAttachment requires cbor_hex and version together");
        }
        if (!hasScriptReference && !hasRuntimeScript && !(hasScriptHex && hasScriptVersion)) {
            throw new IllegalStateException("ValidatorAttachment requires script, cbor_hex + version, script_ref, or script_hash");
        }
    }

    @Override
    public TxIntent resolveVariables(java.util.Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return this;
        }

        String resolvedScriptHex = VariableResolver.resolve(scriptHex, variables);
        String resolvedScriptRef = VariableResolver.resolve(scriptRef, variables);
        String resolvedScriptHash = VariableResolver.resolve(scriptHash, variables);

        // Check if any variables were resolved
        if (!java.util.Objects.equals(resolvedScriptHex, scriptHex) ||
                !java.util.Objects.equals(resolvedScriptRef, scriptRef) ||
                !java.util.Objects.equals(resolvedScriptHash, scriptHash)) {
            return this.toBuilder()
                .scriptHex(resolvedScriptHex)
                .scriptRef(resolvedScriptRef)
                .scriptHash(resolvedScriptHash)
                .build();
        }

        return this;
    }
    
    @Override
    public TxBuilder apply(IntentContext ic) {
        return (ctx, txn) -> {
            try {
                PlutusScript s = resolveScript();
                if (txn.getWitnessSet() == null) txn.setWitnessSet(new TransactionWitnessSet());
                if (s instanceof PlutusV1Script) {
                    if (!txn.getWitnessSet().getPlutusV1Scripts().contains(s))
                        txn.getWitnessSet().getPlutusV1Scripts().add((PlutusV1Script) s);
                } else if (s instanceof PlutusV2Script) {
                    if (!txn.getWitnessSet().getPlutusV2Scripts().contains(s))
                        txn.getWitnessSet().getPlutusV2Scripts().add((PlutusV2Script) s);
                } else if (s instanceof PlutusV3Script) {
                    if (!txn.getWitnessSet().getPlutusV3Scripts().contains(s))
                        txn.getWitnessSet().getPlutusV3Scripts().add((PlutusV3Script) s);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to attach validator", e);
            }
        };
    }

    private PlutusScript resolveScript() {
        if (script != null) return script;
        if (scriptVersion == PlutusVersion.v1)
            return PlutusV1Script.builder()
                .cborHex(scriptHex)
                .build();
        if (scriptVersion == PlutusVersion.v2)
            return PlutusV2Script.builder()
                .cborHex(scriptHex)
                .build();
        if (scriptVersion == PlutusVersion.v3)
            return PlutusV3Script.builder()
                .cborHex(scriptHex)
                .build();

        throw new IllegalStateException("Invalid script version: " + scriptVersion);
    }

    @JsonIgnore
    public boolean hasScriptRef() {
        return scriptRef != null && !scriptRef.isBlank();
    }

    @JsonIgnore
    public boolean hasScriptHash() {
        return scriptHash != null && !scriptHash.isBlank();
    }

    @JsonIgnore
    public boolean hasScriptReference() {
        return hasScriptRef() || hasScriptHash();
    }

    @JsonIgnore
    public boolean hasScriptHexField() {
        return scriptHex != null && !scriptHex.isBlank();
    }

    @JsonIgnore
    public void resolveScript(PlutusScript script) {
        if (script == null) {
            throw new IllegalArgumentException("script cannot be null");
        }
        this.script = script;
        this.scriptReferenceResolved = true;
    }

    // Factory helper
    public static ScriptValidatorAttachmentIntent of(com.bloxbean.cardano.client.plutus.spec.RedeemerTag role, PlutusScript script) {
        return ScriptValidatorAttachmentIntent.builder()
                .role(role)
                .script(script)
                .build();
    }

    public static ScriptValidatorAttachmentIntent of(com.bloxbean.cardano.client.plutus.spec.RedeemerTag role, ScriptRef scriptRef) {
        if (scriptRef == null) {
            throw new TxBuildException("scriptRef cannot be null");
        }

        ScriptValidatorAttachmentIntentBuilder builder = ScriptValidatorAttachmentIntent.builder()
                .role(role);

        if (scriptRef.getRef() != null) {
            builder.scriptRef(scriptRef.getRef());
        } else {
            builder.scriptHash(scriptRef.getHash());
        }

        return builder.build();
    }
}
