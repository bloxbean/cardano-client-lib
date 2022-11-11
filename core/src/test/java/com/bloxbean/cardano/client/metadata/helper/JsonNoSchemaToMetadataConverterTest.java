package com.bloxbean.cardano.client.metadata.helper;

import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JsonNoSchemaToMetadataConverterTest {

    String dataFile = "json-metadata.json";

    @Test
    void testParseJSONMetadata() throws IOException {
        String json = loadJsonMetadata("json-1").toString();
        Metadata metadata = JsonNoSchemaToMetadataConverter.jsonToCborMetadata(json);

        assertNotNull(metadata);

        byte[] serializedBytes = metadata.serialize();
        String hex = Hex.toHexString(serializedBytes);

        String expected = "a61bf710c72e671fae4ba01b0d205105e6e7bacf504ebc4ea3b43bb0cc76bb326f17a30d8f1b12c2c4e58b6778f6a26430783065463bdefda922656830783134666638643bb6597a178e6a18971b12127f810d7dcee28264554a42333be153691687de9f671b64f4d10bda83efe33bcd995b2806a1d9971b6827b4dcb50c5c0b71726365486c5578586c576d5a4a63785964";
        assertEquals(expected, hex);
    }

    @Test
    void testParseJSONMetadataWith2LevelNestedCollection() throws IOException {
        String json = loadJsonMetadata("json-2").toString();
        System.out.println(json);

        Metadata metadata = JsonNoSchemaToMetadataConverter.jsonToCborMetadata(json);

        System.out.println(HexUtil.encodeHexString(metadata.serialize()));
        assertNotNull(metadata);
    }

    private JsonNode loadJsonMetadata(String key) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(this.getClass().getClassLoader().getResourceAsStream(dataFile));
        ObjectNode root = (ObjectNode)rootNode;

        return root.get(key);
    }
}
