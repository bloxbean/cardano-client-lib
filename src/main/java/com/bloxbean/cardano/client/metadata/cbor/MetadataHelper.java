package com.bloxbean.cardano.client.metadata.cbor;

import co.nstant.in.cbor.model.*;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

@Slf4j
public class MetadataHelper {

    public static Object extractActualValue(DataItem dataItem) {
        if (dataItem instanceof UnicodeString) {
            return ((UnicodeString) dataItem).getString();
        } else if (dataItem instanceof UnsignedInteger) {
            return ((UnsignedInteger) dataItem).getValue();
        } else if (dataItem instanceof NegativeInteger) {
            return ((NegativeInteger) dataItem).getValue();
        } else if (dataItem instanceof ByteString) {
            return ((ByteString) dataItem).getBytes();
        } else if (dataItem instanceof Map) {
            return new CBORMetadataMap((Map) dataItem);
        } else if (dataItem instanceof Array) {
            return new CBORMetadataList((Array) dataItem);
        } else {
            return dataItem;
        }
    }

    public static DataItem objectToDataItem(Object value) {
        if (value == null)
            return null;

        if (value instanceof String) {
            return new UnicodeString((String) value);
        } else if (value instanceof BigInteger) {
            if(((BigInteger) value).compareTo(BigInteger.ZERO) == -1) {
                return new NegativeInteger((BigInteger) value);
            } else {
                return new UnsignedInteger((BigInteger) value);
            }
        } else if (value instanceof byte[]) {
            return new ByteString((byte[]) value);
        } else if (value instanceof CBORMetadataMap) {
            return ((CBORMetadataMap) value).getMap();
        } else if (value instanceof CBORMetadataList) {
            return ((CBORMetadataList) value).getArray();
        } else {
            throw new RuntimeException("Unknown object type : " + value.getClass());
        }
    }

    public static void checkLength(String str) {
        if (str != null && str.getBytes(StandardCharsets.UTF_8).length > 64)
            log.error("Strings in metadata must be at most 64 bytes when UTF-8 encoded. >> " + str);
        //TODO -- throw error ?
    }
}
