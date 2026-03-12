package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.MetadataMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for inheritance combined with complex field types.
 */
class SampleChildComplexMetadataConverterTest {

    SampleChildComplexMetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SampleChildComplexMetadataConverter();
    }

    @Test
    void allFields_roundTrip() {
        SampleChildComplex obj = new SampleChildComplex();
        // parent fields
        obj.setVersion("2.0");
        obj.setAuthor("Alice");
        // child fields
        obj.setStatus(OrderStatus.CONFIRMED);
        obj.setTags(Arrays.asList("urgent", "premium"));
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("quality", 95);
        scores.put("speed", 88);
        obj.setScores(scores);
        obj.setAddress(new SampleNestedAddress("123 Main St", "Springfield", "62704"));

        SampleChildComplex restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

        // parent
        assertEquals("2.0", restored.getVersion());
        assertEquals("Alice", restored.getAuthor());
        // child
        assertEquals(OrderStatus.CONFIRMED, restored.getStatus());
        assertEquals(Arrays.asList("urgent", "premium"), restored.getTags());
        assertEquals(95, restored.getScores().get("quality"));
        assertEquals(88, restored.getScores().get("speed"));
        assertEquals("123 Main St", restored.getAddress().getStreet());
    }

    @Test
    void parentFieldsPresentInMap() {
        SampleChildComplex obj = new SampleChildComplex();
        obj.setVersion("1.0");
        obj.setAuthor("Bob");
        obj.setStatus(OrderStatus.PENDING);

        MetadataMap map = converter.toMetadataMap(obj);

        assertEquals("1.0", map.get("version"));
        assertEquals("Bob", map.get("author"));
        assertEquals("PENDING", map.get("status"));
    }

    @Nested
    class NullHandling {

        @Test
        void nullChildFields_parentFieldsPreserved() {
            SampleChildComplex obj = new SampleChildComplex();
            obj.setVersion("3.0");
            obj.setAuthor("Charlie");
            // child fields left null

            SampleChildComplex restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals("3.0", restored.getVersion());
            assertEquals("Charlie", restored.getAuthor());
            assertNull(restored.getStatus());
            assertNull(restored.getTags());
            assertNull(restored.getScores());
            assertNull(restored.getAddress());
        }

        @Test
        void nullParentFields_childFieldsPreserved() {
            SampleChildComplex obj = new SampleChildComplex();
            // parent fields left null
            obj.setStatus(OrderStatus.SHIPPED);
            obj.setTags(Arrays.asList("tag1"));

            SampleChildComplex restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNull(restored.getVersion());
            assertNull(restored.getAuthor());
            assertEquals(OrderStatus.SHIPPED, restored.getStatus());
            assertEquals(1, restored.getTags().size());
        }
    }

    @Test
    void emptyCollections_withInheritedFields() {
        SampleChildComplex obj = new SampleChildComplex();
        obj.setVersion("1.0");
        obj.setTags(List.of());
        obj.setScores(new LinkedHashMap<>());

        SampleChildComplex restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

        assertEquals("1.0", restored.getVersion());
        assertNotNull(restored.getTags());
        assertTrue(restored.getTags().isEmpty());
        assertNotNull(restored.getScores());
        assertTrue(restored.getScores().isEmpty());
    }
}
