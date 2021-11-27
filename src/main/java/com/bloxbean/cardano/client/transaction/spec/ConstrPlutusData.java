package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Tag;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ConstrPlutusData implements PlutusData {
    private static final long GENERAL_FORM_TAG = 102;
    private long tag;
    private ListPlutusData data;

    @Override
    public DataItem serialize() throws CborSerializationException {
        boolean isCompact = isTagCompact();
        DataItem dataItem = null;

        if (isCompact) {
            dataItem = data.serialize();
            dataItem.setTag(tag);
        } else {
            //general form
            Array constrArray = new Array();
            constrArray.add(new UnsignedInteger(tag));
            constrArray.add(data.serialize());
            dataItem = constrArray;
            dataItem.setTag(GENERAL_FORM_TAG);
        }

        return dataItem;
    }

    public static ConstrPlutusData deserialize(DataItem di) throws CborDeserializationException {
        Tag diTag = di.getTag();
        Long tag = null;
        ListPlutusData data = null;

        if (GENERAL_FORM_TAG == diTag.getValue()) { //general form
            Array constrArray = (Array) di;
            List<DataItem> dataItems = constrArray.getDataItems();

            if (dataItems.size() != 2)
                throw new CborDeserializationException("Cbor deserialization failed. Expected 2 DataItem, found : " + dataItems.size());

            tag = ((UnsignedInteger) dataItems.get(0)).getValue().longValue();
            data = ListPlutusData.deserialize((Array) dataItems.get(1));

        } else { //concise form
            tag = diTag.getValue();
            data = ListPlutusData.deserialize((Array) di);
        }

        return ConstrPlutusData.builder()
                .tag(tag)
                .data(data)
                .build();
    }

    private boolean isTagCompact() {
        if ((tag >= 121 && tag <= 127) || (tag >= 1280 && tag <= 1400))
            return true;
        else
            return false;
    }
}
