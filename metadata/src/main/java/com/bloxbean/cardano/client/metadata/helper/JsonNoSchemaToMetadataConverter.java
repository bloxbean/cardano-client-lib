package com.bloxbean.cardano.client.metadata.helper;

import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataList;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;
import com.bloxbean.cardano.client.metadata.exception.JsonMetadaException;
import com.bloxbean.cardano.client.metadata.exception.MetadataSerializationException;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;

import java.math.BigInteger;
import java.util.Iterator;

public class JsonNoSchemaToMetadataConverter {
    private static ObjectMapper mapper = new ObjectMapper();

    /**
     * Convert a valid json string to Metadata
     * @param json
     * @return
     * @throws JsonProcessingException
     */
    public static Metadata jsonToCborMetadata(String json) throws JsonProcessingException {
        JsonNode jsonNode = mapper.readTree(json);

        if(jsonNode instanceof ObjectNode) {
            return parseTopJsonObject((ObjectNode) jsonNode);
        } else
            throw new MetadataSerializationException("Invalid json type : " + jsonNode.getNodeType());
    }

    private static CBORMetadata parseTopJsonObject(ObjectNode objectNode) {
        Iterator<String> fields = objectNode.fieldNames();
        CBORMetadata metadata = new CBORMetadata();
        while(fields.hasNext()) {
            String field = fields.next();

            BigInteger key = new BigInteger(field);
            JsonNode value = objectNode.get(field);

            Object cborValue = processValueNode(value);

            if (cborValue instanceof CBORMetadataMap) {
                metadata.put(key, (CBORMetadataMap)cborValue);
            } else if (cborValue instanceof CBORMetadataList) {
                metadata.put(key, (CBORMetadataList) cborValue);
            } else if (cborValue instanceof BigInteger) {
                BigInteger bi = (BigInteger)cborValue;
                if(bi.compareTo(BigInteger.ZERO) == -1)
                    metadata.putNegative(key, (BigInteger) cborValue);
                else
                    metadata.put(key, (BigInteger) cborValue);
            } else if (cborValue instanceof String) {
                metadata.put(key, (String)cborValue);
            } else if (cborValue instanceof byte[]) {
                metadata.put(key, (byte[])cborValue);
            }
        }

        return metadata;
    }

    public static CBORMetadataList parseArrayNode(ArrayNode value) {
        CBORMetadataList metadataList = new CBORMetadataList();
        Iterator<JsonNode> fields = value.elements();

        while(fields.hasNext()) {
            JsonNode node = fields.next();
            Object cborValue = processValueNode(node);

            if (cborValue instanceof CBORMetadataMap) {
                metadataList.add((CBORMetadataMap) cborValue);
            } else if (cborValue instanceof CBORMetadataList) {
                metadataList.add((CBORMetadataList) cborValue);
            } else if (cborValue instanceof BigInteger) {
                BigInteger bi = (BigInteger)cborValue;
                if(bi.compareTo(BigInteger.ZERO) == -1)
                    metadataList.addNegative((BigInteger) cborValue);
                else
                    metadataList.add((BigInteger) cborValue);
            } else if (cborValue instanceof String) {
                metadataList.add((String) cborValue);
            } else if (cborValue instanceof byte[]) {
                metadataList.add((byte[]) cborValue);
            }
        }

        return metadataList;
    }

    public static CBORMetadataMap parseObjectNode(ObjectNode jsonObj) {
        CBORMetadataMap metadataMap = new CBORMetadataMap();
        Iterator<String> fields = jsonObj.fieldNames();

        while(fields.hasNext()) {
            String field = fields.next();
            JsonNode nd = jsonObj.get(field);

            Object cborValue = processValueNode(nd);
            if (cborValue == null) {
                metadataMap.put(field, (String)null);
            } else if (cborValue instanceof CBORMetadataMap) {
                metadataMap.put(field, (CBORMetadataMap) cborValue);
            } else if (cborValue instanceof CBORMetadataList) {
                metadataMap.put(field, (CBORMetadataList) cborValue);
            } else if (cborValue instanceof BigInteger) {
                BigInteger bi = (BigInteger)cborValue;
                if(bi.compareTo(BigInteger.ZERO) == -1)
                    metadataMap.putNegative(field, (BigInteger) cborValue);
                else
                    metadataMap.put(field, (BigInteger) cborValue);
            } else if (cborValue instanceof String) {
                metadataMap.put(field, (String) cborValue);
            } else if (cborValue instanceof byte[]) {
                metadataMap.put(field, (byte[]) cborValue);
            } else {
                throw new JsonMetadaException("Invalid value type : " + cborValue);
            }
        }

        return metadataMap;
    }

    private static Object processValueNode(JsonNode value) {
        if(value instanceof ObjectNode) { //Map
            CBORMetadataMap metadataMap = parseObjectNode((ObjectNode)value);
            return metadataMap;
        } else if(value instanceof ArrayNode) { //List
            CBORMetadataList metadataList = parseArrayNode((ArrayNode)value);
            return metadataList;
        } else if(value instanceof TextNode) {
            String textValue = ((TextNode)value).asText();
            if(textValue.startsWith("0x")) { //Hex value
                try {
                    byte[] hexValue = HexUtil.decodeHexString(textValue.substring(2));
                    return hexValue;
                } catch (Exception e) {
                    throw new JsonMetadaException("Invalid hex value : " + textValue);
                }
            } else {
                return textValue;
            }
        } else if(value instanceof NumericNode) {
            BigInteger valueInt = ((NumericNode)value).bigIntegerValue();
            return valueInt;
        } else if (value instanceof NullNode) {
            return null;
        } else {
            throw new JsonMetadaException("Invalid value or value not recognized : " + value);
        }
    }

    private boolean isValidValueType(Object obj) {
        if(obj == null)
            throw new JsonMetadaException("Null value is not allowed in cbor metadata");

        if(obj instanceof CBORMetadataMap || obj instanceof CBORMetadataList || obj instanceof byte[]
                || obj instanceof String || obj instanceof BigInteger)
            return true;
        else
            throw new JsonMetadaException("Invalid value type : " + obj.getClass());
    }
}
