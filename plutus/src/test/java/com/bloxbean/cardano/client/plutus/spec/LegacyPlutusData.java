package com.bloxbean.cardano.client.plutus.spec;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.Number;
import co.nstant.in.cbor.model.Special;
import co.nstant.in.cbor.model.Tag;
import co.nstant.in.cbor.model.UnicodeString;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.exception.CborDeserializationException;
import com.bloxbean.cardano.client.exception.CborSerializationException;

import java.util.List;

/**
 * The recursive Plutus data conversion that {@link PlutusDataCodec} replaced, kept as a test oracle and benchmark
 * baseline (same logic, cbor-java maps).
 */
final class LegacyPlutusData {
    private LegacyPlutusData() {
    }

    static PlutusData deserialize(DataItem dataItem) throws CborDeserializationException {
        if (dataItem == null)
            return null;
        if (dataItem instanceof Number) {
            return BigIntPlutusData.deserialize((Number) dataItem);
        } else if (dataItem instanceof ByteString) {
            Tag tag = dataItem.getTag();
            if (tag != null && (tag.getValue() == PlutusData.BIG_UINT_TAG || tag.getValue() == PlutusData.BIG_NINT_TAG))
                return BigIntPlutusData.deserialize((ByteString) dataItem);
            return BytesPlutusData.deserialize((ByteString) dataItem);
        } else if (dataItem instanceof UnicodeString) {
            return BytesPlutusData.deserialize((UnicodeString) dataItem);
        } else if (dataItem instanceof Array) {
            if (dataItem.getTag() == null)
                return deserializeList((Array) dataItem);
            return deserializeConstr(dataItem);
        } else if (dataItem instanceof Map) {
            Map mapDI = (Map) dataItem;
            MapPlutusData map = new MapPlutusData();
            for (DataItem keyDI : mapDI.getKeys())
                map.put(deserialize(keyDI), deserialize(mapDI.get(keyDI)));
            return map;
        }
        throw new CborDeserializationException("Cbor deserialization failed. Invalid type. " + dataItem);
    }

    private static ListPlutusData deserializeList(Array arrayDI) throws CborDeserializationException {
        boolean isChunked = false;
        ListPlutusData list = new ListPlutusData();
        for (DataItem di : arrayDI.getDataItems()) {
            if (di == Special.BREAK) {
                isChunked = true;
                break;
            }
            list.add(deserialize(di));
        }
        return ListPlutusData.builder().plutusDataList(list.getPlutusDataList()).isChunked(isChunked).build();
    }

    private static ConstrPlutusData deserializeConstr(DataItem di) throws CborDeserializationException {
        Tag tag = di.getTag();
        long alternative;
        ListPlutusData data;
        if (tag.getValue() == 102) {
            List<DataItem> dataItems = ((Array) di).getDataItems();
            alternative = ((UnsignedInteger) dataItems.get(0)).getValue().longValue();
            data = deserializeList((Array) dataItems.get(1));
        } else {
            alternative = PlutusDataCodec.compactTagToAlternative(tag.getValue());
            data = deserializeList((Array) di);
        }
        return ConstrPlutusData.builder().alternative(alternative).data(data).build();
    }

    static DataItem serialize(PlutusData data) throws CborSerializationException {
        if (data instanceof ListPlutusData) {
            ListPlutusData list = (ListPlutusData) data;
            if (list.getPlutusDataList() == null)
                return null;
            Array array = new Array();
            if (list.getPlutusDataList().isEmpty())
                return array;
            if (list.isChunked())
                array.setChunked(true);
            for (PlutusData item : list.getPlutusDataList())
                array.add(serialize(item));
            if (list.isChunked())
                array.add(Special.BREAK);
            return array;
        }
        if (data instanceof ConstrPlutusData) {
            ConstrPlutusData constr = (ConstrPlutusData) data;
            Long tag = PlutusDataCodec.alternativeToCompactTag(constr.getAlternative());
            if (tag != null) {
                DataItem di = serialize(constr.getData());
                di.setTag(tag);
                return di;
            }
            Array array = new Array();
            array.add(new UnsignedInteger(constr.getAlternative()));
            array.add(serialize(constr.getData()));
            array.setTag(102);
            return array;
        }
        if (data instanceof MapPlutusData) {
            Map map = new Map();
            for (java.util.Map.Entry<PlutusData, PlutusData> entry : ((MapPlutusData) data).getMap().entrySet())
                map.put(serialize(entry.getKey()), serialize(entry.getValue()));
            return map;
        }
        return data.serialize();
    }
}
