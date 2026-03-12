package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.MetadataMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for direct public field access (no getters/setters).
 */
class SampleDirectAccessMetadataConverterTest {

    SampleDirectAccessMetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SampleDirectAccessMetadataConverter();
    }

    @Test
    void scalarFields_roundTrip() {
        SampleDirectAccess obj = new SampleDirectAccess();
        obj.name = "Alice";
        obj.count = 42;

        SampleDirectAccess restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

        assertEquals("Alice", restored.name);
        assertEquals(42, restored.count);
    }

    @Test
    void listField_roundTrip() {
        SampleDirectAccess obj = new SampleDirectAccess();
        obj.tags = Arrays.asList("alpha", "beta", "gamma");

        SampleDirectAccess restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

        assertNotNull(restored.tags);
        assertEquals(3, restored.tags.size());
        assertEquals("alpha", restored.tags.get(0));
        assertEquals("beta", restored.tags.get(1));
        assertEquals("gamma", restored.tags.get(2));
    }

    @Test
    void nestedField_roundTrip() {
        SampleDirectAccess obj = new SampleDirectAccess();
        obj.address = new SampleNestedAddress("123 Main St", "Springfield", "62704");

        SampleDirectAccess restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

        assertNotNull(restored.address);
        assertEquals("123 Main St", restored.address.getStreet());
        assertEquals("Springfield", restored.address.getCity());
        assertEquals("62704", restored.address.getZip());
    }

    @Test
    void nullFields_keyAbsent() {
        SampleDirectAccess obj = new SampleDirectAccess();
        // all fields left null

        MetadataMap map = converter.toMetadataMap(obj);

        assertNull(map.get("name"));
        assertNull(map.get("count"));
        assertNull(map.get("tags"));
        assertNull(map.get("address"));
    }

    @Test
    void allFields_roundTrip() {
        SampleDirectAccess obj = new SampleDirectAccess();
        obj.name = "Bob";
        obj.count = 7;
        obj.tags = Arrays.asList("x", "y");
        obj.address = new SampleNestedAddress("456 Oak Ave", "Shelbyville", "62565");

        SampleDirectAccess restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

        assertEquals("Bob", restored.name);
        assertEquals(7, restored.count);
        assertEquals(2, restored.tags.size());
        assertEquals("456 Oak Ave", restored.address.getStreet());
    }
}
