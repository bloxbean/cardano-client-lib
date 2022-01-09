package com.bloxbean.cardano.client.transaction.spec.script;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * This script class is for "RequireOf" expression
 */
@Data
@NoArgsConstructor
public class ScriptAtLeast implements NativeScript {

    private final ScriptType type = ScriptType.atLeast;
    private int required;
    private final List<NativeScript> scripts = new ArrayList<>();

    public ScriptAtLeast(int required) {
        this.required = required;
    }

    public ScriptAtLeast addScript(NativeScript script) {
        scripts.add(script);
        return this;
    }

    //script_n_of_k = (3, n: uint, [ * native_script ])
    @Override
    public DataItem serializeAsDataItem() throws CborSerializationException {
        Array array = new Array();
        array.add(new UnsignedInteger(3));
        array.add(new UnsignedInteger(required));

        Array scriptsArray = new Array();
        for (NativeScript script : scripts) {
            scriptsArray.add(script.serializeAsDataItem());
        }

        array.add(scriptsArray);
        return array;
    }

    public static ScriptAtLeast deserialize(Array array) throws CborDeserializationException {
        int required = ((UnsignedInteger) (array.getDataItems().get(1))).getValue().intValue();
        ScriptAtLeast scriptAtLeast = new ScriptAtLeast(required);
        Array scriptsDIArray = (Array) (array.getDataItems().get(2));
        for (DataItem scriptDI : scriptsDIArray.getDataItems()) {
            Array scriptArray = (Array) scriptDI;
            NativeScript nativeScript = NativeScript.deserialize(scriptArray);
            if (nativeScript != null)
                scriptAtLeast.addScript(nativeScript);
        }

        return scriptAtLeast;
    }

    public static ScriptAtLeast deserialize(JsonNode jsonNode) throws CborDeserializationException {
        int required = jsonNode.get("required").asInt();
        ScriptAtLeast scriptAtLeast = new ScriptAtLeast(required);

        ArrayNode scriptsNode = (ArrayNode) jsonNode.get("scripts");
        for (JsonNode scriptNode : scriptsNode) {
            NativeScript nativeScript = NativeScript.deserialize(scriptNode);
            scriptAtLeast.addScript(nativeScript);
        }
        return scriptAtLeast;
    }
}
