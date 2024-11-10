package com.bloxbean.cardano.client.metadata;

import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MetadataBuilderTest {

    @Test
    void testJsonBodyNotObjectNorArray() {
        String jsonBody = "\"simpleValue\"";
        BigInteger label = new BigInteger("1");
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            MetadataBuilder.metadataFromJsonBody(label, jsonBody);
        });
    }

    @Test
    void testJsonBodyInvalidJsonFormat() {
        String jsonBody = "InvalidJson}";
        BigInteger label = new BigInteger("1");
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            MetadataBuilder.metadataFromJsonBody(label, jsonBody);
        });
    }

    @Test
    void testObjectNodeBody() {
        String json = loadJsonBodyFromFile("metadata-jsonbody-1.json");

        var metadata = MetadataBuilder.metadataFromJsonBody(new BigInteger("1557"), json);
        System.out.println(metadata);
        System.out.println(HexUtil.encodeHexString(metadata.serialize()));

        assertThat(metadata).isNotNull();
        var metadataMap = (MetadataMap) metadata.get(BigInteger.valueOf(1557));
        assertThat(metadataMap.get("metadata")).isNotNull();
        assertThat(((MetadataMap)metadataMap.get("metadata")).get("creation_slot")).isEqualTo("10278");
        assertThat(((MetadataMap)metadataMap.get("metadata")).get("version")).isEqualTo("1.0");

        assertThat(((MetadataMap)metadataMap.get("org")).get("id")).isEqualTo("55f95560c1d883ee7628993da5adf725a5d97a13929fd4f477be0faf5020ca94");
        assertThat(((MetadataMap)metadataMap.get("org")).get("amount")).isEqualTo(new BigInteger("12345678901234567890123456789012345"));
    }

    @Test
    void testObjectNodeBodyRoundTrip() {
        String json = loadJsonBodyFromFile("metadata-jsonbody-1.json");

        var metadata = MetadataBuilder.metadataFromJsonBody(new BigInteger("1557"), json);
        System.out.println(metadata);
        System.out.println(HexUtil.encodeHexString(metadata.serialize()));

        var jsonNode = MetadataBuilder.toJson(metadata);

        assertThat(jsonNode).isNotNull();
        var metadataMap = (MetadataMap) metadata.get(BigInteger.valueOf(1557));
        assertThat(metadataMap.get("metadata")).isNotNull();
        assertThat(((MetadataMap)metadataMap.get("metadata")).get("creation_slot")).isEqualTo("10278");
        assertThat(((MetadataMap)metadataMap.get("metadata")).get("version")).isEqualTo("1.0");

        assertThat(((MetadataMap)metadataMap.get("org")).get("id")).isEqualTo("55f95560c1d883ee7628993da5adf725a5d97a13929fd4f477be0faf5020ca94");
        assertThat(((MetadataMap)metadataMap.get("org")).get("amount")).isEqualTo(new BigInteger("12345678901234567890123456789012345"));
    }

    @Test
    void testMetadataFromJson() throws IOException {
        String json = loadJsonMetadata("json-1").toString();
        Metadata metadata = MetadataBuilder.metadataFromJson(json);

        assertNotNull(metadata);

        byte[] serializedBytes = metadata.serialize();
        String hex = HexUtil.encodeHexString(serializedBytes);

        var deMetadata = MetadataBuilder.deserialize(serializedBytes);

        Collection<DataItem> keys = deMetadata.getData().getKeys();

        AssertionsForInterfaceTypes.assertThat(keys).containsExactly(new UnsignedInteger(945845007538436815L), new UnsignedInteger(1302243434517352162L), new UnsignedInteger(1351859328329939190L),
                new UnsignedInteger(7274669146951118819L), new UnsignedInteger(7505166164059511819L), new UnsignedInteger(new BigInteger("17802948329108123211")));
        String expected = "a61b0d205105e6e7bacf504ebc4ea3b43bb0cc76bb326f17a30d8f1b12127f810d7dcee28264554a42333be153691687de9f671b12c2c4e58b6778f6a26430783065463bdefda922656830783134666638643bb6597a178e6a18971b64f4d10bda83efe33bcd995b2806a1d9971b6827b4dcb50c5c0b71726365486c5578586c576d5a4a637859641bf710c72e671fae4ba0";
        assertEquals(expected, hex);
    }

    @Test
    void testMetadataFromJsonWith2LevelNestedCollection() throws IOException {
        String json = loadJsonMetadata("json-2").toString();

        Metadata metadata = MetadataBuilder.metadataFromJson(json);

        System.out.println(HexUtil.encodeHexString(metadata.serialize()));
        assertNotNull(metadata);
    }

    @Test
    void testMetadataMapFromJsonBody() {
        String json = loadJsonBodyFromFile("metadata-jsonbody-1.json");

        MetadataMap metadataMap = MetadataBuilder.metadataMapFromJsonBody(json);

        assertThat(metadataMap).isNotNull();
        assertThat(metadataMap.get("metadata")).isNotNull();
        assertThat(((MetadataMap)metadataMap.get("metadata")).get("creation_slot")).isEqualTo("10278");
        assertThat(((MetadataMap)metadataMap.get("metadata")).get("version")).isEqualTo("1.0");

        assertThat(((MetadataMap)metadataMap.get("org")).get("id")).isEqualTo("55f95560c1d883ee7628993da5adf725a5d97a13929fd4f477be0faf5020ca94");
        assertThat(((MetadataMap)metadataMap.get("org")).get("amount")).isEqualTo(new BigInteger("12345678901234567890123456789012345"));
    }

    @Test
    void testMetadataListFromJsonBody() {
        String json = loadJsonBodyFromFile("metadata-jsonbody-2.json");

        MetadataList metadataList = MetadataBuilder.metadataListFromJsonBody(json);

        assertThat(metadataList).isNotNull();
        assertThat(metadataList.size()).isEqualTo(2);
    }

    @Test
    void testMetadataMapFromJsonBody_withLongString() {
        String json = loadJsonBodyFromFile("metadata-jsonbody-3.json");

        MetadataMap metadataMap = MetadataBuilder.metadataMapFromJsonBody(json);

        assertThat(metadataMap).isNotNull();
        assertThat(metadataMap.get("date")).isEqualTo("2023-10-03");
        assertThat(metadataMap.get("number")).isEqualTo(BigInteger.valueOf(5678888));
        assertThat(metadataMap.get("batch_id")).isEqualTo("2c45663f4ab1b7b709ecdf9a6376f9caf0ebce5ebe21a4917519f1af24dd4852");
        assertThat(metadataMap.get("id")).isEqualTo("48335c2b63cffcef2a3cd0678b65c4fb16420f51110033024209957fbd58ec4e");
        assertThat(metadataMap.get("types")).isInstanceOf(MetadataList.class);
        assertThat(((MetadataList)metadataMap.get("types")).getValueAt(0)).isEqualTo("abc");
        assertThat(((MetadataList)metadataMap.get("types")).getValueAt(1)).isEqualTo("xyz");
        assertThat(((MetadataList)metadataMap.get("description"))).isInstanceOf(MetadataList.class);
        assertThat(((MetadataList)metadataMap.get("description")).size()).isEqualTo(2);
    }

    @Test
    void testToJson() throws JsonProcessingException {
        String cborHex = "a61bf710c72e671fae4ba01b0d205105e6e7bacf504ebc4ea3b43bb0cc76bb326f17a30d8f1b12c2c4e58b6778f6a36430783065463bdefda922656830783134666638643bb6597a178e6a18976a6e65737465642d6d6170a366307830616161463bdefda922656830783131666638641904d26b6e65737465642d6c69737482624e31187b1b6827b4dcb50c5c0b71726365486c5578586c576d5a4a637859641b64f4d10bda83efe33bcd995b2806a1d9971b12127f810d7dcee28364554a42333be153691687de9f67a2646b65793175616e6f746865722d6e6574737465642d76616c7565646b6579321911d6";

        String json = MetadataBuilder.toJson(HexUtil.decodeHexString(cborHex));

        ObjectMapper objectMapper = new ObjectMapper();
        Map map = objectMapper.readValue(json, Map.class);

        assertNotNull(map);
    }

    private String loadJsonBodyFromFile(String fileName) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("File not found: " + fileName);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Error reading file: " + fileName, e);
        }
    }

    private JsonNode loadJsonMetadata(String key) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(this.getClass().getClassLoader().getResourceAsStream("json-metadata.json"));
        ObjectNode root = (ObjectNode)rootNode;

        return root.get(key);
    }

}
