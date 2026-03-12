package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.MetadataList;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for composite {@code Map<String, V>} field support.
 */
class SampleCompositeMapMetadataConverterTest {

    SampleCompositeMapMetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SampleCompositeMapMetadataConverter();
    }

    @Nested
    class MapStringListString {

        @Test
        void roundTrip() {
            SampleCompositeMap obj = new SampleCompositeMap();
            Map<String, List<String>> tags = new LinkedHashMap<>();
            tags.put("colors", List.of("red", "green", "blue"));
            tags.put("sizes", List.of("S", "M", "L"));
            obj.setTagsByCategory(tags);

            SampleCompositeMap restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(List.of("red", "green", "blue"), restored.getTagsByCategory().get("colors"));
            assertEquals(List.of("S", "M", "L"), restored.getTagsByCategory().get("sizes"));
            assertEquals(2, restored.getTagsByCategory().size());
        }

        @Test
        void metadataStructure() {
            SampleCompositeMap obj = new SampleCompositeMap();
            Map<String, List<String>> tags = new LinkedHashMap<>();
            tags.put("fruit", List.of("apple", "banana"));
            obj.setTagsByCategory(tags);

            MetadataMap map = converter.toMetadataMap(obj);
            Object tagsVal = map.get("tagsByCategory");
            assertInstanceOf(MetadataMap.class, tagsVal);

            MetadataMap tagsMap = (MetadataMap) tagsVal;
            Object fruitVal = tagsMap.get("fruit");
            assertInstanceOf(MetadataList.class, fruitVal);

            MetadataList fruitList = (MetadataList) fruitVal;
            assertEquals(2, fruitList.size());
            assertEquals("apple", fruitList.getValueAt(0));
            assertEquals("banana", fruitList.getValueAt(1));
        }

        @Test
        void emptyInnerList() {
            SampleCompositeMap obj = new SampleCompositeMap();
            Map<String, List<String>> tags = new LinkedHashMap<>();
            tags.put("empty", List.of());
            obj.setTagsByCategory(tags);

            SampleCompositeMap restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getTagsByCategory().get("empty"));
            assertTrue(restored.getTagsByCategory().get("empty").isEmpty());
        }

        @Test
        void nullMap_keyAbsent() {
            SampleCompositeMap obj = new SampleCompositeMap();
            obj.setTagsByCategory(null);

            MetadataMap map = converter.toMetadataMap(obj);
            assertNull(map.get("tagsByCategory"));
        }
    }

    @Nested
    class MapStringMapStringInteger {

        @Test
        void roundTrip() {
            SampleCompositeMap obj = new SampleCompositeMap();
            Map<String, Map<String, Integer>> scores = new LinkedHashMap<>();
            Map<String, Integer> aliceScores = new LinkedHashMap<>();
            aliceScores.put("math", 95);
            aliceScores.put("science", 88);
            Map<String, Integer> bobScores = new LinkedHashMap<>();
            bobScores.put("math", 72);
            scores.put("alice", aliceScores);
            scores.put("bob", bobScores);
            obj.setNestedScores(scores);

            SampleCompositeMap restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(95, restored.getNestedScores().get("alice").get("math"));
            assertEquals(88, restored.getNestedScores().get("alice").get("science"));
            assertEquals(72, restored.getNestedScores().get("bob").get("math"));
        }

        @Test
        void metadataStructure() {
            SampleCompositeMap obj = new SampleCompositeMap();
            Map<String, Map<String, Integer>> scores = new LinkedHashMap<>();
            Map<String, Integer> inner = new LinkedHashMap<>();
            inner.put("x", 42);
            scores.put("group1", inner);
            obj.setNestedScores(scores);

            MetadataMap map = converter.toMetadataMap(obj);
            Object scoresVal = map.get("nestedScores");
            assertInstanceOf(MetadataMap.class, scoresVal);

            MetadataMap scoresMap = (MetadataMap) scoresVal;
            Object group1Val = scoresMap.get("group1");
            assertInstanceOf(MetadataMap.class, group1Val);
        }

        @Test
        void emptyInnerMap() {
            SampleCompositeMap obj = new SampleCompositeMap();
            Map<String, Map<String, Integer>> scores = new LinkedHashMap<>();
            scores.put("empty", new LinkedHashMap<>());
            obj.setNestedScores(scores);

            SampleCompositeMap restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getNestedScores().get("empty"));
            assertTrue(restored.getNestedScores().get("empty").isEmpty());
        }
    }

    @Nested
    class MapStringListEnum {

        @Test
        void roundTrip() {
            SampleCompositeMap obj = new SampleCompositeMap();
            Map<String, List<OrderStatus>> statuses = new LinkedHashMap<>();
            statuses.put("group1", List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED));
            statuses.put("group2", List.of(OrderStatus.SHIPPED));
            obj.setStatusesByGroup(statuses);

            SampleCompositeMap restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED),
                    restored.getStatusesByGroup().get("group1"));
            assertEquals(List.of(OrderStatus.SHIPPED),
                    restored.getStatusesByGroup().get("group2"));
        }
    }

    @Nested
    class MapStringListNested {

        @Test
        void roundTrip() {
            SampleCompositeMap obj = new SampleCompositeMap();
            Map<String, List<SampleNestedAddress>> addresses = new LinkedHashMap<>();
            addresses.put("home", List.of(
                    new SampleNestedAddress("123 Main St", "Springfield", "62704"),
                    new SampleNestedAddress("456 Oak Ave", "Springfield", "62705")
            ));
            addresses.put("work", List.of(
                    new SampleNestedAddress("789 Office Dr", "Capital City", "90210")
            ));
            obj.setAddressesByType(addresses);

            SampleCompositeMap restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(2, restored.getAddressesByType().get("home").size());
            assertEquals("123 Main St", restored.getAddressesByType().get("home").get(0).getStreet());
            assertEquals("789 Office Dr", restored.getAddressesByType().get("work").get(0).getStreet());
        }

        @Test
        void metadataStructure() {
            SampleCompositeMap obj = new SampleCompositeMap();
            Map<String, List<SampleNestedAddress>> addresses = new LinkedHashMap<>();
            addresses.put("office", List.of(new SampleNestedAddress("10 Elm", "City", "11111")));
            obj.setAddressesByType(addresses);

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataMap outerMap = (MetadataMap) map.get("addressesByType");
            MetadataList officeList = (MetadataList) outerMap.get("office");
            assertInstanceOf(MetadataMap.class, officeList.getValueAt(0));

            MetadataMap addrMap = (MetadataMap) officeList.getValueAt(0);
            assertEquals("10 Elm", addrMap.get("street"));
        }
    }
}
