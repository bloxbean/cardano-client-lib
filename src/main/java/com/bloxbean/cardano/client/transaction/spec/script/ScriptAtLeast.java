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
 * This script class is for “RequireMOf” expression
 */
@Data
public class ScriptAtLeast implements NativeScript {
    private final static Logger LOG = LoggerFactory.getLogger(ScriptAtLeast.class);

    private ScriptType type;
    private int required;
    private List<NativeScript> scripts;

    public ScriptAtLeast(int required) {
        this.type = ScriptType.atLeast;
        this.required = required;
        this.scripts = new ArrayList<>();
    }

    public ScriptAtLeast required(int required) {
        this.required = required;
        return this;
    }

    public ScriptAtLeast addScript(NativeScript script) {
        scripts.add(script);
        return  this;
    }


    //script_n_of_k = (3, n: uint, [ * native_script ])
    @Override
    public DataItem serializeAsDataItem() throws CborException {
        Array array = new Array();
        array.add(new UnsignedInteger(3));
        array.add(new UnsignedInteger(required));

        Array scriptsArray = new Array();
        for(NativeScript script: scripts) {
            scriptsArray.add(script.serializeAsDataItem());
        }

        array.add(scriptsArray);
        return array;
    }
}
