package com.bloxbean.cardano.client.transaction.spec.script;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.SimpleValue;
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
 * This script class is for "RequireAny" expression
 */
@Data
@NoArgsConstructor
public class ScriptAny implements NativeScript {

    private final ScriptType type = ScriptType.any;
    private final List<NativeScript> scripts = new ArrayList<>();

    public ScriptAny addScript(NativeScript script) {
        scripts.add(script);
        return this;
    }

    //script_any = (2, [ * native_script ])
    @Override
    public DataItem serializeAsDataItem() throws CborSerializationException {
        Array array = new Array();
        array.add(new UnsignedInteger(2));

        Array scriptsArray = new Array();
        for (NativeScript script : scripts) {
            scriptsArray.add(script.serializeAsDataItem());
        }

        array.add(scriptsArray);
        return array;
    }

    public static ScriptAny deserialize(Array array) throws CborDeserializationException {
        ScriptAny scriptAny = new ScriptAny();
        Array scriptsDIArray = (Array) (array.getDataItems().get(1));
        for (DataItem scriptDI : scriptsDIArray.getDataItems()) {
            if (scriptDI == SimpleValue.BREAK) continue;
            Array scriptArray = (Array) scriptDI;
            NativeScript nativeScript = NativeScript.deserialize(scriptArray);
            if (nativeScript != null)
                scriptAny.addScript(nativeScript);
        }
        return scriptAny;
    }

    public static ScriptAny deserialize(JsonNode jsonNode) throws CborDeserializationException {
        ScriptAny scriptAny = new ScriptAny();

        ArrayNode scriptsNode = (ArrayNode) jsonNode.get("scripts");
        for (JsonNode scriptNode : scriptsNode) {
            NativeScript nativeScript = NativeScript.deserialize(scriptNode);
            scriptAny.addScript(nativeScript);
        }
        return scriptAny;
    }
}
