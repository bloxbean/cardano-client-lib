package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ExUnits {
    private BigInteger mem;
    private BigInteger steps;

    public Array serialize() {
        Array array = new Array();
        array.add(new UnsignedInteger(mem));
        array.add(new UnsignedInteger(steps));

        return array;
    }

    public static ExUnits deserialize(Array exUnitDI) throws CborDeserializationException {
        List<DataItem> dataItemList = exUnitDI.getDataItems();
        if (dataItemList == null || dataItemList.size() != 2)
            throw new CborDeserializationException("ExUnits deserialization error. Invalid no of DataItem");

        DataItem memDI = dataItemList.get(0);
        DataItem stepsDI = dataItemList.get(1);

        ExUnits exUnits = new ExUnits();
        exUnits.setMem(((UnsignedInteger) memDI).getValue());
        exUnits.setSteps(((UnsignedInteger) stepsDI).getValue());

        return exUnits;
    }

}
