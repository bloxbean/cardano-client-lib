package com.bloxbean.cardano.client.transaction.spec.script;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import lombok.Data;

/**
 * This script class is for "RequireTimeBefore" expression
 */
@Data
public class RequireTimeBefore implements NativeScript {
    private ScriptType type;
    private long slot;

    public RequireTimeBefore(long slot) {
        this.type = ScriptType.before;
        this.slot = slot;
    }

    public RequireTimeBefore before(long slot) {
        this.slot = slot;
        return this;
    }

    @Override
    public DataItem serializeAsDataItem() throws CborException {
        Array array = new Array();
        array.add(new UnsignedInteger(5));
        array.add(new UnsignedInteger(slot));
        return array;
    }
}
