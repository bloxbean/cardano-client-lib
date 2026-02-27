package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.MetadataMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the generated {@code SampleEnumMetadataConverter}.
 *
 * <p>The converter is generated at compile time by {@code MetadataAnnotationProcessor}
 * from {@link SampleEnum}. Tests verify actual runtime round-trip behaviour for enum fields.
 */
class SampleEnumMetadataConverterIT {

    SampleEnumMetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SampleEnumMetadataConverter();
    }

    @Nested
    class DefaultKey {

        @Test
        void roundTrip() {
            SampleEnum obj = new SampleEnum();
            obj.setStatus(OrderStatus.CONFIRMED);

            SampleEnum restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(OrderStatus.CONFIRMED, restored.getStatus());
        }

        @Test
        void allConstants_roundTrip() {
            for (OrderStatus s : OrderStatus.values()) {
                SampleEnum obj = new SampleEnum();
                obj.setStatus(s);

                SampleEnum restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

                assertEquals(s, restored.getStatus());
            }
        }

        @Test
        void storedAsEnumName() {
            SampleEnum obj = new SampleEnum();
            obj.setStatus(OrderStatus.SHIPPED);

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals("SHIPPED", map.get("status"));
        }

        @Test
        void nullEnum_keyAbsentInMap() {
            SampleEnum obj = new SampleEnum();
            obj.setStatus(null);

            MetadataMap map = converter.toMetadataMap(obj);

            assertNull(map.get("status"));
        }
    }

    @Nested
    class CustomKey {

        @Test
        void roundTrip() {
            SampleEnum obj = new SampleEnum();
            obj.setStatusWithKey(OrderStatus.DELIVERED);

            SampleEnum restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(OrderStatus.DELIVERED, restored.getStatusWithKey());
        }

        @Test
        void storedUnderCustomKey() {
            SampleEnum obj = new SampleEnum();
            obj.setStatusWithKey(OrderStatus.PENDING);

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals("PENDING", map.get("st"));
            assertNull(map.get("statusWithKey"));
        }
    }
}
