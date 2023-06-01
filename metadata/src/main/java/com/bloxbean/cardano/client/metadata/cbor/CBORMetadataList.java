package com.bloxbean.cardano.client.metadata.cbor;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.metadata.MetadataList;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import com.bloxbean.cardano.client.util.JsonUtil;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static com.bloxbean.cardano.client.metadata.cbor.MetadataHelper.*;

public class CBORMetadataList implements MetadataList {
    Array array;

    public CBORMetadataList() {
        array = new Array();
    }

    public CBORMetadataList(Array array) {
        this.array = array;
    }

    @Override
    public CBORMetadataList add(BigInteger value) {
        array.add(new UnsignedInteger(value));
        return this;
    }

    @Override
    public CBORMetadataList addNegative(BigInteger value) {
        array.add(new NegativeInteger(value));
        return this;
    }

    @Override
    public CBORMetadataList add(String value) {
        checkLength(value);
        array.add(new UnicodeString(value));
        return this;
    }

    @Override
    public CBORMetadataList addAll(String[] value) {
        for (String str : value) {
            checkLength(str);
            array.add(new UnicodeString(str));
        }
        return this;
    }

    @Override
    public CBORMetadataList add(byte[] value) {
        array.add(new ByteString(value));
        return this;
    }

    @Override
    public CBORMetadataList add(MetadataMap map) {
        if(map != null)
            array.add(map.getMap());
        return this;
    }

    @Override
    public CBORMetadataList add(MetadataList list) {
        if(list != null)
            array.add(list.getArray());
        return this;
    }

    @Override
    public void replaceAt(int index, BigInteger value) {
        replaceAt(index, objectToDataItem(value));
    }

    @Override
    public void replaceAt(int index, String value) {
        replaceAt(index, objectToDataItem(value));
    }

    @Override
    public void replaceAt(int index, byte[] value) {
        replaceAt(index, objectToDataItem(value));
    }

    @Override
    public void replaceAt(int index, MetadataMap map) {
        replaceAt(index, objectToDataItem(map));
    }

    @Override
    public void replaceAt(int index, MetadataList list) {
        replaceAt(index, objectToDataItem(list));
    }

    @Override
    public void removeItem(Object value) {
        array.getDataItems().remove(objectToDataItem(value));
    }

    @Override
    public void removeItemAt(int index) {
        if(index != -1 && index < array.getDataItems().size()) {
            array.getDataItems().remove(index);
        }
    }

    @Override
    public Object getValueAt(int index) {
        if(index != -1 && index < array.getDataItems().size()) {
            DataItem dataItem = array.getDataItems().get(index);
            return extractActualValue(dataItem);
        }

        return null;
    }

    @Override
    public int size() {
        if(array.getDataItems() != null)
            return array.getDataItems().size();
        else
            return 0;
    }

    @Override
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
