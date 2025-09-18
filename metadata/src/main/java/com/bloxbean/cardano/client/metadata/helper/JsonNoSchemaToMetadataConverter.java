package com.bloxbean.cardano.client.metadata.helper;

import com.bloxbean.cardano.client.metadata.Metadata;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonNoSchemaToMetadataConverter extends JsonNodeToMetadataConverter {
    private static ObjectMapper mapper = new ObjectMapper();

    /**
     * Convert a valid json string to Metadata
     * @param json
     * @return
     * @throws JsonProcessingException
     */
    public static Metadata jsonToCborMetadata(String json) throws JsonProcessingException {
        JsonNode jsonNode = mapper.readTree(json);
        return parseJsonNode(jsonNode);
    }
}
