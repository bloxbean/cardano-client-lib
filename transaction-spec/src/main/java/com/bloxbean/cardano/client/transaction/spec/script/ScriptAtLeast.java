package com.bloxbean.cardano.client.transaction.spec.script;

import co.nstant.in.cbor.model.*;
import co.nstant.in.cbor.model.Number;
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
        Array array = new Array();
        array.add(new UnsignedInteger(3));

        if (required == null)
            required = BigInteger.ZERO;

        if (required.signum() >= 0) {
            array.add(new UnsignedInteger(required));
        } else {
            array.add(new NegativeInteger(required));
        }

        Array scriptsArray = new Array();
        for (NativeScript script : scripts) {
            scriptsArray.add(script.serializeAsDataItem());
        }

        array.add(scriptsArray);
        return array;
    }

    public static ScriptAtLeast deserialize(Array array) throws CborDeserializationException {
        BigInteger required = ((Number) (array.getDataItems().get(1))).getValue();
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
        String required = jsonNode.get("required").asText();
        ScriptAtLeast scriptAtLeast = new ScriptAtLeast(new BigInteger(required));

        ArrayNode scriptsNode = (ArrayNode) jsonNode.get("scripts");
        for (JsonNode scriptNode : scriptsNode) {
            NativeScript nativeScript = NativeScript.deserialize(scriptNode);
            scriptAtLeast.addScript(nativeScript);
        }
        return scriptAtLeast;
    }
}
