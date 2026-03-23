package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the generated {@code SampleRequiredMetadataConverter}.
 * Verifies that required fields throw on missing keys and non-required fields stay null.
 */
class SampleRequiredMetadataConverterTest {

    SampleRequiredMetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SampleRequiredMetadataConverter();
    }

    @Test
    void roundTrip_allFieldsPresent() {
        SampleRequired obj = new SampleRequired();
        obj.setName("Alice");
        obj.setRefId(123);
        obj.setOptional("extra");

        SampleRequired restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

        assertEquals("Alice", restored.getName());
        assertEquals(123, restored.getRefId());
        assertEquals("extra", restored.getOptional());
    }

    @Test
    void missingRequiredString_throws() {
        MetadataMap map = MetadataBuilder.createMap();
        map.put("ref_id", BigInteger.valueOf(1));
        // "name" is missing

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> converter.fromMetadataMap(map));
        assertTrue(ex.getMessage().contains("name"), "Exception should mention the missing key");
    }

    @Test
    void missingRequiredInt_throws() {
        MetadataMap map = MetadataBuilder.createMap();
        map.put("name", "Bob");
        // "ref_id" is missing

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> converter.fromMetadataMap(map));
        assertTrue(ex.getMessage().contains("ref_id"), "Exception should mention the missing key");
    }

    @Test
    void missingOptionalField_staysNull() {
        MetadataMap map = MetadataBuilder.createMap();
        map.put("name", "Carol");
        map.put("ref_id", BigInteger.valueOf(5));
        // "optional" is missing — should be fine

        SampleRequired restored = converter.fromMetadataMap(map);

        assertEquals("Carol", restored.getName());
        assertEquals(5, restored.getRefId());
        assertNull(restored.getOptional());
    }
}
