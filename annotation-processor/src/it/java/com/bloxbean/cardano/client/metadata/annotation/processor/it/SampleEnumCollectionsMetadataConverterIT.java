package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.MetadataList;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the generated {@code SampleEnumCollectionsMetadataConverter}.
 */
class SampleEnumCollectionsMetadataConverterIT {

    SampleEnumCollectionsMetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SampleEnumCollectionsMetadataConverter();
    }

    // =========================================================================
    // List<OrderStatus>
    // =========================================================================

    @Nested
    class EnumListField {

        @Test
        void roundTrip() {
            SampleEnumCollections obj = new SampleEnumCollections();
            obj.setStatusList(List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.SHIPPED));

            SampleEnumCollections restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.SHIPPED),
                    restored.getStatusList());
        }

        @Test
        void storedAsMetadataListOfStrings() {
            SampleEnumCollections obj = new SampleEnumCollections();
            obj.setStatusList(List.of(OrderStatus.DELIVERED, OrderStatus.PENDING));

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataList list = (MetadataList) map.get("statusList");

            assertNotNull(list);
            assertEquals("DELIVERED", list.getValueAt(0));
            assertEquals("PENDING", list.getValueAt(1));
        }

        @Test
        void emptyList_roundTrip() {
            SampleEnumCollections obj = new SampleEnumCollections();
            obj.setStatusList(List.of());

            SampleEnumCollections restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getStatusList());
            assertTrue(restored.getStatusList().isEmpty());
        }

        @Test
        void nullList_keyAbsentInMap() {
            SampleEnumCollections obj = new SampleEnumCollections();
            obj.setStatusList(null);

            assertNull(converter.toMetadataMap(obj).get("statusList"));
        }
    }

    // =========================================================================
    // Set<OrderStatus>
    // =========================================================================

    @Nested
    class EnumSetField {

        @Test
        void roundTrip() {
            SampleEnumCollections obj = new SampleEnumCollections();
            obj.setStatusSet(Set.of(OrderStatus.PENDING, OrderStatus.CONFIRMED));

            SampleEnumCollections restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(Set.of(OrderStatus.PENDING, OrderStatus.CONFIRMED), restored.getStatusSet());
        }

        @Test
        void storedAsMetadataList() {
            SampleEnumCollections obj = new SampleEnumCollections();
            obj.setStatusSet(Set.of(OrderStatus.SHIPPED));

            MetadataMap map = converter.toMetadataMap(obj);

            assertInstanceOf(MetadataList.class, map.get("statusSet"));
        }

        @Test
        void nullSet_keyAbsentInMap() {
            SampleEnumCollections obj = new SampleEnumCollections();
            obj.setStatusSet(null);

            assertNull(converter.toMetadataMap(obj).get("statusSet"));
        }
    }

    // =========================================================================
    // SortedSet<OrderStatus>
    // =========================================================================

    @Nested
    class EnumSortedSetField {

        @Test
        void roundTrip() {
            SampleEnumCollections obj = new SampleEnumCollections();
            SortedSet<OrderStatus> input = new TreeSet<>(
                    List.of(OrderStatus.SHIPPED, OrderStatus.PENDING, OrderStatus.CONFIRMED));
            obj.setSortedStatuses(input);

            SampleEnumCollections restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            // TreeSet uses natural (declaration) order of enum constants
            assertNotNull(restored.getSortedStatuses());
            assertEquals(input, restored.getSortedStatuses());
        }

        @Test
        void naturalOrderPreserved_onRoundTrip() {
            SampleEnumCollections obj = new SampleEnumCollections();
            // Insert in reverse declaration order — TreeSet will sort to declaration order
            SortedSet<OrderStatus> input = new TreeSet<>(
                    List.of(OrderStatus.DELIVERED, OrderStatus.PENDING));
            obj.setSortedStatuses(input);

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataList list = (MetadataList) map.get("sortedStatuses");

            // PENDING < DELIVERED by enum declaration order
            assertEquals("PENDING", list.getValueAt(0));
            assertEquals("DELIVERED", list.getValueAt(1));
        }

        @Test
        void nullSortedSet_keyAbsentInMap() {
            SampleEnumCollections obj = new SampleEnumCollections();
            obj.setSortedStatuses(null);

            assertNull(converter.toMetadataMap(obj).get("sortedStatuses"));
        }
    }

    // =========================================================================
    // Optional<OrderStatus>
    // =========================================================================

    @Nested
    class EnumOptionalField {

        @Test
        void present_roundTrip() {
            SampleEnumCollections obj = new SampleEnumCollections();
            obj.setOptionalStatus(Optional.of(OrderStatus.CONFIRMED));

            SampleEnumCollections restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(Optional.of(OrderStatus.CONFIRMED), restored.getOptionalStatus());
        }

        @Test
        void present_storedAsEnumName() {
            SampleEnumCollections obj = new SampleEnumCollections();
            obj.setOptionalStatus(Optional.of(OrderStatus.SHIPPED));

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals("SHIPPED", map.get("optionalStatus"));
        }

        @Test
        void empty_keyAbsentInMap() {
            SampleEnumCollections obj = new SampleEnumCollections();
            obj.setOptionalStatus(Optional.empty());

            assertNull(converter.toMetadataMap(obj).get("optionalStatus"));
        }

        @Test
        void absent_deserializesToOptionalEmpty() {
            SampleEnumCollections restored = converter.fromMetadataMap(
                    converter.toMetadataMap(new SampleEnumCollections()));

            assertEquals(Optional.empty(), restored.getOptionalStatus());
        }
    }
}
