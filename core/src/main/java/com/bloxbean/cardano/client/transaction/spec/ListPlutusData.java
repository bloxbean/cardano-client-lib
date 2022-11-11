package com.bloxbean.cardano.client.transaction.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Special;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ListPlutusData implements PlutusData {

    @Builder.Default
    private List<PlutusData> plutusDataList = new ArrayList<>();

    @Builder.Default
    private boolean isChunked = true;

    public static ListPlutusData of(PlutusData... plutusDataList) {
        ListPlutusData listPlutusData = new ListPlutusData();
        Arrays.stream(plutusDataList).forEach(plutusData -> listPlutusData.add(plutusData));

        return listPlutusData;
    }

    public static ListPlutusData deserialize(Array arrayDI) throws CborDeserializationException {
        if (arrayDI == null)
            return null;

        boolean isChunked = false;
        ListPlutusData listPlutusData = new ListPlutusData();
        for (DataItem di : arrayDI.getDataItems()) {
            if (di == Special.BREAK) {
                isChunked = true;
                break;
            }

            PlutusData plutusData = PlutusData.deserialize(di);
            if (plutusData == null)
                throw new CborDeserializationException("Null value found during PlutusData de-serialization");

            listPlutusData.add(PlutusData.deserialize(di));
        }

        listPlutusData.isChunked = isChunked;

        return listPlutusData;
    }

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

        if (plutusDataList.size() == 0)
            return plutusDataArray;

        if (isChunked)
            plutusDataArray.setChunked(true);

        for (PlutusData plutusData : plutusDataList) {
            DataItem di = plutusData.serialize();
            if (di == null) {
                throw new CborSerializationException("Cbor Serialization failed for plutus data. NULL serialized value found in the list");
            }

            plutusDataArray.add(di);
        }

        if (isChunked)
            plutusDataArray.add(Special.BREAK);

        return plutusDataArray;
    }

}
