package com.bloxbean.cardano.client.transaction.spec.script;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.TransactionDeserializationException;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * This script class is for "RequireAny" expression
 */
@Data
public class ScriptAny implements NativeScript {
    private final static Logger LOG = LoggerFactory.getLogger(ScriptAny.class);

    private ScriptType type;
    private List<NativeScript> scripts;

    public ScriptAny() {
        this.type = ScriptType.any;
        this.scripts = new ArrayList<>();
    }

    public ScriptAny addScript(NativeScript script) {
        scripts.add(script);

        return  this;
    }

    //script_any = (2, [ * native_script ])
    @Override
    public DataItem serializeAsDataItem() throws CborException {
        Array array = new Array();
        array.add(new UnsignedInteger(2));

        Array scriptsArray = new Array();
        for(NativeScript script: scripts) {
            scriptsArray.add(script.serializeAsDataItem());
        }

        array.add(scriptsArray);
        return array;
    }

    public static ScriptAny deserialize(Array array) throws TransactionDeserializationException {
        ScriptAny scriptAny = new ScriptAny();
        Array scriptsDIArray = (Array) (array.getDataItems().get(1));
        for(DataItem scriptDI: scriptsDIArray.getDataItems()) {
            Array scriptArray = (Array)scriptDI;
            NativeScript nativeScript = NativeScript.deserialize(scriptArray);
            if(nativeScript != null)
                scriptAny.addScript(nativeScript);
        }

        return scriptAny;
    }
}
