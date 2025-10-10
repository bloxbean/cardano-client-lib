package com.bloxbean.cardano.client.quicktx;

import com.bloxbean.cardano.client.spec.UnitInterval;
import com.bloxbean.cardano.client.transaction.spec.cert.*;
import com.bloxbean.cardano.client.quicktx.intent.PoolRegistrationIntent;
import com.bloxbean.cardano.client.quicktx.serialization.TxPlan;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test PoolRegistrationIntent serialization and round-trip functionality.
 * Ensures proper JSON/YAML serialization for transaction plans.
 */
class PoolRegistrationIntentTest {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Test
    void testPoolRegistrationFactoryMethod() {
        // Given
        PoolRegistration poolReg = createSamplePoolRegistration();

        // When
        PoolRegistrationIntent intent = PoolRegistrationIntent.register(poolReg);

        // Then
        assertNotNull(intent);
        assertEquals("pool_registration", intent.getType());
        assertFalse(intent.isUpdate());
        assertEquals(poolReg, intent.getPoolRegistration());
    }

    @Test
    void testPoolUpdateFactoryMethod() {
        // Given
        PoolRegistration poolReg = createSamplePoolRegistration();

        // When
        PoolRegistrationIntent intent = PoolRegistrationIntent.update(poolReg);

        // Then
        assertNotNull(intent);
        assertEquals("pool_update", intent.getType());
        assertTrue(intent.isUpdate());
        assertEquals(poolReg, intent.getPoolRegistration());
    }

    @Test
    void testPoolRegistrationIntentValidation() {
        // Given - Empty intent
        PoolRegistrationIntent intent = new PoolRegistrationIntent();

        // Then - Should fail without pool registration
        assertThrows(IllegalStateException.class, intent::validate);

        // Given - Intent with pool registration
        intent.setPoolRegistration(createSamplePoolRegistration());

        // Then - Should pass validation
        assertDoesNotThrow(intent::validate);
    }

    @Test
    void testPoolRegistrationIntentJsonSerialization() throws Exception {
        // Given
        PoolRegistration poolReg = createSamplePoolRegistration();
        PoolRegistrationIntent intent = PoolRegistrationIntent.register(poolReg);

        // When
        String json = JsonUtil.getPrettyJson(intent);
        System.out.println("PoolRegistrationIntent JSON:");
        System.out.println(json);

        // Then - Verify JSON structure
        assertThat(json).contains("\"type\" : \"pool_registration\"");
        assertThat(json).contains("\"is_update\" : false");
        assertThat(json).contains("\"pool_registration\"");
        // Verify nested PoolRegistration serialization
        assertThat(json).contains("ed40b0a319f639a70b1e2a4de00f112c4f7b7d4849f0abd25c4336a4");
        assertThat(json).contains("single_host_addr");
    }

    @Test
    void testPoolUpdateIntentJsonSerialization() throws Exception {
        // Given
        PoolRegistration poolReg = createSamplePoolRegistration();
        PoolRegistrationIntent intent = PoolRegistrationIntent.update(poolReg);

        // When
        String json = JsonUtil.getPrettyJson(intent);
        System.out.println("PoolUpdateIntent JSON:");
        System.out.println(json);

        // Then
        assertThat(json).contains("\"type\" : \"pool_update\"");
        assertThat(json).contains("\"is_update\" : true");
        assertThat(json).contains("\"pool_registration\"");
    }

    @Test
    void testPoolRegistrationIntentJsonRoundTrip() throws Exception {
        // Given
        PoolRegistration poolReg = createSamplePoolRegistration();
        PoolRegistrationIntent original = PoolRegistrationIntent.register(poolReg);

        // When - Serialize to JSON
        String json = JSON_MAPPER.writeValueAsString(original);

        // Then - Deserialize back
        PoolRegistrationIntent restored = JSON_MAPPER.readValue(json, PoolRegistrationIntent.class);

        // Verify all fields match
        assertNotNull(restored);
        assertEquals("pool_registration", restored.getType());
        assertFalse(restored.isUpdate());

        PoolRegistration restoredPool = restored.getPoolRegistration();
        assertNotNull(restoredPool);
        assertArrayEquals(poolReg.getOperator(), restoredPool.getOperator());
        assertArrayEquals(poolReg.getVrfKeyHash(), restoredPool.getVrfKeyHash());
        assertEquals(poolReg.getPledge(), restoredPool.getPledge());
        assertEquals(poolReg.getCost(), restoredPool.getCost());
        assertEquals(poolReg.getMargin(), restoredPool.getMargin());
        assertEquals(poolReg.getRewardAccount(), restoredPool.getRewardAccount());
        assertEquals(poolReg.getPoolOwners(), restoredPool.getPoolOwners());
        assertEquals(poolReg.getPoolMetadataUrl(), restoredPool.getPoolMetadataUrl());
        assertEquals(poolReg.getPoolMetadataHash(), restoredPool.getPoolMetadataHash());
        assertThat(restoredPool.getRelays()).hasSize(1);
    }

    @Test
    void testPoolUpdateIntentJsonRoundTrip() throws Exception {
        // Given
        PoolRegistration poolReg = createSamplePoolRegistration();
        PoolRegistrationIntent original = PoolRegistrationIntent.update(poolReg);

        // When
        String json = JSON_MAPPER.writeValueAsString(original);
        PoolRegistrationIntent restored = JSON_MAPPER.readValue(json, PoolRegistrationIntent.class);

        // Then
        assertEquals("pool_update", restored.getType());
        assertTrue(restored.isUpdate());
        assertNotNull(restored.getPoolRegistration());
    }

    @Test
    void testTypeInferenceFromJson() throws Exception {
        // Given - JSON with type: pool_registration
        String registrationJson = "{\n" +
            "  \"type\": \"pool_registration\",\n" +
            "  \"pool_registration\": {\n" +
            "    \"operator\": \"ed40b0a319f639a70b1e2a4de00f112c4f7b7d4849f0abd25c4336a4\",\n" +
            "    \"vrfKeyHash\": \"b95af7a0a58928fbd0e73b03ce81dedd42d4a776685b443cf2016c18438a3b9b\",\n" +
            "    \"pledge\": 6600000000000,\n" +
            "    \"cost\": 500000000,\n" +
            "    \"margin\": {\n" +
            "      \"numerator\": 13,\n" +
            "      \"denominator\": 1000\n" +
            "    },\n" +
            "    \"rewardAccount\": \"e1f3c3d69b1d4eca197096cbfd67450f64123de4a5ed61b1f94a356134\",\n" +
            "    \"poolOwners\": [\"f3c3d69b1d4eca197096cbfd67450f64123de4a5ed61b1f94a356134\"],\n" +
            "    \"relays\": []\n" +
            "  }\n" +
            "}";

        // When
        PoolRegistrationIntent registration = JSON_MAPPER.readValue(registrationJson, PoolRegistrationIntent.class);

        // Then
        assertFalse(registration.isUpdate());
        assertEquals("pool_registration", registration.getType());

        // Given - JSON with type: pool_update
        String updateJson = "{\n" +
            "  \"type\": \"pool_update\",\n" +
            "  \"pool_registration\": {\n" +
            "    \"operator\": \"ed40b0a319f639a70b1e2a4de00f112c4f7b7d4849f0abd25c4336a4\",\n" +
            "    \"vrfKeyHash\": \"b95af7a0a58928fbd0e73b03ce81dedd42d4a776685b443cf2016c18438a3b9b\",\n" +
            "    \"pledge\": 6600000000000,\n" +
            "    \"cost\": 500000000,\n" +
            "    \"margin\": {\n" +
            "      \"numerator\": 13,\n" +
            "      \"denominator\": 1000\n" +
            "    },\n" +
            "    \"rewardAccount\": \"e1f3c3d69b1d4eca197096cbfd67450f64123de4a5ed61b1f94a356134\",\n" +
            "    \"poolOwners\": [\"f3c3d69b1d4eca197096cbfd67450f64123de4a5ed61b1f94a356134\"],\n" +
            "    \"relays\": []\n" +
            "  }\n" +
            "}";

        // When
        PoolRegistrationIntent update = JSON_MAPPER.readValue(updateJson, PoolRegistrationIntent.class);

        // Then
        assertTrue(update.isUpdate());
        assertEquals("pool_update", update.getType());
    }

    @Test
    void testPoolRegistrationInTxYamlSerialization() {
        // Given
        PoolRegistration poolReg = createSamplePoolRegistration();
        Tx tx = new Tx()
            .from("addr_test1qz2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3jcu5d8ps7zex2k2xt3uqxgjqnnj83ws8lhrn648jjxtwq2ytjqp")
            .registerPool(poolReg);

        // When
        String yaml = TxPlan.from(tx).toYaml();
        System.out.println("Pool Registration Tx YAML:");
        System.out.println(yaml);

        // Then
        assertThat(yaml).contains("version: 1.0");
        assertThat(yaml).contains("transaction:");
        assertThat(yaml).contains("type: pool_registration");
        assertThat(yaml).contains("pool_registration:");
        assertThat(yaml).contains("operator:");
        assertThat(yaml).contains("ed40b0a319f639a70b1e2a4de00f112c4f7b7d4849f0abd25c4336a4");
    }

    @Test
    void testPoolUpdateInTxYamlSerialization() {
        // Given
        PoolRegistration poolReg = createSamplePoolRegistration();
        Tx tx = new Tx()
            .from("addr_test1qz2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3jcu5d8ps7zex2k2xt3uqxgjqnnj83ws8lhrn648jjxtwq2ytjqp")
            .updatePool(poolReg);

        // When
        String yaml = TxPlan.from(tx).toYaml();
        System.out.println("Pool Update Tx YAML:");
        System.out.println(yaml);

        // Then
        assertThat(yaml).contains("version: 1.0");
        assertThat(yaml).contains("type: pool_update");
        assertThat(yaml).contains("pool_registration:");
    }

    @Test
    void testPoolRegistrationYamlRoundTrip() {
        // Given
        PoolRegistration poolReg = createSamplePoolRegistration();
        Tx original = new Tx()
            .from("addr_test1qz2fxv2umyhttkxyxp8x0dlpdt3k6cwng5pxj3jhsydzer3jcu5d8ps7zex2k2xt3uqxgjqnnj83ws8lhrn648jjxtwq2ytjqp")
            .registerPool(poolReg);

        // When - Serialize to YAML and back
        String yaml = TxPlan.from(original).toYaml();
        List<AbstractTx<?>> restored = TxPlan.getTxs(yaml);

        // Then
        assertThat(restored).hasSize(1);
        Tx restoredTx = (Tx) restored.get(0);
        assertThat(restoredTx.getIntentions()).hasSize(1);

        PoolRegistrationIntent restoredIntent = (PoolRegistrationIntent) restoredTx.getIntentions().get(0);
        assertEquals("pool_registration", restoredIntent.getType());
        assertFalse(restoredIntent.isUpdate());

        PoolRegistration restoredPool = restoredIntent.getPoolRegistration();
        assertNotNull(restoredPool);
        assertArrayEquals(poolReg.getOperator(), restoredPool.getOperator());
        assertEquals(poolReg.getPledge(), restoredPool.getPledge());
        assertEquals(poolReg.getCost(), restoredPool.getCost());
    }

    // Helper method to create a sample PoolRegistration
    private PoolRegistration createSamplePoolRegistration() {
        try {
            byte[] operator = HexUtil.decodeHexString("ed40b0a319f639a70b1e2a4de00f112c4f7b7d4849f0abd25c4336a4");
            byte[] vrfKeyHash = HexUtil.decodeHexString("b95af7a0a58928fbd0e73b03ce81dedd42d4a776685b443cf2016c18438a3b9b");

            SingleHostAddr relay = SingleHostAddr.builder()
                .port(3001)
                .ipv4((Inet4Address) Inet4Address.getByName("54.177.41.35"))
                .build();

            return PoolRegistration.builder()
                .operator(operator)
                .vrfKeyHash(vrfKeyHash)
                .pledge(BigInteger.valueOf(6600000000000L))
                .cost(BigInteger.valueOf(500000000))
                .margin(new UnitInterval(BigInteger.valueOf(13), BigInteger.valueOf(1000)))
                .rewardAccount("e1f3c3d69b1d4eca197096cbfd67450f64123de4a5ed61b1f94a356134")
                .poolOwners(Set.of("f3c3d69b1d4eca197096cbfd67450f64123de4a5ed61b1f94a356134"))
                .relays(Arrays.asList(relay))
                .poolMetadataUrl("https://git.io/JttTl")
                .poolMetadataHash("51700f7e33476a20b6e5a3f681e31d2cf0d8e706393f45912a5dbe3a8d7edd41")
                .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create sample pool registration", e);
        }
    }
}
