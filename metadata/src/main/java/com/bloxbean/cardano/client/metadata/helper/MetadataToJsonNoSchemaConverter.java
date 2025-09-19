package com.bloxbean.cardano.client.metadata.helper;

import com.bloxbean.cardano.client.metadata.exception.MetadataDeSerializationException;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MetadataToJsonNoSchemaConverter extends AbstractMetadataConverter {
    private final static ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Convert cbor metadata bytes to json string
     * @param cborBytes
     * @return
     */
    public static String cborBytesToJson(byte[] cborBytes)  {
        try {
           return cborHexToJson(HexUtil.encodeHexString(cborBytes));
        } catch (Exception e) {
            throw new MetadataDeSerializationException("Deserialization error", e);
        }
    }

    /**
     * Converts cbor metadata bytes in hex format to json string
     * @param hex
     * @return
     */
    public static String cborHexToJson(String hex)  {
        try {
            java.util.Map<Object, Object> result = cborHexToJavaMap(hex);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            throw new MetadataDeSerializationException("Deserialization error", e);
        }
    }
}
