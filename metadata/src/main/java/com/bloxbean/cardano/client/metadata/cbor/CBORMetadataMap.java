package com.bloxbean.cardano.client.metadata.cbor;

import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.metadata.MetadataList;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import com.bloxbean.cardano.client.metadata.helper.MetadataToJsonNoSchemaConverter;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.bloxbean.cardano.client.util.StringUtils;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

import static com.bloxbean.cardano.client.metadata.cbor.MetadataHelper.*;

public class CBORMetadataMap implements MetadataMap {
    private Map map;

    public CBORMetadataMap() {
        map = new Map();
    }

    public CBORMetadataMap(Map map) {
        this.map = map;
    }

    @Override
    public CBORMetadataMap put(BigInteger key, BigInteger value) {
        map.put(new UnsignedInteger(key), new UnsignedInteger(value));
        return this;
    }

    @Override
    public CBORMetadataMap putNegative(BigInteger key, BigInteger value) {
        map.put(new UnsignedInteger(key), new NegativeInteger(value));
        return this;
    }

    @Override
    public CBORMetadataMap put(BigInteger key, byte[] value) {
        map.put(new UnsignedInteger(key), new ByteString(value));
        return this;
    }

    @Override
    public CBORMetadataMap put(BigInteger key, String value) {
        checkLength(value);
        map.put(new UnsignedInteger(key), new UnicodeString(value));
        return this;
    }

    @Override
    public CBORMetadataMap put(BigInteger key, MetadataMap value) {
        if(value != null)
            map.put(new UnsignedInteger(key), value.getMap());
        return this;
    }

    @Override
    public CBORMetadataMap put(BigInteger key, MetadataList list) {
        if(list != null)
            map.put(new UnsignedInteger(key), list.getArray());
        return this;
    }

    @Override
    public CBORMetadataMap put(String key, BigInteger value) {
        map.put(new UnicodeString(key), new UnsignedInteger(value));
        return this;
    }

    @Override
    public CBORMetadataMap putNegative(String key, BigInteger value) {
        map.put(new UnicodeString(key), new NegativeInteger(value));
        return this;
    }

    @Override
    public CBORMetadataMap put(String key, byte[] value) {
        map.put(new UnicodeString(key), new ByteString(value));
        return this;
    }

    @Override
    public CBORMetadataMap put(String key, String value) {
        if (checkLength(value) > 64) {
            CBORMetadataList cborMetadataList = new CBORMetadataList();
            cborMetadataList.addAll(StringUtils.splitStringEveryNCharacters(value, 64));
            map.put(new UnicodeString(key), cborMetadataList.getArray());
        } else {
            map.put(new UnicodeString(key), new UnicodeString(value));
        }
        return this;
    }

    @Override
    public CBORMetadataMap put(String key, MetadataMap value) {
        if(value != null)
            map.put(new UnicodeString(key), value.getMap());
        return this;
    }

    @Override
    public CBORMetadataMap put(String key, MetadataList list) {
        if(list != null)
            map.put(new UnicodeString(key), list.getArray());
        return this;
    }

    @Override
    public CBORMetadataMap put(byte[] key, BigInteger value) {
        map.put(new ByteString(key), new UnsignedInteger(value));
        return this;
    }

    @Override
    public CBORMetadataMap putNegative(byte[] key, BigInteger value) {
        map.put(new ByteString(key), new NegativeInteger(value));
        return this;
    }

    @Override
    public CBORMetadataMap put(byte[] key, byte[] value) {
        map.put(new ByteString(key), new ByteString(value));
        return this;
    }

    @Override
    public CBORMetadataMap put(byte[] key, String value) {
        checkLength(value);
        map.put(new ByteString(key), new UnicodeString(value));
        return this;
    }

    @Override
    public CBORMetadataMap put(byte[] key, MetadataMap value) {
        if(value != null)
            map.put(new ByteString(key), value.getMap());
        return this;
    }

    @Override
    public CBORMetadataMap put(byte[] key, MetadataList list) {
        if(list != null)
            map.put(new ByteString(key), list.getArray());
        return this;
    }

    @Override
    public Object get(String key) {
        return extractActualValue(this.map.get(objectToDataItem(key)));
    }

    @Override
    public Object get(BigInteger key) {
        return extractActualValue(this.map.get(objectToDataItem(key)));
    }

    @Override
    public Object get(byte[] key) {
        return extractActualValue(this.map.get(objectToDataItem(key)));
    }

    @Override
    public void remove(String key) {
        this.map.remove(objectToDataItem(key));
    }

    @Override
    public void remove(BigInteger key) {
        this.map.remove(objectToDataItem(key));
    }

    @Override
    public void remove(byte[] key) {
        this.map.remove(objectToDataItem(key));
    }

    @Override
    public List keys() {
        return this.map.getKeys().stream().map(di -> extractActualValue(di)).collect(Collectors.toList());
    }

    @Override
    public Map getMap() {
        return map;
    }

    @Override
    public String toJson() throws CborException {
        byte[] bytes = CborSerializationUtil.serialize(map);
        String json = MetadataToJsonNoSchemaConverter.cborBytesToJson(bytes);
        return JsonUtil.getPrettyJson(json);
    }
}
