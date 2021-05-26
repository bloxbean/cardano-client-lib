package com.bloxbean.cardano.client.metadata.cbor;

import co.nstant.in.cbor.model.*;

import java.math.BigInteger;

public class CBORMetadataList {
    Array array;

    public CBORMetadataList() {
        array = new Array();
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

    public CBORMetadataList MetadataList(CBORMetadataList list) {
        if(list != null)
            array.add(list.getArray());
        return this;
    }

    Array getArray() {
        return array;
    }
}
