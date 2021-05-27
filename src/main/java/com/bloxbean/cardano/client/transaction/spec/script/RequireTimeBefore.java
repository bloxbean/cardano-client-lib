package com.bloxbean.cardano.client.transaction.spec.script;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
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
    public DataItem serializeAsDataItem() {
        Array array = new Array();
        array.add(new UnsignedInteger(5));
        array.add(new UnsignedInteger(slot));
        return array;
    }

    public static RequireTimeBefore deserialize(Array array) throws CborDeserializationException {
        long slot = ((UnsignedInteger)array.getDataItems().get(1)).getValue().longValue();
        return new RequireTimeBefore(slot);
    }
}
