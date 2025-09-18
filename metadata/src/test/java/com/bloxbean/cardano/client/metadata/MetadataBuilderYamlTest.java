package com.bloxbean.cardano.client.metadata;

import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for YAML conversion functionality in MetadataBuilder
 */
class MetadataBuilderYamlTest {

    @Test
    void testSimpleMetadataToYaml() {
        Metadata metadata = MetadataBuilder.createMetadata();
        metadata.put(BigInteger.valueOf(1), "Hello World");
        metadata.put(BigInteger.valueOf(2), BigInteger.valueOf(12345));

        String yaml = MetadataBuilder.toYaml(metadata);
        assertNotNull(yaml);
        assertTrue(yaml.contains("Hello World"));
        assertTrue(yaml.contains("12345"));
    }

    @Test
    void testYamlToMetadata() {
        String yaml = "1: Hello World\n2: 12345";

        Metadata metadata = MetadataBuilder.metadataFromYaml(yaml);
        assertNotNull(metadata);
        assertEquals("Hello World", metadata.get(BigInteger.valueOf(1)));
        assertEquals(BigInteger.valueOf(12345), metadata.get(BigInteger.valueOf(2)));
    }

    @Test
    void testComplexMetadataWithMapToYaml() {
        Metadata metadata = MetadataBuilder.createMetadata();
        MetadataMap metadataMap = MetadataBuilder.createMap();
        metadataMap.put("name", "Alice");
        metadataMap.put("age", BigInteger.valueOf(30));
        metadataMap.put("active", "true");

        metadata.put(BigInteger.valueOf(721), metadataMap);

        String yaml = MetadataBuilder.toYaml(metadata);
        System.out.println(yaml);
        assertNotNull(yaml);
        assertTrue(yaml.contains("name: \"Alice\""));  // Strings are quoted
        assertTrue(yaml.contains("age: 30"));
        assertTrue(yaml.contains("active: \"true\""));
    }

    @Test
    void testYamlToMetadataWithMap() {
        String yaml = "721:\n  name: Alice\n  age: 30\n  active: \"true\"";

        Metadata metadata = MetadataBuilder.metadataFromYaml(yaml);
        assertNotNull(metadata);

        Object value = metadata.get(BigInteger.valueOf(721));
        assertTrue(value instanceof MetadataMap);
        MetadataMap map = (MetadataMap) value;
        assertEquals("Alice", map.get("name"));
        assertEquals(BigInteger.valueOf(30), map.get("age"));
        assertEquals("true", map.get("active"));
    }

    @Test
    void testMetadataWithListToYaml() {
        Metadata metadata = MetadataBuilder.createMetadata();
        MetadataList metadataList = MetadataBuilder.createList();
        metadataList.add("item1");
        metadataList.add("item2");
        metadataList.add(BigInteger.valueOf(100));

        metadata.put(BigInteger.valueOf(100), metadataList);

        String yaml = MetadataBuilder.toYaml(metadata);
        assertNotNull(yaml);
        assertTrue(yaml.contains("- \"item1\""));  // Strings are quoted
        assertTrue(yaml.contains("- \"item2\""));  // Strings are quoted
        assertTrue(yaml.contains("- 100"));
    }

    @Test
    void testYamlToMetadataWithList() {
        String yaml = "100:\n  - item1\n  - item2\n  - 100";

        Metadata metadata = MetadataBuilder.metadataFromYaml(yaml);
        assertNotNull(metadata);

        Object value = metadata.get(BigInteger.valueOf(100));
        assertTrue(value instanceof MetadataList);
        MetadataList list = (MetadataList) value;
        assertEquals(3, list.size());
        assertEquals("item1", list.getValueAt(0));
        assertEquals("item2", list.getValueAt(1));
        assertEquals(BigInteger.valueOf(100), list.getValueAt(2));
    }

    @Test
    void testMetadataWithByteArrayToYaml() {
        Metadata metadata = MetadataBuilder.createMetadata();
        byte[] bytes = new byte[]{0x01, 0x02, 0x03, 0x04};
        metadata.put(BigInteger.valueOf(5), bytes);

        String yaml = MetadataBuilder.toYaml(metadata);
        assertNotNull(yaml);
        assertTrue(yaml.contains("0x01020304"));
    }

    @Test
    void testYamlToMetadataWithByteArray() {
        String yaml = "5: \"0x01020304\"";

        Metadata metadata = MetadataBuilder.metadataFromYaml(yaml);
        assertNotNull(metadata);

        Object value = metadata.get(BigInteger.valueOf(5));
        assertTrue(value instanceof byte[]);
        byte[] bytes = (byte[]) value;
        assertArrayEquals(new byte[]{0x01, 0x02, 0x03, 0x04}, bytes);
    }

    @Test
    void testNestedStructureToYaml() {
        Metadata metadata = MetadataBuilder.createMetadata();

        MetadataMap outerMap = MetadataBuilder.createMap();
        MetadataMap innerMap = MetadataBuilder.createMap();
        innerMap.put("key1", "value1");
        innerMap.put("key2", BigInteger.valueOf(42));

        MetadataList innerList = MetadataBuilder.createList();
        innerList.add("listItem1");
        innerList.add(BigInteger.valueOf(99));

        outerMap.put("nested_map", innerMap);
        outerMap.put("nested_list", innerList);

        metadata.put(BigInteger.valueOf(1000), outerMap);

        String yaml = MetadataBuilder.toYaml(metadata);
        assertNotNull(yaml);
        assertTrue(yaml.contains("nested_map:"));
        assertTrue(yaml.contains("key1: \"value1\""));  // Strings are quoted
        assertTrue(yaml.contains("key2: 42"));
        assertTrue(yaml.contains("nested_list:"));
        assertTrue(yaml.contains("- \"listItem1\""));  // Strings are quoted
        assertTrue(yaml.contains("- 99"));
    }

    @Test
    void testYamlBodyToMetadata() {
        String yamlBody = "name: Bob\nage: 25";
        BigInteger label = BigInteger.valueOf(123);

        Metadata metadata = MetadataBuilder.metadataFromYamlBody(label, yamlBody);
        assertNotNull(metadata);

        Object value = metadata.get(label);
        assertTrue(value instanceof MetadataMap);
        MetadataMap map = (MetadataMap) value;
        assertEquals("Bob", map.get("name"));
        assertEquals(BigInteger.valueOf(25), map.get("age"));
    }

    @Test
    void testYamlBodyToMetadataMap() {
        String yamlBody = "field1: value1\nfield2: 200";

        MetadataMap map = MetadataBuilder.metadataMapFromYamlBody(yamlBody);
        assertNotNull(map);
        assertEquals("value1", map.get("field1"));
        assertEquals(BigInteger.valueOf(200), map.get("field2"));
    }

    @Test
    void testYamlBodyToMetadataList() {
        String yamlBody = "- first\n- second\n- 333";

        MetadataList list = MetadataBuilder.metadataListFromYamlBody(yamlBody);
        assertNotNull(list);
        assertEquals(3, list.size());
        assertEquals("first", list.getValueAt(0));
        assertEquals("second", list.getValueAt(1));
        assertEquals(BigInteger.valueOf(333), list.getValueAt(2));
    }

    @Test
    void testCborBytesToYaml() {
        Metadata metadata = MetadataBuilder.createMetadata();
        metadata.put(BigInteger.valueOf(10), "Test Value");

        byte[] cborBytes = metadata.serialize();
        String yaml = MetadataBuilder.toYaml(cborBytes);

        assertNotNull(yaml);
        assertTrue(yaml.contains("\"10\"") || yaml.contains("10:"));  // Accept quoted or unquoted
        assertTrue(yaml.contains("Test Value"));
    }

    @Test
    void testRoundTripConversion() {
        // Create original metadata
        Metadata original = MetadataBuilder.createMetadata();
        MetadataMap map = MetadataBuilder.createMap();
        map.put("string", "test");
        map.put("number", BigInteger.valueOf(42));
        map.put("bytes", new byte[]{0x0A, 0x0B});

        MetadataList list = MetadataBuilder.createList();
        list.add("item");
        list.add(BigInteger.valueOf(100));

        original.put(BigInteger.valueOf(1), map);
        original.put(BigInteger.valueOf(2), list);

        // Convert to YAML and back
        String yaml = MetadataBuilder.toYaml(original);
        Metadata restored = MetadataBuilder.metadataFromYaml(yaml);

        // Verify the round trip
        assertNotNull(restored);

        MetadataMap restoredMap = (MetadataMap) restored.get(BigInteger.valueOf(1));
        assertEquals("test", restoredMap.get("string"));
        assertEquals(BigInteger.valueOf(42), restoredMap.get("number"));
        assertArrayEquals(new byte[]{0x0A, 0x0B}, (byte[])restoredMap.get("bytes"));

        MetadataList restoredList = (MetadataList) restored.get(BigInteger.valueOf(2));
        assertEquals("item", restoredList.getValueAt(0));
        assertEquals(BigInteger.valueOf(100), restoredList.getValueAt(1));
    }

    @Test
    void testNegativeNumbers() {
        Metadata metadata = MetadataBuilder.createMetadata();
        metadata.putNegative(BigInteger.valueOf(3), BigInteger.valueOf(-123));

        String yaml = MetadataBuilder.toYaml(metadata);
        assertNotNull(yaml);

        Metadata restored = MetadataBuilder.metadataFromYaml(yaml);
        assertEquals(BigInteger.valueOf(-123), restored.get(BigInteger.valueOf(3)));
    }

    @Test
    void testInvalidYamlFormat() {
        String invalidYaml = "this is not: valid: yaml: structure";

        assertThrows(IllegalArgumentException.class, () -> {
            MetadataBuilder.metadataFromYaml(invalidYaml);
        });
    }

    @Test
    void testEmptyMetadata() {
        Metadata metadata = MetadataBuilder.createMetadata();

        String yaml = MetadataBuilder.toYaml(metadata);
        assertNotNull(yaml);

        Metadata restored = MetadataBuilder.metadataFromYaml(yaml);
        assertNotNull(restored);
    }

    @Test
    void testSpecialCharactersInStrings() {
        Metadata metadata = MetadataBuilder.createMetadata();
        metadata.put(BigInteger.valueOf(1), "String with\nnewline");
        metadata.put(BigInteger.valueOf(2), "String with \"quotes\"");

        String yaml = MetadataBuilder.toYaml(metadata);
        assertNotNull(yaml);

        Metadata restored = MetadataBuilder.metadataFromYaml(yaml);
        assertEquals("String with\nnewline", restored.get(BigInteger.valueOf(1)));
        assertEquals("String with \"quotes\"", restored.get(BigInteger.valueOf(2)));
    }
}
