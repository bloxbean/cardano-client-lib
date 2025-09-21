package com.bloxbean.cardano.client.quicktx.intent;

import co.nstant.in.cbor.model.Array;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.function.TxBuilder;
import com.bloxbean.cardano.client.quicktx.IntentContext;
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

    // Field for JSON deserialization support
    @JsonProperty("script_hex")
    private String scriptHex;

    @Override
    public String getType() {
        return "native_script";
    }

    @JsonProperty("script_hex")
    public String getScriptHex() {
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
        if (script == null) {
            throw new IllegalStateException("NativeScriptAttachment requires script");
        }
    }

    @Override
    public TxIntent resolveVariables(java.util.Map<String, Object> variables) {
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
    public TxBuilder preApply(IntentContext ic) {
        return (ctx, txn) -> validate();
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

    // Factory helper
    public static NativeScriptAttachmentIntent of(NativeScript script) {
        return NativeScriptAttachmentIntent.builder()
                .script(script)
                .build();
    }
}

