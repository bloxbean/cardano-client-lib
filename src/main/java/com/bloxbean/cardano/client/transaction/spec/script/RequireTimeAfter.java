package com.bloxbean.cardano.client.transaction.spec.script;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import lombok.Data;

/**
 * This script class is for “RequireTimeAfter” expression
 */
@Data
public class RequireTimeAfter implements NativeScript {
    private ScriptType type;
    private long slot;

    public RequireTimeAfter(long slot) {
        this.type = ScriptType.after;
        this.slot = slot;
    }

    public RequireTimeAfter after(long slot) {
        this.slot = slot;
        return this;
    }

    @Override
    public DataItem serializeAsDataItem() throws CborException {
        Array array = new Array();
        array.add(new UnsignedInteger(4));
        array.add(new UnsignedInteger(slot));
        return array;
    }
}
