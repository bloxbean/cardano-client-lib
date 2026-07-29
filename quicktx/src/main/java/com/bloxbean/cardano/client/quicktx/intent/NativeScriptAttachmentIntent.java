package com.bloxbean.cardano.client.quicktx.intent;

import co.nstant.in.cbor.model.Array;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.function.exception.TxBuildException;
import com.bloxbean.cardano.client.quicktx.IntentContext;
import com.bloxbean.cardano.client.quicktx.ScriptRef;
import com.bloxbean.cardano.client.quicktx.serialization.VariableResolver;
import com.bloxbean.cardano.client.transaction.spec.TransactionWitnessSet;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Intention for attaching one NativeScript to the transaction witness set.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class NativeScriptAttachmentIntent implements TxScriptAttachmentIntent {

    // Runtime field
    @JsonIgnore
    private NativeScript script;

    @JsonIgnore
    private boolean scriptReferenceResolved;

    @JsonProperty("script_hex")
    private String scriptHex;

    @JsonProperty("script_ref")
    private String scriptRef;

    @JsonProperty("script_hash")
    private String scriptHash;

    @Override
    public String getType() {
        return "native_script";
    }

    @JsonProperty("script_hex")
    public String getScriptHex() {
        if (hasScriptReference()) {
            return null;
        }
        if (script != null) {
            try {
                //Store script body as hex. This is an 2-element array. First element is native script type and body
                return HexUtil.encodeHexString(script.serializeScriptBody());
            } catch (CborSerializationException e) {
                // Log error or handle appropriately
                throw new CborRuntimeException("Error serializing native script", e);
            }
        }
        // Return stored hex if script object not available (e.g., during deserialization)
        return scriptHex;
    }

    @Override
    public void validate() {
        if (scriptRef != null && scriptRef.isBlank()) {
            throw new IllegalStateException("NativeScriptAttachment script_ref cannot be blank");
        }
        if (scriptHash != null && scriptHash.isBlank()) {
            throw new IllegalStateException("NativeScriptAttachment script_hash cannot be blank");
        }

        boolean hasScriptRef = hasScriptRef();
        boolean hasScriptHash = hasScriptHash();
        boolean hasScriptReference = hasScriptRef || hasScriptHash;
        boolean hasRuntimeScript = script != null;
        boolean hasScriptHex = hasScriptHexField();

        if (hasScriptRef && hasScriptHash) {
            throw new IllegalStateException("NativeScriptAttachment requires only one of script_ref or script_hash");
        }
        if (hasScriptReference && hasScriptHex) {
            throw new IllegalStateException("NativeScriptAttachment script_ref/script_hash cannot be combined with script_hex");
        }
        if (hasScriptReference && hasRuntimeScript && !scriptReferenceResolved) {
            throw new IllegalStateException("NativeScriptAttachment script_ref/script_hash cannot be combined with a runtime script");
        }
        if (!hasScriptReference && !hasRuntimeScript && !hasScriptHex) {
            throw new IllegalStateException("NativeScriptAttachment requires script, script_hex, script_ref, or script_hash");
        }
    }

    @Override
    public TxIntent resolveVariables(java.util.Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return this;
        }

        String resolvedScriptRef = VariableResolver.resolve(scriptRef, variables);
        String resolvedScriptHash = VariableResolver.resolve(scriptHash, variables);

        if (!java.util.Objects.equals(resolvedScriptRef, scriptRef) ||
                !java.util.Objects.equals(resolvedScriptHash, scriptHash)) {
            return this.toBuilder()
                    .scriptRef(resolvedScriptRef)
                    .scriptHash(resolvedScriptHash)
                    .build();
        }

        // If script is not set but script_hex is available, deserialize it
        if (script == null && scriptHex != null && !scriptHex.isEmpty()) {
            try {
                // Resolve variable in script_hex if needed (e.g., ${some_variable})
                String resolvedScriptHex = VariableResolver.resolve(scriptHex, variables);

                // Decode hex to bytes of native script body and deserialize to NativeScript
                byte[] scriptBytes = HexUtil.decodeHexString(resolvedScriptHex);
                Array cborArray = (Array) CborSerializationUtil.deserialize(scriptBytes);
                NativeScript deserializedScript = NativeScript.deserialize(cborArray);

                // Return new instance with script object set and scriptHex cleared
                return this.toBuilder()
                    .script(deserializedScript)
                    .scriptHex(null) // Clear the hex since we now have the object
                    .build();

            } catch (Exception e) {
                throw new CborRuntimeException("Failed to deserialize native script from hex: " + scriptHex, e);
            }
        }

        return this;
    }

    @Override
    public TxBuilder apply(IntentContext ic) {
        return (ctx, txn) -> {
            if (script == null) {
                throw new IllegalStateException("NativeScriptAttachment missing runtime script");
            }

            if (txn.getWitnessSet() == null) txn.setWitnessSet(new TransactionWitnessSet());
            var ws = txn.getWitnessSet();
            var nativeList = ws.getNativeScripts();
            if (nativeList == null) {
                nativeList = new java.util.ArrayList<>();
                ws.setNativeScripts(nativeList);
            }

            if (!nativeList.contains(script)) {
                nativeList.add(script);
            }
        };
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
    public void resolveScript(NativeScript script) {
        if (script == null) {
            throw new IllegalArgumentException("script cannot be null");
        }
        this.script = script;
        this.scriptReferenceResolved = true;
    }

    // Factory helper
    public static NativeScriptAttachmentIntent of(NativeScript script) {
        return NativeScriptAttachmentIntent.builder()
                .script(script)
                .build();
    }

    public static NativeScriptAttachmentIntent of(ScriptRef scriptRef) {
        if (scriptRef == null) {
            throw new TxBuildException("scriptRef cannot be null");
        }

        NativeScriptAttachmentIntentBuilder builder = NativeScriptAttachmentIntent.builder();

        if (scriptRef.getRef() != null) {
            builder.scriptRef(scriptRef.getRef());
        } else {
            builder.scriptHash(scriptRef.getHash());
        }

        return builder.build();
    }
}
