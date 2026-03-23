package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the generated {@code SampleRecordMetadataConverter}.
 * Validates record-based serialization/deserialization round-trips.
 */
class SampleRecordMetadataConverterTest {

    SampleRecordMetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SampleRecordMetadataConverter();
    }

    @Nested
    class RoundTrip {

        @Test
        void allFieldsPopulated() {
            SampleRecord original = new SampleRecord(
                    "Alice", 30, "123 Main St",
                    BigInteger.valueOf(1000000),
                    List.of("tag1", "tag2"));

            SampleRecord restored = converter.fromMetadataMap(converter.toMetadataMap(original));

            assertEquals("Alice", restored.name());
            assertEquals(30, restored.age());
            assertEquals("123 Main St", restored.address());
            assertEquals(BigInteger.valueOf(1000000), restored.amount());
            assertEquals(List.of("tag1", "tag2"), restored.tags());
        }

        @Test
        void nullReferenceFields() {
            SampleRecord original = new SampleRecord(null, 0, null, null, null);

            SampleRecord restored = converter.fromMetadataMap(converter.toMetadataMap(original));

            assertNull(restored.name());
            assertEquals(0, restored.age());
            assertNull(restored.address());
            assertNull(restored.amount());
            assertNull(restored.tags());
        }

        @Test
        void emptyList() {
            SampleRecord original = new SampleRecord("Bob", 25, "addr", BigInteger.ONE, List.of());

            SampleRecord restored = converter.fromMetadataMap(converter.toMetadataMap(original));

            assertEquals("Bob", restored.name());
            assertNotNull(restored.tags());
            assertTrue(restored.tags().isEmpty());
        }
    }

    @Nested
    class CustomKey {

        @Test
        void addressUsesCustomKey() {
            SampleRecord original = new SampleRecord(null, 0, "custom-addr", null, null);

            MetadataMap map = converter.toMetadataMap(original);

            // Field 'address' should be stored under key 'addr'
            assertEquals("custom-addr", map.get("addr"));
            assertNull(map.get("address"));
        }
    }

    @Nested
    class LabelSupport {

        @Test
        void toMetadata_producesLabeledMetadata() {
            SampleRecord original = new SampleRecord("Test", 1, null, null, null);

            Metadata metadata = converter.toMetadata(original);
            assertNotNull(metadata);

            SampleRecord restored = converter.fromMetadata(metadata);
            assertEquals("Test", restored.name());
            assertEquals(1, restored.age());
        }
    }

    @Nested
    class Serialization {

        @Test
        void toMetadataMap_containsAllFields() {
            SampleRecord original = new SampleRecord(
                    "Charlie", 42, "456 Oak Ave",
                    BigInteger.TEN,
                    Arrays.asList("a", "b", "c"));

            MetadataMap map = converter.toMetadataMap(original);

            assertEquals("Charlie", map.get("name"));
            assertEquals(BigInteger.valueOf(42), map.get("age"));
            assertEquals("456 Oak Ave", map.get("addr"));
            assertEquals(BigInteger.TEN, map.get("amount"));
            assertNotNull(map.get("tags"));
        }
    }
}
