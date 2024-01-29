package com.bloxbean.cardano.client.plutus.util;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.plutus.spec.PlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.SneakyThrows;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This is a helper class which tries to pretty print {@link PlutusData} to utf8 json. It converts byte array to utf8 string
 * when possible. Otherwise, it prints the hex representation of the byte array.This is useful when you want to see the
 * data in a readable format, but not all data can be represented in a readable format.
 * Currently, this class is used to display CIP-68 metadata, which consists of {@link PlutusData}, in a readable format. This class
 * can be used in other similar scenarios.
 */
public class PlutusDataPrettyPrinter {
    private static ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);;

    /**
     * Convert {@link PlutusData} to utf8 encoded json String
     * @param plutusData data to be encoded
     * @return utf8 encoded json
     */
    @SneakyThrows
    public static String toJson(PlutusData plutusData) {
        DataItem serializedPlutusData = plutusData.serialize();
        Object o = parseItem(serializedPlutusData);
        return mapper.writeValueAsString(o);
    }

    /**
     * parsing a {@link DataItem} to utf8 object regarding it's type
     * @param item cbor {@link DataItem}
     * @return utf8 encoded representation
     */
    private static Object parseItem(DataItem item) {
        Object value = "";
        switch (item.getMajorType()) {
            case BYTE_STRING:
                value = bytestringItemToString((ByteString) item);
                break;
            case ARRAY:
                value = dataItemArrayToString((Array) item);
                break;
            case MAP:
                value = parseDataItemMap((Map) item);
                break;
            case UNSIGNED_INTEGER:
                value = ((UnsignedInteger)item).getValue();
                break;
            case NEGATIVE_INTEGER:
                value = ((NegativeInteger)item).getValue();
                break;
            case UNICODE_STRING:
                value = ((UnicodeString)item).getString();
                break;
            case TAG:
                value = ((Tag) item).getValue();
                break;
            default:
                throw new UnsupportedOperationException("Unkown type. Not implemented"); // TODO need to implement the other types
        }
        return value;
    }

    /**
     * {@link ByteString} item parsing to utf8 string
     * @param item
     * @return
     */
    private static String bytestringItemToString(ByteString item) {
        String value = "";
        byte[] bytes = item.getBytes();
        if(isUtf8String(bytes))
            value = new String(bytes);
        else
            value = HexUtil.encodeHexString(bytes, true);
        return value;
    }

    /**
     * {@link Array} item parsing to utf8 representation
     * @param array
     * @return
     */
    private static Object dataItemArrayToString(Array array) {
        List<Object> decodedItemList = new ArrayList<>();
        for (DataItem dataItem : array.getDataItems()) {
            if(!dataItem.getMajorType().equals(MajorType.SPECIAL)){
                decodedItemList.add(parseItem(dataItem));
            }
        }
        return decodedItemList.stream().toArray(Object[]::new);
    }

    /**
     * {@link Map} item parsing to utf8 representation
     * @param m
     * @return
     */
    private static HashMap<String, Object> parseDataItemMap(Map m) {
        HashMap<String, Object> decodedMap = new HashMap<>();
        for (DataItem keyItem : m.getKeys()) {
            String keyString = (String) parseItem(keyItem);
            Object valueString = parseItem(m.get(keyItem));
            decodedMap.put(keyString, valueString);
        }
        return decodedMap;
    }

    private static boolean isUtf8String(byte[] bytes) {
        CharsetDecoder decoder =
                StandardCharsets.UTF_8.newDecoder();
        try {
            decoder.decode(
                    ByteBuffer.wrap(bytes));
        } catch (CharacterCodingException ex) {
            return false;
        }
        return true;
    }

}
