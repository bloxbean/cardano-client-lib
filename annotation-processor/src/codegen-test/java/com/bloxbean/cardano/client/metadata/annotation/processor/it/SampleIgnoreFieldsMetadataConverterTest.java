package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.MetadataMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests proving {@code @MetadataIgnore} fields are excluded from serialization.
 */
class SampleIgnoreFieldsMetadataConverterTest {

    SampleIgnoreFieldsMetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SampleIgnoreFieldsMetadataConverter();
    }

    @Test
    void ignoredFields_absentFromMap() {
        SampleIgnoreFields obj = new SampleIgnoreFields();
        obj.setName("visible");
        obj.setSecret("should-not-appear");
        obj.setCount(10);
        obj.setInternalTags(Arrays.asList("tag1", "tag2"));

        MetadataMap map = converter.toMetadataMap(obj);

        assertEquals("visible", map.get("name"));
        assertEquals(10, ((Number) map.get("count")).intValue());
        assertNull(map.get("secret"));
        assertNull(map.get("internalTags"));
    }

    @Test
    void roundTrip_ignoredFieldsAreNull() {
        SampleIgnoreFields obj = new SampleIgnoreFields();
        obj.setName("Alice");
        obj.setSecret("hidden");
        obj.setCount(5);
        obj.setInternalTags(Arrays.asList("internal"));

        SampleIgnoreFields restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

        assertEquals("Alice", restored.getName());
        assertEquals(5, restored.getCount());
        // ignored fields should be null after round-trip
        assertNull(restored.getSecret());
        assertNull(restored.getInternalTags());
    }

    @Test
    void mapSize_onlyNonIgnoredFields() {
        SampleIgnoreFields obj = new SampleIgnoreFields();
        obj.setName("Bob");
        obj.setSecret("secret-val");
        obj.setCount(3);
        obj.setInternalTags(Arrays.asList("a", "b"));

        MetadataMap map = converter.toMetadataMap(obj);

        // Only "name" and "count" should be in the map
        int keyCount = 0;
        for (Object key : map.keys()) {
            keyCount++;
        }
        assertEquals(2, keyCount);
    }

    @Test
    void allFieldsNull_emptyMap() {
        SampleIgnoreFields obj = new SampleIgnoreFields();

        MetadataMap map = converter.toMetadataMap(obj);

        assertNull(map.get("name"));
        assertNull(map.get("count"));
        assertNull(map.get("secret"));
        assertNull(map.get("internalTags"));
    }
}
