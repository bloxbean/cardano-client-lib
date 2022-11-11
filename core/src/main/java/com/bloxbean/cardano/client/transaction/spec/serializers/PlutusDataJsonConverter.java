package com.bloxbean.cardano.client.transaction.spec.serializers;

import com.bloxbean.cardano.client.transaction.spec.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.NonNull;

import static com.bloxbean.cardano.client.transaction.spec.serializers.PlutusDataJsonKeys.*;

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
}
