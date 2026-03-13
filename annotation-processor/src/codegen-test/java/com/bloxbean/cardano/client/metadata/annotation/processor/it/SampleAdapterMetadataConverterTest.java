package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for custom adapter support via {@code @MetadataField(adapter = ...)}.
 */
class SampleAdapterMetadataConverterTest {

    SampleAdapterMetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SampleAdapterMetadataConverter();
    }

    @Test
    void roundTrip() {
        Instant now = Instant.ofEpochSecond(1700000000L);
        SampleAdapter obj = new SampleAdapter("event", now);

        MetadataMap map = converter.toMetadataMap(obj);
        SampleAdapter restored = converter.fromMetadataMap(map);

        assertEquals("event", restored.getName());
        assertEquals(now, restored.getTimestamp());
    }

    @Test
    void toMetadataMap_adapterFieldStoredAsBigInteger() {
        Instant ts = Instant.ofEpochSecond(1700000000L);
        SampleAdapter obj = new SampleAdapter("test", ts);

        MetadataMap map = converter.toMetadataMap(obj);

        assertEquals("test", map.get("name"));
        assertEquals(BigInteger.valueOf(1700000000L), map.get("timestamp"));
    }

    @Test
    void nullAdapterField_omittedFromMap() {
        SampleAdapter obj = new SampleAdapter("test", null);

        MetadataMap map = converter.toMetadataMap(obj);

        assertEquals("test", map.get("name"));
        assertNull(map.get("timestamp"));
    }

    @Test
    void nullAdapterField_deserializesToNull() {
        SampleAdapter obj = new SampleAdapter("test", null);

        MetadataMap map = converter.toMetadataMap(obj);
        SampleAdapter restored = converter.fromMetadataMap(map);

        assertEquals("test", restored.getName());
        assertNull(restored.getTimestamp());
    }

    @Test
    void toMetadata_fromMetadata_roundTrip() {
        Instant ts = Instant.ofEpochSecond(1234567890L);
        SampleAdapter obj = new SampleAdapter("labeled", ts);

        Metadata metadata = converter.toMetadata(obj);
        SampleAdapter restored = converter.fromMetadata(metadata);

        assertEquals("labeled", restored.getName());
        assertEquals(ts, restored.getTimestamp());
    }

    @Test
    void toMetadata_labelKeyPresent() {
        SampleAdapter obj = new SampleAdapter("check", Instant.ofEpochSecond(42L));

        Metadata metadata = converter.toMetadata(obj);
        Object raw = metadata.get(BigInteger.valueOf(950L));

        assertNotNull(raw);
        assertInstanceOf(MetadataMap.class, raw);
        MetadataMap map = (MetadataMap) raw;
        assertEquals(BigInteger.valueOf(42L), map.get("timestamp"));
    }
}
