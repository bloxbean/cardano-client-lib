package com.bloxbean.cardano.client.plutus.spec.serializers;

import co.nstant.in.cbor.model.Map;
import com.bloxbean.cardano.client.common.cbor.CborSerializationUtil;
import com.bloxbean.cardano.client.plutus.spec.BigIntPlutusData;
import com.bloxbean.cardano.client.plutus.spec.BytesPlutusData;
import com.bloxbean.cardano.client.plutus.spec.ListPlutusData;
import com.bloxbean.cardano.client.plutus.spec.MapPlutusData;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MapDataJsonSerializerTest {
    ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializeAndDeserialize() throws JsonProcessingException {
        MapPlutusData mapPlutusData = new MapPlutusData();
        mapPlutusData.put(BigIntPlutusData.of(1000), BigIntPlutusData.of(2000));
        mapPlutusData.put(BigIntPlutusData.of(3), BytesPlutusData.of("Hello"));
        mapPlutusData.put(ListPlutusData.of(BigIntPlutusData.of(1), BytesPlutusData.of("key")), BytesPlutusData.of("World"));

        String expectedJson = "{\n" +
                "  \"map\": [\n" +
                "    {\n" +
                "      \"k\": {\n" +
                "        \"list\": [\n" +
                "          {\n" +
                "            \"int\": 1\n" +
                "          },\n" +
                "          {\n" +
                "            \"bytes\": \"6b6579\"\n" +
                "          }\n" +
                "        ]\n" +
                "      },\n" +
                "      \"v\": {\n" +
                "        \"bytes\": \"576f726c64\"\n" +
                "      }\n" +
                "    },\n" +
                "    {\n" +
                "      \"k\": {\n" +
                "        \"int\": 3\n" +
                "      },\n" +
                "      \"v\": {\n" +
                "        \"bytes\": \"48656c6c6f\"\n" +
                "      }\n" +
                "    },\n" +
                "    {\n" +
                "      \"k\": {\n" +
                "        \"int\": 1000\n" +
                "      },\n" +
                "      \"v\": {\n" +
                "        \"int\": 2000\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        MapPlutusData mapPlutusData2 = (MapPlutusData) mapper.readValue(expectedJson, MapPlutusData.class);
        assertThat(mapPlutusData).isEqualTo(mapPlutusData2);

    }

    @Test
    void serDeser_from_cbor() throws Exception {
        String cborHex = "a3034548656c6c6f1903e81907d09f01436b6579ff45576f726c64";
        MapPlutusData mapPlutusData = MapPlutusData.deserialize((Map) CborSerializationUtil.deserialize(HexUtil.decodeHexString(cborHex)));

        String json = mapper.writeValueAsString(mapPlutusData);
        MapPlutusData mapPlutusData2 = mapper.readValue(json, MapPlutusData.class);
        String cborHex2 = mapPlutusData2.serializeToHex();

        assertThat(cborHex2).isEqualTo(cborHex);
    }
}
