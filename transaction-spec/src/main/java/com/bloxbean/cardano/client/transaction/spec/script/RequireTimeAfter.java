package com.bloxbean.cardano.client.transaction.spec.script;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;

/**
 * This script class is for "RequireTimeAfter" expression
 */
@Data
@NoArgsConstructor
public class RequireTimeAfter implements NativeScript {

    private final ScriptType type = ScriptType.after;
    private BigInteger slot;

    public RequireTimeAfter(long slot) {
        this.slot = BigInteger.valueOf(slot);
    }

    public RequireTimeAfter(BigInteger slot) {
        this.slot = slot;
    }

    @Override
    public DataItem serializeAsDataItem() {
        Array array = new Array();
        array.add(new UnsignedInteger(4));
        array.add(new UnsignedInteger(slot));
        return array;
    }

    public static RequireTimeAfter deserialize(Array array) throws CborDeserializationException {
        long slot = ((UnsignedInteger)array.getDataItems().get(1)).getValue().longValue();
        return new RequireTimeAfter(slot);
    }

    public static RequireTimeAfter deserialize(JsonNode jsonNode) throws CborDeserializationException {
        long slot = jsonNode.get("slot").asLong();
        return new RequireTimeAfter(slot);
    }
}
