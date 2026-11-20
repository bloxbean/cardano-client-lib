package com.bloxbean.cardano.client.plutus.spec.serializers;

import co.nstant.in.cbor.model.*;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.plutus.spec.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.bloxbean.cardano.client.plutus.spec.serializers.PlutusDataJsonKeys.*;

/**
 * Use this class to convert {@link PlutusData} to json or parse a compatible json to {@link PlutusData}
 * It supports detailed schema mapping for json conversion.
 * Follows ScriptDataJsonSchema in cardano-cli defined at for detailed schema mapping.
 * https://github.com/input-output-hk/cardano-node/blob/master/cardano-api/src/Cardano/Api/ScriptData.hs#L254
 */
public class PlutusDataJsonConverter {
    private static ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);;

    /**
     * Convert a compatible json string to {@link PlutusData}
     * @param json
     * @return
     * @throws JsonProcessingException
     */
    public static PlutusData toPlutusData(@NonNull String json) throws JsonProcessingException {
        JsonNode jsonNode = mapper.readTree(json);
        return toPlutusData(jsonNode);
    }

    /**
     * Convert a compatible {@link JsonNode} to {@link PlutusData}
     * @param jsonNode
     * @return
     * @throws JsonProcessingException
     */
    public static PlutusData toPlutusData(@NonNull JsonNode jsonNode) throws JsonProcessingException {
        if (jsonNode instanceof ObjectNode) {
            if (jsonNode.has(CONSTRUCTOR))
                return mapper.readValue(jsonNode.toString(), ConstrPlutusData.class);
            else if (jsonNode.has(INT))
                return mapper.readValue(jsonNode.toString(), BigIntPlutusData.class);
            else if (jsonNode.has(BYTES))
                return mapper.readValue(jsonNode.toString(), BytesPlutusData.class);
            else if (jsonNode.has(LIST)) {
                return mapper.readValue(jsonNode.toString(), ListPlutusData.class);
            } else if (jsonNode.has(MAP)) {
                return mapper.readValue(jsonNode.toString(), MapPlutusData.class);
            }
        }

        throw new IllegalArgumentException("Json parsing failed. " + jsonNode);
    }

    /**
     * Convert {@link PlutusData} to json
     * @param plutusData
     * @return
     * @throws JsonProcessingException
     */
    public static String toJson(PlutusData plutusData) throws JsonProcessingException {
        return mapper.writeValueAsString(plutusData);
    }

    /**
     * Convert {@link PlutusData} to utf8 encoded json String
     * @param plutusData data to be encoded
     * @return utf8 encoded json
     * @throws JsonProcessingException
     * @throws CborSerializationException
     */
    public static String toUTF8Json(PlutusData plutusData) throws JsonProcessingException, CborSerializationException {
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
        value = new String(bytes);
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
}