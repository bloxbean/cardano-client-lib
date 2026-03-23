package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.MetadataMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for 2+ levels of nesting (nested within nested).
 */
class SampleDeepNestedMetadataConverterTest {

    SampleDeepNestedMetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SampleDeepNestedMetadataConverter();
    }

    @Nested
    class ScalarDeepNested {

        @Test
        void roundTrip() {
            SampleDeepNested obj = new SampleDeepNested();
            obj.setId("DN-001");
            obj.setInner(new SampleDeepInner("home",
                    new SampleNestedAddress("123 Main St", "Springfield", "62704")));

            SampleDeepNested restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals("DN-001", restored.getId());
            assertNotNull(restored.getInner());
            assertEquals("home", restored.getInner().getLabel());
            assertNotNull(restored.getInner().getAddress());
            assertEquals("123 Main St", restored.getInner().getAddress().getStreet());
            assertEquals("Springfield", restored.getInner().getAddress().getCity());
            assertEquals("62704", restored.getInner().getAddress().getZip());
        }

        @Test
        void nestedStoredAsNestedMetadataMap() {
            SampleDeepNested obj = new SampleDeepNested();
            obj.setInner(new SampleDeepInner("work",
                    new SampleNestedAddress("456 Oak Ave", "Shelbyville", "62565")));

            MetadataMap map = converter.toMetadataMap(obj);
            Object innerVal = map.get("inner");
            assertInstanceOf(MetadataMap.class, innerVal);

            MetadataMap innerMap = (MetadataMap) innerVal;
            assertEquals("work", innerMap.get("label"));
            Object addressVal = innerMap.get("address");
            assertInstanceOf(MetadataMap.class, addressVal);

            MetadataMap addressMap = (MetadataMap) addressVal;
            assertEquals("456 Oak Ave", addressMap.get("street"));
        }

        @Test
        void nullInner_keyAbsent() {
            SampleDeepNested obj = new SampleDeepNested();
            obj.setId("DN-002");
            obj.setInner(null);

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals("DN-002", map.get("id"));
            assertNull(map.get("inner"));
        }

        @Test
        void nullInnerAddress_keyAbsentInInnerMap() {
            SampleDeepNested obj = new SampleDeepNested();
            obj.setInner(new SampleDeepInner("label-only", null));

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataMap innerMap = (MetadataMap) map.get("inner");

            assertEquals("label-only", innerMap.get("label"));
            assertNull(innerMap.get("address"));
        }
    }

    @Nested
    class ListOfDeepNested {

        @Test
        void roundTrip() {
            SampleDeepNested obj = new SampleDeepNested();
            obj.setInnerList(Arrays.asList(
                    new SampleDeepInner("home",
                            new SampleNestedAddress("111 First St", "CityA", "11111")),
                    new SampleDeepInner("work",
                            new SampleNestedAddress("222 Second St", "CityB", "22222"))
            ));

            SampleDeepNested restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getInnerList());
            assertEquals(2, restored.getInnerList().size());
            assertEquals("home", restored.getInnerList().get(0).getLabel());
            assertEquals("111 First St", restored.getInnerList().get(0).getAddress().getStreet());
            assertEquals("work", restored.getInnerList().get(1).getLabel());
            assertEquals("222 Second St", restored.getInnerList().get(1).getAddress().getStreet());
        }

        @Test
        void emptyList_roundTrip() {
            SampleDeepNested obj = new SampleDeepNested();
            obj.setInnerList(List.of());

            SampleDeepNested restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getInnerList());
            assertTrue(restored.getInnerList().isEmpty());
        }
    }
}
