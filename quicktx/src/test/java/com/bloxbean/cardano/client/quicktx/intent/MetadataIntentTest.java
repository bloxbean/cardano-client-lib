package com.bloxbean.cardano.client.quicktx.intent;

import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import com.bloxbean.cardano.client.quicktx.serialization.PlaceholderMetadata;
import com.bloxbean.cardano.client.quicktx.serialization.YamlSerializer;
import com.bloxbean.cardano.client.util.HexUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for MetadataIntent serialization and deserialization.
 */
class MetadataIntentTest {

    @Test
    void testMetadataIntentCreation() {
        // Create test metadata
        Metadata metadata = createTestMetadata();

        // Create intent from metadata
        MetadataIntent intent = MetadataIntent.from(metadata);

        assertNotNull(intent);
        assertNotNull(intent.getMetadata());
        assertEquals("metadata", intent.getType());
    }

    @Test
    void testYamlSerialization() throws Exception {
        // Create test metadata
        Metadata metadata = createTestMetadata();
        MetadataIntent intent = MetadataIntent.from(metadata);

        // Serialize to YAML
        String yaml = YamlSerializer.serialize(intent);

        assertNotNull(yaml);
        assertTrue(yaml.contains("metadata:"));
        // Should contain YAML-formatted metadata
        assertTrue(yaml.contains("msg:") || yaml.contains("\"msg\":"));
    }

    @Test
    void testYamlDeserialization() throws Exception {
        // Create test metadata
        Metadata metadata = createTestMetadata();
        MetadataIntent intent = MetadataIntent.from(metadata);

        // Serialize to YAML
        String yaml = YamlSerializer.serialize(intent);

        // Deserialize from YAML
        MetadataIntent restored = YamlSerializer.deserialize(yaml, MetadataIntent.class);

        assertNotNull(restored);
        assertNotNull(restored.getMetadata());
        assertEquals(intent.getType(), restored.getType());

        // Verify metadata content
        MetadataMap restoredMap = (MetadataMap) restored.getMetadata().get(BigInteger.valueOf(721));
        assertNotNull(restoredMap);
        assertEquals("Test message", restoredMap.get("msg"));
    }

    @Test
    void testCborHexDeserialization() throws Exception {
        // Create test metadata
        Metadata metadata = createTestMetadata();

        // Get CBOR hex
        String cborHex = HexUtil.encodeHexString(metadata.serialize());

        // Create YAML with CBOR hex
        String yaml = "type: metadata\nmetadata: \"" + cborHex + "\"";

        // Deserialize
        MetadataIntent restored = YamlSerializer.deserialize(yaml, MetadataIntent.class);

        assertNotNull(restored);
        assertNotNull(restored.getMetadata());

        // Verify metadata content
        MetadataMap restoredMap = (MetadataMap) restored.getMetadata().get(BigInteger.valueOf(721));
        assertNotNull(restoredMap);
        assertEquals("Test message", restoredMap.get("msg"));
    }

    @Test
    void testJsonLegacyDeserialization() throws Exception {
        // Create test metadata
        Metadata metadata = createTestMetadata();
        String metadataJson = MetadataBuilder.toJson(metadata);

        // Create YAML with JSON metadata
        String yaml = "type: metadata\nmetadata: '" + metadataJson + "'";

        // Deserialize
        MetadataIntent restored = YamlSerializer.deserialize(yaml, MetadataIntent.class);

        assertNotNull(restored);
        assertNotNull(restored.getMetadata());

        // Verify metadata content
        MetadataMap restoredMap = (MetadataMap) restored.getMetadata().get(BigInteger.valueOf(721));
        assertNotNull(restoredMap);
        assertEquals("Test message", restoredMap.get("msg"));
    }

    @Test
    void testRoundTripWithYamlFormat() throws Exception {
        // Create complex metadata
        Metadata metadata = createComplexMetadata();
        MetadataIntent original = MetadataIntent.from(metadata);

        // Serialize to YAML
        String yaml = YamlSerializer.serialize(original);

        // Deserialize back
        MetadataIntent restored = YamlSerializer.deserialize(yaml, MetadataIntent.class);

        assertNotNull(restored);
        assertNotNull(restored.getMetadata());

        // Verify complex structure
        MetadataMap map721 = (MetadataMap) restored.getMetadata().get(BigInteger.valueOf(721));
        assertNotNull(map721);

        MetadataMap tokenMap = (MetadataMap) map721.get("TestToken");
        assertNotNull(tokenMap);
        assertEquals("Test Token", tokenMap.get("name"));
        assertEquals("Test token for testing", tokenMap.get("description"));
        assertEquals(BigInteger.valueOf(1000000), tokenMap.get("decimals"));
    }

    @Test
    void testDirectJsonSerialization() throws Exception {
        // Create test metadata
        Metadata metadata = createTestMetadata();
        MetadataIntent intent = MetadataIntent.from(metadata);

        // Use Jackson directly
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(intent);

        assertNotNull(json);
        assertTrue(json.contains("metadata"));

        // Deserialize back
        MetadataIntent restored = mapper.readValue(json, MetadataIntent.class);

        assertNotNull(restored);
        assertNotNull(restored.getMetadata());
    }

    @Test
    void testNullMetadata() {
        assertThrows(IllegalArgumentException.class, () -> {
            MetadataIntent.from(null);
        });
    }

    @Test
    void testValidation() {
        MetadataIntent intent = MetadataIntent.builder().build();

        assertThrows(IllegalStateException.class, () -> {
            intent.validate();
        });

        Metadata metadata = createTestMetadata();
        intent.setMetadata(metadata);

        // Should not throw
        assertDoesNotThrow(() -> intent.validate());
    }

    @Test
    void testEmptyStringDeserialization() throws Exception {
        String yaml = "type: metadata\nmetadata: \"\"";

        MetadataIntent restored = YamlSerializer.deserialize(yaml, MetadataIntent.class);

        assertNotNull(restored);
        assertNull(restored.getMetadata());
    }

    @Test
    void testVariableReference() throws Exception {
        // YAML with variable reference
        String yaml = "type: metadata\nmetadata: \"${metadata_var}\"";

        // Should deserialize without error (variable will be resolved later)
        MetadataIntent restored = YamlSerializer.deserialize(yaml, MetadataIntent.class);

        assertNotNull(restored);
        assertTrue(restored.getMetadata() instanceof PlaceholderMetadata);
        assertEquals("${metadata_var}", ((PlaceholderMetadata) restored.getMetadata()).getTemplate());
    }

    @Test
    void testVariableResolution() {
        Metadata metadata = createTestMetadata();
        String metadataYaml = MetadataBuilder.toYaml(metadata);

        String yaml = "type: metadata\nmetadata: \"${metadata_var}\"";
        MetadataIntent intent = YamlSerializer.deserialize(yaml, MetadataIntent.class);

        MetadataIntent resolved = (MetadataIntent) intent.resolveVariables(Map.of("metadata_var", metadataYaml));

        assertNotNull(resolved.getMetadata());
        assertFalse(resolved.getMetadata() instanceof PlaceholderMetadata);
        assertEquals(metadataYaml.trim(), MetadataBuilder.toYaml(resolved.getMetadata()).trim());
    }

    // Helper methods

    private Metadata createTestMetadata() {
        return MetadataBuilder.createMetadata()
                .put(BigInteger.valueOf(721), MetadataBuilder.createMap()
                        .put("msg", "Test message")
                        .put("number", BigInteger.valueOf(123))
                        .put("array", MetadataBuilder.createList()
                                .add("item1")
                                .add("item2")));
    }

    private Metadata createComplexMetadata() {
        return MetadataBuilder.createMetadata()
                .put(BigInteger.valueOf(721), MetadataBuilder.createMap()
                        .put("TestToken", MetadataBuilder.createMap()
                                .put("name", "Test Token")
                                .put("description", "Test token for testing")
                                .put("decimals", BigInteger.valueOf(1000000))
                                .put("ticker", "TST")
                                .put("url", "https://test.com")
                                .put("logo", "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==")))
                .put(BigInteger.valueOf(20), MetadataBuilder.createMap()
                        .put("message", "Additional metadata")
                        .put("timestamp", BigInteger.valueOf(System.currentTimeMillis() / 1000)));
    }
}
