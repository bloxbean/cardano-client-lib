package com.bloxbean.cardano.client.backend.gql.adapter;

import com.apollographql.apollo.api.CustomTypeAdapter;
import com.apollographql.apollo.api.CustomTypeValue;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class JSONCustomTypeAdapter implements CustomTypeAdapter<JsonNode> {
    private ObjectMapper mapper = new ObjectMapper();

    @Override
    public JsonNode decode(@NotNull CustomTypeValue<?> customTypeValue) {
        try {
            return mapper.valueToTree(customTypeValue.value);
        } catch (Exception e) {
            if(customTypeValue.value != null)
                return new TextNode(customTypeValue.value.toString());
            else
                return new TextNode("");
        }
    }

    @NotNull
    @Override
    public CustomTypeValue<?> encode(JsonNode jsonNode) { //TODO Not tested
        if (jsonNode == null)
            return null;

        if (jsonNode instanceof ObjectNode) {
            return new CustomTypeValue.GraphQLJsonObject(mapper.convertValue(jsonNode, new TypeReference<Map<String, Object>>() {
            }));
        } else if(jsonNode instanceof ArrayNode) {
            return new CustomTypeValue.GraphQLJsonList(mapper.convertValue(jsonNode, new TypeReference<List<Object>>() {}));
        } else {
            return new CustomTypeValue.GraphQLString(jsonNode.toString());
        }
    }
}
