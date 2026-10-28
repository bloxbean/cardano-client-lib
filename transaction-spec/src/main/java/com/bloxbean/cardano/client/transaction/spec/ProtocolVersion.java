package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import lombok.*;

import java.util.List;

import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.getBigInteger;
import static com.bloxbean.cardano.client.common.cbor.CborSerializationUtil.toInt;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@ToString
@Builder
public class ProtocolVersion {
    private int major;
    // cardano-ledger: a Natural until protocol version 12, then a Word32
    private long minor;

    public Array serialize() {
        Array array = new Array();
        array.add(new UnsignedInteger(major));
        array.add(new UnsignedInteger(minor));

        return array;
    }

    public static ProtocolVersion deserialize(Array array) {
        List<DataItem> protocolVerDIList = array.getDataItems();
        if (protocolVerDIList.size() != 2)
            throw new IllegalArgumentException("Invalid protocol version array. Expected 2 items. Found : "
                    + protocolVerDIList.size());

        int major = toInt(protocolVerDIList.get(0));
        long minor = getBigInteger(protocolVerDIList.get(1)).longValueExact();

        return new ProtocolVersion(major, minor);
    }
}
