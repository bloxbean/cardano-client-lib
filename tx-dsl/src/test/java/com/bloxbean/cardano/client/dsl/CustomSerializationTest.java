package com.bloxbean.cardano.client.dsl;

import com.bloxbean.cardano.client.address.Credential;
import com.bloxbean.cardano.client.address.CredentialType;
import com.bloxbean.cardano.client.dsl.intention.DRepRegistrationIntention;
import com.bloxbean.cardano.client.dsl.intention.PaymentIntention;
import com.bloxbean.cardano.client.transaction.spec.governance.Anchor;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for custom serialization of Credential, Anchor, and byte[] fields.
 * Verifies that YAML serialization/deserialization works with human-readable formats.
 */
public class CustomSerializationTest {

    private static final Logger log = LoggerFactory.getLogger(CustomSerializationTest.class);

    @Test
    public void testCredentialSerialization() {
        log.info("=== Testing Credential Serialization ===");

        // Given - create test credentials
        byte[] keyBytes = HexUtil.decodeHexString("1234567890abcdef1234567890abcdef12345678");
        byte[] scriptBytes = HexUtil.decodeHexString("fedcba0987654321fedcba0987654321fedcba09");
        
        Credential keyCredential = Credential.fromKey(keyBytes);
        Credential scriptCredential = Credential.fromScript(scriptBytes);

        // When - create TxDsl with DRep registrations using different credential types
        TxDsl original = new TxDsl()
                .registerDRep(keyCredential, null)
                .registerDRep(scriptCredential, null);

        // Then - serialize to YAML
        String yaml = original.toYaml();
        log.info("Serialized YAML with credentials:\n{}", yaml);

        // Verify YAML contains human-readable credential types and hex bytes
        assertThat(yaml).contains("type: Key");
        assertThat(yaml).contains("type: Script");
        assertThat(yaml).contains("bytes: 1234567890abcdef1234567890abcdef12345678");
        assertThat(yaml).contains("bytes: fedcba0987654321fedcba0987654321fedcba09");

        // When - deserialize from YAML
        TxDsl restored = TxDsl.fromYaml(yaml);

        // Then - verify deserialization worked
        assertThat(restored).isNotNull();
        assertThat(restored.getIntentions()).hasSize(2);

        log.info("✓ Credential serialization working correctly");
    }

    @Test
    public void testCredentialCaseInsensitiveDeserialization() {
        log.info("=== Testing Credential Case-Insensitive Deserialization ===");

        // Given - YAML with various case combinations
        String yaml = "version: 1.0\n" +
                "transaction:\n" +
                "  - tx:\n" +
                "      intentions:\n" +
                "        - type: drep_registration\n" +
                "          drep_credential:\n" +
                "            type: key\n" +
                "            bytes: 1234567890abcdef1234567890abcdef12345678\n" +
                "        - type: drep_registration\n" +
                "          drep_credential:\n" +
                "            type: KEY\n" +
                "            bytes: abcdef1234567890abcdef1234567890abcdef12\n" +
                "        - type: drep_registration\n" +
                "          drep_credential:\n" +
                "            type: Script\n" +
                "            bytes: fedcba0987654321fedcba0987654321fedcba09\n" +
                "        - type: drep_registration\n" +
                "          drep_credential:\n" +
                "            type: SCRIPT\n" +
                "            bytes: 987654321fedcba0987654321fedcba098765432\n";

        // When - deserialize from YAML
        TxDsl restored = TxDsl.fromYaml(yaml);

        // Then - verify all credentials were parsed correctly
        assertThat(restored).isNotNull();
        assertThat(restored.getIntentions()).hasSize(4);

        // All should be DRepRegistrationIntention
        assertThat(restored.getIntentions()).allMatch(intention -> intention instanceof DRepRegistrationIntention);

        log.info("✓ Credential case-insensitive deserialization working correctly");
    }

    @Test
    public void testAnchorSerialization() {
        log.info("=== Testing Anchor Serialization ===");

        // Given - create test anchor
        String url = "https://example.com/metadata.json";
        byte[] dataHash = HexUtil.decodeHexString("bafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937");
        Anchor anchor = new Anchor(url, dataHash);

        byte[] credBytes = HexUtil.decodeHexString("1234567890abcdef1234567890abcdef12345678");
        Credential credential = Credential.fromKey(credBytes);

        // When - create TxDsl with DRep registration containing anchor
        TxDsl original = new TxDsl()
                .registerDRep(credential, anchor);

        // Then - serialize to YAML
        String yaml = original.toYaml();
        log.info("Serialized YAML with anchor:\n{}", yaml);

        // Verify YAML contains human-readable anchor format
        assertThat(yaml).contains("anchor_url: " + url);
        assertThat(yaml).contains("anchor_data_hash: bafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937");

        // When - deserialize from YAML
        TxDsl restored = TxDsl.fromYaml(yaml);

        // Then - verify deserialization worked
        assertThat(restored).isNotNull();
        assertThat(restored.getIntentions()).hasSize(1);

        log.info("✓ Anchor serialization working correctly");
    }

    @Test
    public void testByteArraySerialization() {
        log.info("=== Testing Byte Array Serialization ===");

        // Given - create payment intention with script reference bytes
        byte[] scriptRefBytes = HexUtil.decodeHexString("abcdef1234567890fedcba0987654321");
        
        PaymentIntention paymentIntention = PaymentIntention.builder()
                .address("addr1test123")
                .scriptRefBytes(scriptRefBytes)
                .build();

        TxDsl original = new TxDsl();
        original.addIntention(paymentIntention);

        // When - serialize to YAML
        String yaml = original.toYaml();
        log.info("Serialized YAML with byte array:\n{}", yaml);

        // Verify YAML contains hex representation of byte array
        assertThat(yaml).contains("script_ref_bytes: abcdef1234567890fedcba0987654321");

        // When - deserialize from YAML
        TxDsl restored = TxDsl.fromYaml(yaml);

        // Then - verify deserialization worked
        assertThat(restored).isNotNull();
        assertThat(restored.getIntentions()).hasSize(1);

        log.info("✓ Byte array serialization working correctly");
    }

    @Test
    public void testCompleteGovernanceRoundTrip() {
        log.info("=== Testing Complete Governance Round-Trip Serialization ===");

        // Given - create complex governance transaction
        byte[] credBytes = HexUtil.decodeHexString("1234567890abcdef1234567890abcdef12345678");
        Credential credential = Credential.fromKey(credBytes);
        
        byte[] anchorHash = HexUtil.decodeHexString("bafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937");
        Anchor anchor = new Anchor("https://example.com/governance.json", anchorHash);

        TxDsl original = new TxDsl()
                .registerDRep(credential, anchor)
                .updateDRep(credential, anchor)
                .unregisterDRep(credential)
                .from("addr1test123");

        // When - serialize to YAML
        String yaml = original.toYaml();
        log.info("Serialized complete governance YAML:\n{}", yaml);

        // Then - verify YAML format
        assertThat(yaml).contains("version: 1.0");
        assertThat(yaml).contains("from: addr1test123");
        assertThat(yaml).contains("type: Key");
        assertThat(yaml).contains("bytes: 1234567890abcdef1234567890abcdef12345678");
        assertThat(yaml).contains("anchor_url: https://example.com/governance.json");
        assertThat(yaml).contains("anchor_data_hash: bafef700c0039a2efb056a665b3a8bcd94f8670b88d659f7f3db68340f6f0937");

        // When - deserialize from YAML
        TxDsl restored = TxDsl.fromYaml(yaml);

        // Then - verify complete round-trip
        assertThat(restored).isNotNull();
        assertThat(restored.getIntentions()).hasSize(3); // register, update, unregister

        // Test serialization again to verify consistency
        String yamlRoundTrip = restored.toYaml();
        assertThat(yamlRoundTrip).contains("type: Key"); // Should maintain consistent format
        
        log.info("✓ Complete governance round-trip serialization working correctly");
    }
}