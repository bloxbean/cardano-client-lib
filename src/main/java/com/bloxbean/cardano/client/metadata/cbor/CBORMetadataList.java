package com.bloxbean.cardano.client.metadata.cbor;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.util.JsonUtil;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.metadata.cbor.MetadataHelper.*;

public class CBORMetadataList {
    Array array;

    public CBORMetadataList() {
        array = new Array();
    }

    public CBORMetadataList(Array array) {
        this.array = array;
    }

    public CBORMetadataList add(BigInteger value) {
        array.add(new UnsignedInteger(value));
        return this;
    }

    public CBORMetadataList addNegative(BigInteger value) {
        array.add(new NegativeInteger(value));
        return this;
    }

    public CBORMetadataList add(String value) {
        checkLength(value);
        array.add(new UnicodeString(value));
        return this;
    }

    public CBORMetadataList add(byte[] value) {
        array.add(new ByteString(value));
        return this;
    }

    public CBORMetadataList add(CBORMetadataMap map) {
        if(map != null)
            array.add(map.getMap());
        return this;
    }

    public CBORMetadataList add(CBORMetadataList list) {
        if(list != null)
            array.add(list.getArray());
        return this;
    }

    public void replaceAt(int index, BigInteger value) {
        replaceAt(index, objectToDataItem(value));
    }

    public void replaceAt(int index, String value) {
        replaceAt(index, objectToDataItem(value));
    }

    public void replaceAt(int index, byte[] value) {
        replaceAt(index, objectToDataItem(value));
    }

    public void replaceAt(int index, CBORMetadataMap map) {
        replaceAt(index, objectToDataItem(map));
    }

    public void replaceAt(int index, CBORMetadataList list) {
        replaceAt(index, objectToDataItem(list));
    }

    public void removeItem(Object value) {
        array.getDataItems().remove(objectToDataItem(value));
    }

    public void removeItemAt(int index) {
        if(index != -1 && index < array.getDataItems().size()) {
            array.getDataItems().remove(index);
        }
    }

    public Object getValueAt(int index) {
        if(index != -1 && index < array.getDataItems().size()) {
            DataItem dataItem = array.getDataItems().get(index);
            return extractActualValue(dataItem);
        }

        return null;
    }

    public int size() {
        if(array.getDataItems() != null)
            return array.getDataItems().size();
        else
            return 0;
    }

    public Array getArray() {
        return array;
    }

    private void replaceAt(int index, DataItem value) {
        if(index == -1)
            return;
        array.getDataItems().remove(index);
        array.getDataItems().add(index, value);
    }

    public String toJson() {
        List<DataItem> dataItemList = array.getDataItems();
        List list = new ArrayList();

        for (DataItem di: dataItemList) {
            list.add(extractActualValue(di));
        }

        return JsonUtil.getPrettyJson(list);
    }




}
