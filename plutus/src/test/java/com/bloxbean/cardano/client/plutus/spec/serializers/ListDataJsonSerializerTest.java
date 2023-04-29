package com.bloxbean.cardano.client.plutus.spec.serializers;

import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ListDataJsonSerializerTest {
    ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serialize() throws JsonProcessingException {
        ListPlutusData listPlutusData = ListPlutusData.of(BytesPlutusData.of("Hello"), BigIntPlutusData.of(10000), BytesPlutusData.of("World"));

        String json = JsonUtil.getPrettyJson(listPlutusData);
        JsonNode jsonNode = JsonUtil.parseJson(json);

        JsonNode expectedJson = JsonUtil.parseJson("{\"list\":[{\"bytes\":\"48656c6c6f\"},{\"int\":10000},{\"bytes\":\"576f726c64\"}]}");

        assertThat(jsonNode).isEqualTo(expectedJson);
    }

    @Test
    void deserialize() throws JsonProcessingException {
        String json = "{\"list\":[{\"bytes\":\"48656c6c6f\"},{\"int\":10000},{\"bytes\":\"576f726c64\"}]}";

        ListPlutusData listPlutusData = objectMapper.readValue(json, ListPlutusData.class);

        assertThat(listPlutusData.getPlutusDataList()).hasSize(3);
        assertThat(listPlutusData.getPlutusDataList().get(0)).isEqualTo(BytesPlutusData.of("Hello"));
        assertThat(listPlutusData.getPlutusDataList().get(1)).isEqualTo(BigIntPlutusData.of(10000));
        assertThat(listPlutusData.getPlutusDataList().get(2)).isEqualTo(BytesPlutusData.of("World"));
    }
}
