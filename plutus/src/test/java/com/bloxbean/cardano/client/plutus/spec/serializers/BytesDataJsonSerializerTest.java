package com.bloxbean.cardano.client.plutus.spec.serializers;

import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BytesDataJsonSerializerTest {
    ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void serialize() throws JsonProcessingException {
        BytesPlutusData bytesPlutusData = BytesPlutusData.of("Hello World");
        System.out.println(bytesPlutusData.serializeToHex());

        String json = JsonUtil.getPrettyJson(bytesPlutusData);
        JsonNode actualJsonNode = JsonUtil.parseJson(json);

        JsonNode expectedJson = JsonUtil.parseJson("{\n" +
                "   \"bytes\": \"48656c6c6f20576f726c64\"\n" +
                "}");
        assertThat(actualJsonNode).isEqualTo(expectedJson);
    }

    @Test
    void deserialize() throws JsonProcessingException {
        String json = "{\n" +
                "   \"bytes\": \"48656c6c6f20576f726c64\"\n" +
                "}";
        BytesPlutusData bytesPlutusData = objectMapper.readValue(json, BytesPlutusData.class);

        BytesPlutusData expectedBytesPlutusData = BytesPlutusData.of("Hello World");
        assertThat(bytesPlutusData).isEqualTo(expectedBytesPlutusData);
    }
}
