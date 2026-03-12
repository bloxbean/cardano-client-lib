package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.MetadataList;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for composite collection element support.
 */
class SampleCompositeListMetadataConverterTest {

    SampleCompositeListMetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SampleCompositeListMetadataConverter();
    }

    @Nested
    class ListOfListString {

        @Test
        void roundTrip() {
            SampleCompositeList obj = new SampleCompositeList();
            List<List<String>> matrix = List.of(
                    List.of("a", "b", "c"),
                    List.of("d", "e")
            );
            obj.setMatrix(matrix);

            SampleCompositeList restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(2, restored.getMatrix().size());
            assertEquals(List.of("a", "b", "c"), restored.getMatrix().get(0));
            assertEquals(List.of("d", "e"), restored.getMatrix().get(1));
        }

        @Test
        void metadataStructure() {
            SampleCompositeList obj = new SampleCompositeList();
            obj.setMatrix(List.of(List.of("x", "y")));

            MetadataMap map = converter.toMetadataMap(obj);
            Object matrixVal = map.get("matrix");
            assertInstanceOf(MetadataList.class, matrixVal);

            MetadataList outerList = (MetadataList) matrixVal;
            assertEquals(1, outerList.size());
            assertInstanceOf(MetadataList.class, outerList.getValueAt(0));

            MetadataList innerList = (MetadataList) outerList.getValueAt(0);
            assertEquals(2, innerList.size());
            assertEquals("x", innerList.getValueAt(0));
            assertEquals("y", innerList.getValueAt(1));
        }

        @Test
        void emptyInnerList() {
            SampleCompositeList obj = new SampleCompositeList();
            obj.setMatrix(List.of(List.of(), List.of("a")));

            SampleCompositeList restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(2, restored.getMatrix().size());
            assertTrue(restored.getMatrix().get(0).isEmpty());
            assertEquals(List.of("a"), restored.getMatrix().get(1));
        }

        @Test
        void nullMatrix_keyAbsent() {
            SampleCompositeList obj = new SampleCompositeList();
            obj.setMatrix(null);

            MetadataMap map = converter.toMetadataMap(obj);
            assertNull(map.get("matrix"));
        }
    }

    @Nested
    class ListOfMapStringInteger {

        @Test
        void roundTrip() {
            SampleCompositeList obj = new SampleCompositeList();
            Map<String, Integer> rec1 = new LinkedHashMap<>();
            rec1.put("score", 100);
            rec1.put("rank", 1);
            Map<String, Integer> rec2 = new LinkedHashMap<>();
            rec2.put("score", 85);
            obj.setRecords(List.of(rec1, rec2));

            SampleCompositeList restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(2, restored.getRecords().size());
            assertEquals(100, restored.getRecords().get(0).get("score"));
            assertEquals(1, restored.getRecords().get(0).get("rank"));
            assertEquals(85, restored.getRecords().get(1).get("score"));
        }

        @Test
        void metadataStructure() {
            SampleCompositeList obj = new SampleCompositeList();
            Map<String, Integer> rec = new LinkedHashMap<>();
            rec.put("val", 42);
            obj.setRecords(List.of(rec));

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataList outerList = (MetadataList) map.get("records");
            assertInstanceOf(MetadataMap.class, outerList.getValueAt(0));
        }

        @Test
        void emptyInnerMap() {
            SampleCompositeList obj = new SampleCompositeList();
            obj.setRecords(List.of(new LinkedHashMap<>()));

            SampleCompositeList restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(1, restored.getRecords().size());
            assertTrue(restored.getRecords().get(0).isEmpty());
        }
    }

    @Nested
    class ListOfListEnum {

        @Test
        void roundTrip() {
            SampleCompositeList obj = new SampleCompositeList();
            obj.setStatusGrid(List.of(
                    List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED),
                    List.of(OrderStatus.SHIPPED, OrderStatus.DELIVERED)
            ));

            SampleCompositeList restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(2, restored.getStatusGrid().size());
            assertEquals(List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED), restored.getStatusGrid().get(0));
            assertEquals(List.of(OrderStatus.SHIPPED, OrderStatus.DELIVERED), restored.getStatusGrid().get(1));
        }
    }

    @Nested
    class ListOfMapStringNested {

        @Test
        void roundTrip() {
            SampleCompositeList obj = new SampleCompositeList();
            Map<String, SampleNestedAddress> rec1 = new LinkedHashMap<>();
            rec1.put("home", new SampleNestedAddress("123 Main", "City", "11111"));
            rec1.put("work", new SampleNestedAddress("456 Office", "Town", "22222"));
            Map<String, SampleNestedAddress> rec2 = new LinkedHashMap<>();
            rec2.put("home", new SampleNestedAddress("789 Elm", "Village", "33333"));
            obj.setAddressRecords(List.of(rec1, rec2));

            SampleCompositeList restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(2, restored.getAddressRecords().size());
            assertEquals("123 Main", restored.getAddressRecords().get(0).get("home").getStreet());
            assertEquals("456 Office", restored.getAddressRecords().get(0).get("work").getStreet());
            assertEquals("789 Elm", restored.getAddressRecords().get(1).get("home").getStreet());
        }

        @Test
        void metadataStructure() {
            SampleCompositeList obj = new SampleCompositeList();
            Map<String, SampleNestedAddress> rec = new LinkedHashMap<>();
            rec.put("office", new SampleNestedAddress("10 Elm", "City", "44444"));
            obj.setAddressRecords(List.of(rec));

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataList outerList = (MetadataList) map.get("addressRecords");
            MetadataMap innerMap = (MetadataMap) outerList.getValueAt(0);
            Object officeVal = innerMap.get("office");
            assertInstanceOf(MetadataMap.class, officeVal);

            MetadataMap addrMap = (MetadataMap) officeVal;
            assertEquals("10 Elm", addrMap.get("street"));
        }
    }
}
