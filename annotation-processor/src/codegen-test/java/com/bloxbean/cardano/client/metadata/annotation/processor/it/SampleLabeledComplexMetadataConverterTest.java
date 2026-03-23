package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for labeled POJO with complex field types.
 */
class SampleLabeledComplexMetadataConverterTest {

    SampleLabeledComplexMetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SampleLabeledComplexMetadataConverter();
    }

    @Test
    void toMetadata_fromMetadata_roundTrip() {
        SampleLabeledComplex obj = buildFullObject();

        Metadata metadata = converter.toMetadata(obj);
        SampleLabeledComplex restored = converter.fromMetadata(metadata);

        assertEquals("TestItem", restored.getName());
        assertEquals(Arrays.asList("alpha", "beta"), restored.getTags());
        assertEquals(OrderStatus.DELIVERED, restored.getStatus());
        assertEquals(90, restored.getScores().get("math"));
        assertEquals(85, restored.getScores().get("science"));
        assertEquals("123 Main St", restored.getAddress().getStreet());
    }

    @Test
    void toMetadata_labelKeyWrapsComplexContent() {
        SampleLabeledComplex obj = buildFullObject();

        Metadata metadata = converter.toMetadata(obj);
        Object raw = metadata.get(BigInteger.valueOf(42));

        assertNotNull(raw);
        assertInstanceOf(MetadataMap.class, raw);
        MetadataMap map = (MetadataMap) raw;
        assertEquals("TestItem", map.get("name"));
        assertEquals("DELIVERED", map.get("status"));
        assertNotNull(map.get("tags"));
        assertNotNull(map.get("scores"));
        assertInstanceOf(MetadataMap.class, map.get("address"));
    }

    @Test
    void toMetadataMap_stillWorks() {
        SampleLabeledComplex obj = new SampleLabeledComplex();
        obj.setName("DirectMap");
        obj.setStatus(OrderStatus.PENDING);

        MetadataMap map = converter.toMetadataMap(obj);

        assertEquals("DirectMap", map.get("name"));
        assertEquals("PENDING", map.get("status"));
    }

    @Test
    void fromMetadata_wrongLabel_throws() {
        SampleLabeledComplex obj = new SampleLabeledComplex();
        obj.setName("Test");
        Metadata metadata = converter.toMetadata(obj);
        // Remove label 42 and put under wrong label
        metadata.remove(BigInteger.valueOf(42));
        metadata.put(BigInteger.valueOf(999), "wrong");

        assertThrows(IllegalArgumentException.class, () -> converter.fromMetadata(metadata));
    }

    private SampleLabeledComplex buildFullObject() {
        SampleLabeledComplex obj = new SampleLabeledComplex();
        obj.setName("TestItem");
        obj.setTags(Arrays.asList("alpha", "beta"));
        obj.setStatus(OrderStatus.DELIVERED);
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("math", 90);
        scores.put("science", 85);
        obj.setScores(scores);
        obj.setAddress(new SampleNestedAddress("123 Main St", "Springfield", "62704"));
        return obj;
    }
}
