package com.bloxbean.cardano.client.transaction.spec.script;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * This script class is for "RequireAllOf" expression
 */
@Data
@NoArgsConstructor
public class ScriptAll implements NativeScript {

    private final ScriptType type = ScriptType.all;
    private final List<NativeScript> scripts = new ArrayList<>();

    public ScriptAll addScript(NativeScript script) {
        scripts.add(script);
        return this;
    }

    //script_all = (1, [ * native_script ])
    @Override
    public DataItem serializeAsDataItem() throws CborSerializationException {
        return NativeScriptCodec.encode(this);
    }

    public static ScriptAll deserialize(Array array) throws CborDeserializationException {
        return NativeScriptCodec.decode(array, ScriptAll.class);
    }

    public static ScriptAll deserialize(JsonNode jsonNode) throws CborDeserializationException {
        ScriptAll scriptAll = new ScriptAll();
        ArrayNode scriptsNode = (ArrayNode) jsonNode.get("scripts");
        for (JsonNode scriptNode : scriptsNode) {
            NativeScript nativeScript = NativeScript.deserialize(scriptNode);
            scriptAll.addScript(nativeScript);
        }
        return scriptAll;
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
