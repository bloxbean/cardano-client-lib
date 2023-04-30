package com.bloxbean.cardano.client.plutus.spec.serializers;

import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BigIntDataJsonSerializerTest {
    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serialize() throws JsonProcessingException {
        BigIntPlutusData bigIntPlutusData = BigIntPlutusData.of(new BigInteger("20000"));
        JsonNode expected = JsonUtil.parseJson("{\n" +
                "   \"int\": 20000\n" +
                "}");

        String json = JsonUtil.getPrettyJson(bigIntPlutusData);
        System.out.println(json);
        JsonNode actualJsonNode = JsonUtil.parseJson(json);

        assertThat(actualJsonNode).isEqualTo(expected);
    }

    @Test
    void deserialize() throws JsonProcessingException {
        String json = "{\n" +
                "   \"int\": 20000\n" +
                "}";
        BigIntPlutusData bigIntPlutusData = objectMapper.readValue(json, BigIntPlutusData.class);

        assertThat(bigIntPlutusData.getValue()).isEqualTo(BigInteger.valueOf(20000));
    }
}
