package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ListPlutusData implements PlutusData {
    private List<PlutusData> plutusDataList = new ArrayList<>();

    public void add(PlutusData plutusData) {
        if (plutusDataList == null)
            plutusDataList = new ArrayList<>();

        plutusDataList.add(plutusData);
    }

    @Override
    public DataItem serialize() throws CborSerializationException {
        if (plutusDataList == null)
            return null;

        Array plutusDataArray = new Array();
        for (PlutusData plutusData : plutusDataList) {
            DataItem di = plutusData.serialize();
            if (di == null) {
                throw new CborSerializationException("Cbor Serialization failed for plutus data. NULL serialized value found in the list");
            }

            plutusDataArray.add(di);
        }

        return plutusDataArray;
    }

    public static ListPlutusData deserialize(Array arrayDI) throws CborDeserializationException {
        if (arrayDI == null)
            return null;

        ListPlutusData listPlutusData = new ListPlutusData();
        for (DataItem di : arrayDI.getDataItems()) {
            PlutusData plutusData = PlutusData.deserialize(di);
            if (plutusData == null)
                throw new CborDeserializationException("Null value found during PlutusData de-serialization");

            listPlutusData.add(PlutusData.deserialize(di));
        }

        return listPlutusData;
    }

}
