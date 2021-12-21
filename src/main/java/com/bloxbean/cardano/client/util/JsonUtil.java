package com.bloxbean.cardano.client.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class JsonUtil {
    protected final static ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);;

    public static String getPrettyJson(Object obj) {
        if(obj == null) return null;
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return obj.toString();
        }
    }

    public static String getPrettyJson(String jsonStr) {
        if(jsonStr == null)
            return null;

        try {
            Object json = mapper.readValue(jsonStr, Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (Exception e) {
            return jsonStr;
        }
    }

    public static JsonNode parseJson(String jsonContent) throws JsonProcessingException {
        return mapper.readTree(jsonContent);
    }
}
