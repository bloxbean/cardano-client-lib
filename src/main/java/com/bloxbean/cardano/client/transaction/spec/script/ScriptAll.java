package com.bloxbean.cardano.client.transaction.spec.script;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * This script class is for "RequireAllOf" expression
 */
@Data
public class ScriptAll implements NativeScript {
    private final static Logger LOG = LoggerFactory.getLogger(ScriptAll.class);

    private ScriptType type;
    private List<NativeScript> scripts;

    public ScriptAll() {
        this.type = ScriptType.all;
        this.scripts = new ArrayList<>();
    }

    public ScriptAll addScript(NativeScript script) {
        scripts.add(script);

        return  this;
    }

    //script_all = (1, [ * native_script ])
    @Override
    public DataItem serializeAsDataItem() throws CborException {
        Array array = new Array();
        array.add(new UnsignedInteger(1));

        Array scriptsArray = new Array();
        for(NativeScript script: scripts) {
            scriptsArray.add(script.serializeAsDataItem());
        }

        array.add(scriptsArray);
        return array;
    }
}
