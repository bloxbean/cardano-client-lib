package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.MetadataMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the generated {@code SampleRecordComplexMetadataConverter}.
 * Validates enum, Optional, Map, and @MetadataIgnore support with records.
 */
class SampleRecordComplexMetadataConverterTest {

    SampleRecordComplexMetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SampleRecordComplexMetadataConverter();
    }

    @Test
    void roundTrip_allPopulated() {
        SampleRecordComplex original = new SampleRecordComplex(
                "test", OrderStatus.SHIPPED,
                Optional.of("nick"),
                Map.of("math", 95, "science", 88),
                "secret");

        SampleRecordComplex restored = converter.fromMetadataMap(converter.toMetadataMap(original));

        assertEquals("test", restored.label());
        assertEquals(OrderStatus.SHIPPED, restored.status());
        assertEquals(Optional.of("nick"), restored.nickname());
        assertEquals(95, restored.scores().get("math"));
        assertEquals(88, restored.scores().get("science"));
    }

    @Test
    void ignoredField_absentFromMap() {
        SampleRecordComplex original = new SampleRecordComplex(
                "visible", OrderStatus.PENDING,
                Optional.empty(), null, "should-not-appear");

        MetadataMap map = converter.toMetadataMap(original);

        assertNotNull(map.get("label"));
        assertNull(map.get("internal"));
    }

    @Test
    void optionalEmpty_roundTrip() {
        SampleRecordComplex original = new SampleRecordComplex(
                "test", null, Optional.empty(), null, null);

        SampleRecordComplex restored = converter.fromMetadataMap(converter.toMetadataMap(original));

        assertEquals(Optional.empty(), restored.nickname());
    }

    @Test
    void nullOptional_roundTrip() {
        SampleRecordComplex original = new SampleRecordComplex(
                "test", null, null, null, null);

        SampleRecordComplex restored = converter.fromMetadataMap(converter.toMetadataMap(original));

        // A null Optional in the source becomes Optional.empty() after round-trip
        // because the map has no entry, and the else-branch sets Optional.empty()
        assertEquals(Optional.empty(), restored.nickname());
    }
}
