package com.bloxbean.cardano.client.transaction.spec.cert;

import com.bloxbean.cardano.client.spec.UnitInterval;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.client.util.JsonUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test JSON serialization for PoolRegistration and related classes.
 * Ensures backward compatibility with CBOR while adding JSON support.
 */
class PoolRegistrationJsonTest {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Test
    void testJsonRoundTrip_withAllRelayTypes() throws Exception {
        // Given - Create a pool registration with all relay types
        byte[] operator = HexUtil.decodeHexString("ed40b0a319f639a70b1e2a4de00f112c4f7b7d4849f0abd25c4336a4");
        byte[] vrfKeyHash = HexUtil.decodeHexString("b95af7a0a58928fbd0e73b03ce81dedd42d4a776685b443cf2016c18438a3b9b");

        SingleHostAddr relay1 = SingleHostAddr.builder()
                .port(3001)
                .ipv4((Inet4Address) Inet4Address.getByName("54.177.41.35"))
                .build();

        SingleHostName relay2 = SingleHostName.builder()
                .port(3002)
                .dnsName("relay.example.com")
                .build();

        MultiHostName relay3 = MultiHostName.builder()
                .dnsName("multi.example.com")
                .build();

        PoolRegistration poolReg = PoolRegistration.builder()
                .operator(operator)
                .vrfKeyHash(vrfKeyHash)
                .pledge(BigInteger.valueOf(6600000000000L))
                .cost(BigInteger.valueOf(500000000))
                .margin(new UnitInterval(BigInteger.valueOf(13), BigInteger.valueOf(1000)))
                .rewardAccount("e1f3c3d69b1d4eca197096cbfd67450f64123de4a5ed61b1f94a356134")
                .poolOwners(Set.of("f3c3d69b1d4eca197096cbfd67450f64123de4a5ed61b1f94a356134"))
                .relays(Arrays.asList(relay1, relay2, relay3))
                .poolMetadataUrl("https://git.io/JttTl")
                .poolMetadataHash("51700f7e33476a20b6e5a3f681e31d2cf0d8e706393f45912a5dbe3a8d7edd41")
                .build();

        // When - Serialize to JSON
        String json = JsonUtil.getPrettyJson(poolReg);
        System.out.println("JSON: " + json);

        // Then - JSON contains hex-encoded byte arrays
        assertThat(json).contains("ed40b0a319f639a70b1e2a4de00f112c4f7b7d4849f0abd25c4336a4");
        assertThat(json).contains("b95af7a0a58928fbd0e73b03ce81dedd42d4a776685b443cf2016c18438a3b9b");
        assertThat(json).contains("single_host_addr");
        assertThat(json).contains("single_host_name");
        assertThat(json).contains("multi_host_name");
        assertThat(json).contains("54.177.41.35");
        assertThat(json).contains("relay.example.com");

        // When - Deserialize back from JSON
        PoolRegistration deserialized = JSON_MAPPER.readValue(json, PoolRegistration.class);

        // Then - All fields match
        assertThat(deserialized.getOperator()).isEqualTo(operator);
        assertThat(deserialized.getVrfKeyHash()).isEqualTo(vrfKeyHash);
        assertThat(deserialized.getPledge()).isEqualTo(poolReg.getPledge());
        assertThat(deserialized.getCost()).isEqualTo(poolReg.getCost());
        assertThat(deserialized.getMargin()).isEqualTo(poolReg.getMargin());
        assertThat(deserialized.getRewardAccount()).isEqualTo(poolReg.getRewardAccount());
        assertThat(deserialized.getPoolOwners()).isEqualTo(poolReg.getPoolOwners());
        assertThat(deserialized.getPoolMetadataUrl()).isEqualTo(poolReg.getPoolMetadataUrl());
        assertThat(deserialized.getPoolMetadataHash()).isEqualTo(poolReg.getPoolMetadataHash());

        // Verify relays
        assertThat(deserialized.getRelays()).hasSize(3);
        assertThat(deserialized.getRelays().get(0)).isInstanceOf(SingleHostAddr.class);
        assertThat(deserialized.getRelays().get(1)).isInstanceOf(SingleHostName.class);
        assertThat(deserialized.getRelays().get(2)).isInstanceOf(MultiHostName.class);

        SingleHostAddr deserRelay1 = (SingleHostAddr) deserialized.getRelays().get(0);
        assertThat(deserRelay1.getPort()).isEqualTo(3001);
        assertThat(deserRelay1.getIpv4().getHostAddress()).isEqualTo("54.177.41.35");
    }

    @Test
    void testSingleHostAddrWithIpv6() throws Exception {
        // Given
        SingleHostAddr relay = SingleHostAddr.builder()
                .port(3001)
                .ipv4((Inet4Address) Inet4Address.getByName("192.168.1.1"))
                .ipv6((Inet6Address) Inet6Address.getByName("2001:db8::1"))
                .build();

        // When
        String json = JSON_MAPPER.writeValueAsString(relay);
        System.out.println("SingleHostAddr JSON: " + json);

        // Then
        assertThat(json).contains("192.168.1.1");
        assertThat(json).contains("2001:db8:0:0:0:0:0:1"); // Java formats IPv6

        // When - Deserialize
        SingleHostAddr deserialized = JSON_MAPPER.readValue(json, SingleHostAddr.class);

        // Then
        assertThat(deserialized.getPort()).isEqualTo(3001);
        assertThat(deserialized.getIpv4()).isNotNull();
        assertThat(deserialized.getIpv6()).isNotNull();
        assertThat(deserialized.getIpv4().getHostAddress()).isEqualTo("192.168.1.1");
    }

    @Test
    void testNullByteArrays() throws Exception {
        // Given - PoolRegistration with null operator and vrfKeyHash
        PoolRegistration poolReg = PoolRegistration.builder()
                .pledge(BigInteger.valueOf(1000000000))
                .cost(BigInteger.valueOf(340000000))
                .margin(new UnitInterval(BigInteger.valueOf(1), BigInteger.valueOf(100)))
                .rewardAccount("e1f3c3d69b1d4eca197096cbfd67450f64123de4a5ed61b1f94a356134")
                .poolOwners(Set.of("f3c3d69b1d4eca197096cbfd67450f64123de4a5ed61b1f94a356134"))
                .relays(List.of())
                .build();

        // When
        String json = JSON_MAPPER.writeValueAsString(poolReg);

        // Then - null fields are handled gracefully
        PoolRegistration deserialized = JSON_MAPPER.readValue(json, PoolRegistration.class);
        assertThat(deserialized.getOperator()).isNull();
        assertThat(deserialized.getVrfKeyHash()).isNull();
    }

    @Test
    void testEmptyRelaysList() throws Exception {
        // Given
        byte[] operator = HexUtil.decodeHexString("ed40b0a319f639a70b1e2a4de00f112c4f7b7d4849f0abd25c4336a4");

        PoolRegistration poolReg = PoolRegistration.builder()
                .operator(operator)
                .vrfKeyHash(operator)
                .pledge(BigInteger.valueOf(1000000000))
                .cost(BigInteger.valueOf(340000000))
                .margin(new UnitInterval(BigInteger.valueOf(1), BigInteger.valueOf(100)))
                .rewardAccount("e1f3c3d69b1d4eca197096cbfd67450f64123de4a5ed61b1f94a356134")
                .poolOwners(Set.of("f3c3d69b1d4eca197096cbfd67450f64123de4a5ed61b1f94a356134"))
                .relays(List.of())
                .build();

        // When
        String json = JSON_MAPPER.writeValueAsString(poolReg);
        PoolRegistration deserialized = JSON_MAPPER.readValue(json, PoolRegistration.class);

        // Then
        assertThat(deserialized.getRelays()).isEmpty();
    }

    @Test
    void testPoolMetadataOptional() throws Exception {
        // Given - Pool without metadata
        byte[] operator = HexUtil.decodeHexString("ed40b0a319f639a70b1e2a4de00f112c4f7b7d4849f0abd25c4336a4");

        PoolRegistration poolReg = PoolRegistration.builder()
                .operator(operator)
                .vrfKeyHash(operator)
                .pledge(BigInteger.valueOf(1000000000))
                .cost(BigInteger.valueOf(340000000))
                .margin(new UnitInterval(BigInteger.valueOf(1), BigInteger.valueOf(100)))
                .rewardAccount("e1f3c3d69b1d4eca197096cbfd67450f64123de4a5ed61b1f94a356134")
                .poolOwners(Set.of("f3c3d69b1d4eca197096cbfd67450f64123de4a5ed61b1f94a356134"))
                .relays(List.of())
                .poolMetadataUrl(null)
                .poolMetadataHash(null)
                .build();

        // When
        String json = JSON_MAPPER.writeValueAsString(poolReg);
        PoolRegistration deserialized = JSON_MAPPER.readValue(json, PoolRegistration.class);

        // Then
        assertThat(deserialized.getPoolMetadataUrl()).isNull();
        assertThat(deserialized.getPoolMetadataHash()).isNull();
    }

    @Test
    void testRelayPolymorphism() throws Exception {
        // Given - Test each relay type individually
        List<Relay> relays = Arrays.asList(
            SingleHostAddr.builder().port(3001).ipv4((Inet4Address) Inet4Address.getByName("1.2.3.4")).build(),
            SingleHostName.builder().port(3002).dnsName("relay.example.com").build(),
            MultiHostName.builder().dnsName("multi.example.com").build()
        );

        for (Relay relay : relays) {
            // When - Serialize individual relay
            String json = JSON_MAPPER.writeValueAsString(relay);
            System.out.println("Relay JSON: " + json);

            // Then - Contains relay_type discriminator
            assertThat(json).contains("relay_type");

            // When - Deserialize
            Relay deserialized = JSON_MAPPER.readValue(json, Relay.class);

            // Then - Correct type restored
            assertThat(deserialized).isInstanceOf(relay.getClass());
        }
    }
}
