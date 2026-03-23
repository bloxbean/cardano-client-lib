package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.MetadataMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for nested {@code @MetadataType} composition.
 */
class SampleNestedMetadataConverterTest {

    SampleNestedMetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SampleNestedMetadataConverter();
    }

    @Nested
    class ScalarNested {

        @Test
        void roundTrip() {
            SampleNested obj = new SampleNested();
            obj.setOrderId("ORD-001");
            obj.setAddress(new SampleNestedAddress("123 Main St", "Springfield", "62704"));

            SampleNested restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals("ORD-001", restored.getOrderId());
            assertNotNull(restored.getAddress());
            assertEquals("123 Main St", restored.getAddress().getStreet());
            assertEquals("Springfield", restored.getAddress().getCity());
            assertEquals("62704", restored.getAddress().getZip());
        }

        @Test
        void nestedStoredAsMetadataMap() {
            SampleNested obj = new SampleNested();
            obj.setAddress(new SampleNestedAddress("456 Oak Ave", "Shelbyville", "62565"));

            MetadataMap map = converter.toMetadataMap(obj);
            Object addressVal = map.get("address");

            assertInstanceOf(MetadataMap.class, addressVal);
            MetadataMap addressMap = (MetadataMap) addressVal;
            assertEquals("456 Oak Ave", addressMap.get("street"));
        }

        @Test
        void nullNested_keyAbsent() {
            SampleNested obj = new SampleNested();
            obj.setOrderId("ORD-002");
            obj.setAddress(null);

            MetadataMap map = converter.toMetadataMap(obj);

            assertNull(map.get("address"));
            assertEquals("ORD-002", map.get("orderId"));
        }
    }

    @Nested
    class ListOfNested {

        @Test
        void roundTrip() {
            SampleNested obj = new SampleNested();
            obj.setItems(Arrays.asList(
                    new SampleNestedItem("Widget", BigInteger.valueOf(10)),
                    new SampleNestedItem("Gadget", BigInteger.valueOf(5))
            ));

            SampleNested restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getItems());
            assertEquals(2, restored.getItems().size());
            assertEquals("Widget", restored.getItems().get(0).getName());
            assertEquals(BigInteger.valueOf(10), restored.getItems().get(0).getQuantity());
            assertEquals("Gadget", restored.getItems().get(1).getName());
            assertEquals(BigInteger.valueOf(5), restored.getItems().get(1).getQuantity());
        }

        @Test
        void emptyList_roundTrip() {
            SampleNested obj = new SampleNested();
            obj.setItems(List.of());

            SampleNested restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getItems());
            assertTrue(restored.getItems().isEmpty());
        }
    }

    @Nested
    class OptionalNested {

        @Test
        void present_roundTrip() {
            SampleNested obj = new SampleNested();
            obj.setBillingAddress(Optional.of(
                    new SampleNestedAddress("789 Elm Blvd", "Capital City", "90210")));

            SampleNested restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertTrue(restored.getBillingAddress().isPresent());
            assertEquals("789 Elm Blvd", restored.getBillingAddress().get().getStreet());
            assertEquals("Capital City", restored.getBillingAddress().get().getCity());
        }

        @Test
        void absent_roundTrip() {
            SampleNested obj = new SampleNested();
            obj.setBillingAddress(Optional.empty());

            SampleNested restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertFalse(restored.getBillingAddress().isPresent());
        }
    }
}
