package com.bloxbean.cardano.client.transaction.spec.script;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * This script class is for "RequireOf" expression
 */
@Data
@NoArgsConstructor
public class ScriptAtLeast implements NativeScript {

    private final ScriptType type = ScriptType.atLeast;
    private BigInteger required;
    private final List<NativeScript> scripts = new ArrayList<>();

    public ScriptAtLeast(int required) {
        this.required = BigInteger.valueOf(required);
    }

    public ScriptAtLeast(long required) {
        this.required = BigInteger.valueOf(required);
    }

    public ScriptAtLeast(BigInteger required) {
        this.required = required;
    }

    public ScriptAtLeast addScript(NativeScript script) {
        scripts.add(script);
        return this;
    }

    //Till Babbage: script_n_of_k = (3, n: uint, [ * native_script ])
    //Conway: script_n_of_k = (3, int64, [* native_script])
    @Override
    public DataItem serializeAsDataItem() throws CborSerializationException {
        return NativeScriptCodec.encode(this);
    }

    public static ScriptAtLeast deserialize(Array array) throws CborDeserializationException {
        return NativeScriptCodec.decode(array, ScriptAtLeast.class);
    }

    public static ScriptAtLeast deserialize(JsonNode jsonNode) throws CborDeserializationException {
        String required = jsonNode.get("required").asText();
        ScriptAtLeast scriptAtLeast = new ScriptAtLeast(new BigInteger(required));

        ArrayNode scriptsNode = (ArrayNode) jsonNode.get("scripts");
        for (JsonNode scriptNode : scriptsNode) {
            NativeScript nativeScript = NativeScript.deserialize(scriptNode);
            scriptAtLeast.addScript(nativeScript);
        }
        return scriptAtLeast;
    }

    /**
     * Value equality, computed without recursion so that scripts of any nesting depth can be compared.
     */
    @Override
    public boolean equals(Object o) {
        return NativeScriptCodec.valueEquals(this, o);
    }

    @Override
    public int hashCode() {
        return NativeScriptCodec.hash(this);
    }

    @Override
    public String toString() {
        return NativeScriptCodec.toString(this);
    }
}
