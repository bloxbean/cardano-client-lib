package com.bloxbean.cardano.client.plutus.spec.serializers;

import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ConstrPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConstrDataJsonSerializerTest {
    ObjectMapper mapper = new ObjectMapper();

    @Test
    void serialize() throws JsonProcessingException {
        ConstrPlutusData constrPlutusData = ConstrPlutusData.of(0,
                BigIntPlutusData.of(100),
                BytesPlutusData.of("Hello"),
                ListPlutusData.of(BigIntPlutusData.of(2000), BytesPlutusData.of("World")));

        System.out.println(constrPlutusData.serializeToHex());

        String json = JsonUtil.getPrettyJson(constrPlutusData);
        System.out.println(json);
        JsonNode jsonNode = JsonUtil.parseJson(json);

        JsonNode expectedJson = JsonUtil.parseJson("{\"constructor\":0," +
                "\"fields\":[" +
                "{\"int\":100}," +
                "{\"bytes\":\"48656c6c6f\"}," +
                "{\"list\":" +
                "[{\"int\":2000},{\"bytes\":\"576f726c64\"}]}]" +
                "}");

        assertThat(jsonNode).isEqualTo(expectedJson);
    }

    @Test
    void deserialize() throws JsonProcessingException {
        String json = "{\n" +
                "   \"constructor\": 2,\n" +
                "   \"fields\": [\n" +
                "      {\n" +
                "         \"int\": 100\n" +
                "      },\n" +
                "      {\n" +
                "         \"bytes\": \"48656c6c6f\"\n" +
                "      },\n" +
                "      {\"list\": [\n" +
                "         {\n" +
                "            \"int\": 2000\n" +
                "         },\n" +
                "         {\n" +
                "            \"bytes\": \"576f726c64\"\n" +
                "         }\n" +
                "      ]}\n" +
                "   ]\n" +
                "}";

        ConstrPlutusData constrPlutusData = mapper.readValue(json, ConstrPlutusData.class);

        ConstrPlutusData expectedConstrPlutusData = ConstrPlutusData.of(2,
                BigIntPlutusData.of(100),
                BytesPlutusData.of("Hello"),
                ListPlutusData.of(BigIntPlutusData.of(2000), BytesPlutusData.of("World")));

        assertThat(constrPlutusData).isEqualTo(expectedConstrPlutusData);
    }

}
