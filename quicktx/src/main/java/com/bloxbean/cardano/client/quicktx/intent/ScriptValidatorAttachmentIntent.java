package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.plutus.blueprint.model.PlutusVersion;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.bloxbean.cardano.client.quicktx.IntentContext;
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

    // For completeness (not used in apply logic but useful for YAML semantics)
    @JsonProperty("role")
    private RedeemerTag role;

    // Serialization fields
    @JsonProperty("cbor_hex")
    private String scriptHex;

    // 1=V1, 2=V2, 3=V3
    @JsonProperty("version")
    private PlutusVersion scriptVersion;

    @Override
    public String getType() {
        return "validator";
    }

    @JsonProperty("cbor_hex")
    public String getScriptHex() {
        if (script != null) {
            try { return script.getCborHex(); } catch (Exception e) {}
        }
        return scriptHex;
    }

    @JsonProperty("version")
    public PlutusVersion getScriptVersion() {
        if (script != null) {
            if (script instanceof PlutusV1Script) return PlutusVersion.v1;
            if (script instanceof PlutusV2Script) return PlutusVersion.v2;
            if (script instanceof PlutusV3Script) return PlutusVersion.v3;
        }
        return scriptVersion;
    }

    @Override
    public void validate() {
        if (script == null && (scriptHex == null || scriptHex.isEmpty() || scriptVersion == null)) {
            throw new IllegalStateException("ValidatorAttachment requires script or script_hex + script_version");
        }
    }
    @Override
    public TxIntent resolveVariables(java.util.Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return this;
        }

        String resolvedScriptHex = VariableResolver.resolve(scriptHex, variables);

        // Check if any variables were resolved
        if (!java.util.Objects.equals(resolvedScriptHex, scriptHex)) {
            return this.toBuilder()
                .scriptHex(resolvedScriptHex)
                .build();
        }

        return this;
    }

    @Override
    public TxBuilder preApply(IntentContext ic) {
        return (ctx, txn) -> validate();
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

    // Factory helper
    public static ScriptValidatorAttachmentIntent of(com.bloxbean.cardano.client.plutus.spec.RedeemerTag role, PlutusScript script) {
        return ScriptValidatorAttachmentIntent.builder()
                .role(role)
                .script(script)
                .build();
    }
}
