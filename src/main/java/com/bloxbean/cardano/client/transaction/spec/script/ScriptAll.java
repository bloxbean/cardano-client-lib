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
        Array array = new Array();
        array.add(new UnsignedInteger(1));

        Array scriptsArray = new Array();
        for (NativeScript script : scripts) {
            scriptsArray.add(script.serializeAsDataItem());
        }

        array.add(scriptsArray);
        return array;
    }

    public static ScriptAll deserialize(Array array) throws CborDeserializationException {
        ScriptAll scriptAll = new ScriptAll();
        Array scriptsDIArray = (Array) (array.getDataItems().get(1));
        for (DataItem scriptDI : scriptsDIArray.getDataItems()) {
            Array scriptArray = (Array) scriptDI;
            NativeScript nativeScript = NativeScript.deserialize(scriptArray);
            if (nativeScript != null)
                scriptAll.addScript(nativeScript);
        }
        return scriptAll;
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
}
