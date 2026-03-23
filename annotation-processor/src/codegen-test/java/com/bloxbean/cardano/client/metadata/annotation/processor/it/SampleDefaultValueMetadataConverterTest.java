package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.MetadataBuilder;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the generated {@code SampleDefaultValueMetadataConverter}.
 * Verifies that missing keys fall back to declared defaults.
 */
class SampleDefaultValueMetadataConverterTest {

    SampleDefaultValueMetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SampleDefaultValueMetadataConverter();
    }

    @Test
    void roundTrip_allFieldsPresent() {
        SampleDefaultValue obj = new SampleDefaultValue();
        obj.setStatus("ACTIVE");
        obj.setCount(10);
        obj.setTimestamp(2000L);
        obj.setActive(false);
        obj.setAmount(BigInteger.valueOf(500));
        obj.setNoDefault("hello");

        SampleDefaultValue restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

        assertEquals("ACTIVE", restored.getStatus());
        assertEquals(10, restored.getCount());
        assertEquals(2000L, restored.getTimestamp());
        assertFalse(restored.isActive());
        assertEquals(BigInteger.valueOf(500), restored.getAmount());
        assertEquals("hello", restored.getNoDefault());
    }

    @Test
    void missingFields_useDefaults() {
        MetadataMap map = MetadataBuilder.createMap();
        // All defaultValue fields are missing — should use defaults

        SampleDefaultValue restored = converter.fromMetadataMap(map);

        assertEquals("UNKNOWN", restored.getStatus());
        assertEquals(42, restored.getCount());
        assertEquals(100L, restored.getTimestamp());
        assertTrue(restored.isActive());
        assertEquals(BigInteger.valueOf(999), restored.getAmount());
        assertNull(restored.getNoDefault(), "Field without defaultValue should stay null");
    }

    @Test
    void presentFields_overrideDefaults() {
        MetadataMap map = MetadataBuilder.createMap();
        map.put("status", "DONE");
        map.put("count", BigInteger.valueOf(7));
        map.put("timestamp", BigInteger.valueOf(3000));
        map.put("active", BigInteger.valueOf(0));
        map.put("amount", BigInteger.valueOf(1));

        SampleDefaultValue restored = converter.fromMetadataMap(map);

        assertEquals("DONE", restored.getStatus());
        assertEquals(7, restored.getCount());
        assertEquals(3000L, restored.getTimestamp());
        assertFalse(restored.isActive());
        assertEquals(BigInteger.ONE, restored.getAmount());
    }
}
