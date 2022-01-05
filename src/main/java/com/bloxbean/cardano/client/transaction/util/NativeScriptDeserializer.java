package com.bloxbean.cardano.client.transaction.util;

import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.SneakyThrows;

import java.io.IOException;

public class NativeScriptDeserializer extends JsonDeserializer<NativeScript> {

    @SneakyThrows
    @Override
    public NativeScript deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        JsonNode node = jsonParser.getCodec().readTree(jsonParser);
        return NativeScript.deserializeJson(node.toString());
    }
}
