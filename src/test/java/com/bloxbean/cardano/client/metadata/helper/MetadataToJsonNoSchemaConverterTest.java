package com.bloxbean.cardano.client.metadata.helper;

import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MetadataToJsonNoSchemaConverterTest {

    @Test
    void testCborBytesToJson() throws JsonProcessingException {
        String cborHex = "a61bf710c72e671fae4ba01b0d205105e6e7bacf504ebc4ea3b43bb0cc76bb326f17a30d8f1b12c2c4e58b6778f6a26430783065463bdefda922656830783134666638643bb6597a178e6a18971b6827b4dcb50c5c0b71726365486c5578586c576d5a4a637859641b64f4d10bda83efe33bcd995b2806a1d9971b12127f810d7dcee28264554a42333be153691687de9f67";

        String json = MetadataToJsonNoSchemaConverter.cborBytesToJson(HexUtil.decodeHexString(cborHex));

        ObjectMapper objectMapper = new ObjectMapper();
        Map map = objectMapper.readValue(json, Map.class);
        System.out.println(JsonUtil.getPrettyJson(map));

        assertNotNull(map);
        assertEquals("0x3bdefda92265", ((Map)map.get("1351859328329939190")).get("0x0e"));
        assertEquals("rceHlUxXlWmZJcxYd", map.get("7505166164059511819"));
        assertEquals("UJB3", ((List)map.get("1302243434517352162")).get(0));
        assertEquals(new BigInteger("-16236436627090481000"), ((List)map.get("1302243434517352162")).get(1));
    }

    @Test
    void testCborHexToJson() throws JsonProcessingException {
        String cborHex = "a61bf710c72e671fae4ba01b0d205105e6e7bacf504ebc4ea3b43bb0cc76bb326f17a30d8f1b12c2c4e58b6778f6a26430783065463bdefda922656830783134666638643bb6597a178e6a18971b6827b4dcb50c5c0b71726365486c5578586c576d5a4a637859641b64f4d10bda83efe33bcd995b2806a1d9971b12127f810d7dcee28264554a42333be153691687de9f67";

        String json = MetadataToJsonNoSchemaConverter.cborHexToJson(cborHex);

        ObjectMapper objectMapper = new ObjectMapper();
        Map map = objectMapper.readValue(json, Map.class);
        System.out.println(JsonUtil.getPrettyJson(map));

        assertNotNull(map);
        assertEquals("0x3bdefda92265", ((Map)map.get("1351859328329939190")).get("0x0e"));
        assertEquals("rceHlUxXlWmZJcxYd", map.get("7505166164059511819"));
        assertEquals("UJB3", ((List)map.get("1302243434517352162")).get(0));
        assertEquals(new BigInteger("-16236436627090481000"), ((List)map.get("1302243434517352162")).get(1));
    }

    @Test
    void testCborHexToJson2() throws JsonProcessingException {
        String cborHex = "a61bf710c72e671fae4ba01b0d205105e6e7bacf504ebc4ea3b43bb0cc76bb326f17a30d8f1b12c2c4e58b6778f6a36430783065463bdefda922656830783134666638643bb6597a178e6a18976a6e65737465642d6d6170a366307830616161463bdefda922656830783131666638641904d26b6e65737465642d6c69737482624e31187b1b6827b4dcb50c5c0b71726365486c5578586c576d5a4a637859641b64f4d10bda83efe33bcd995b2806a1d9971b12127f810d7dcee28364554a42333be153691687de9f67a2646b65793175616e6f746865722d6e6574737465642d76616c7565646b6579321911d6";

        String json = MetadataToJsonNoSchemaConverter.cborHexToJson(cborHex);

        ObjectMapper objectMapper = new ObjectMapper();
        Map map = objectMapper.readValue(json, Map.class);
        System.out.println(JsonUtil.getPrettyJson(map));

        assertNotNull(map);
    }
}
