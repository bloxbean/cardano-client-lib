package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.MetadataMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the generated {@code SampleOptionalMetadataConverter}.
 */
class SampleOptionalMetadataConverterIT {

    SampleOptionalMetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SampleOptionalMetadataConverter();
    }

    @Nested
    class StringOptionalField {

        @Test
        void present_roundTrips() {
            SampleOptional obj = new SampleOptional();
            obj.setLabel(Optional.of("hello"));

            SampleOptional restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(Optional.of("hello"), restored.getLabel());
        }

        @Test
        void nullOptional_keyAbsentInMap() {
            SampleOptional obj = new SampleOptional();
            obj.setLabel(null);

            MetadataMap map = converter.toMetadataMap(obj);

            assertNull(map.get("label"));
        }

        @Test
        void emptyOptional_keyAbsentInMap() {
            SampleOptional obj = new SampleOptional();
            obj.setLabel(Optional.empty());

            MetadataMap map = converter.toMetadataMap(obj);

            assertNull(map.get("label"));
        }

        @Test
        void emptyOptional_deserializesToOptionalEmpty() {
            SampleOptional obj = new SampleOptional();
            obj.setLabel(Optional.empty());

            SampleOptional restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getLabel());
            assertFalse(restored.getLabel().isPresent());
        }

        @Test
        void returnedField_isInstanceOfOptional() {
            SampleOptional obj = new SampleOptional();
            obj.setLabel(Optional.of("test"));

            SampleOptional restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertInstanceOf(Optional.class, restored.getLabel());
        }

        @Test
        void longString_chunked_roundTrips() {
            String longValue = "x".repeat(70);
            SampleOptional obj = new SampleOptional();
            obj.setLabel(Optional.of(longValue));

            SampleOptional restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(Optional.of(longValue), restored.getLabel());
        }
    }

    @Nested
    class BigIntegerOptionalField {

        @Test
        void present_roundTrips() {
            SampleOptional obj = new SampleOptional();
            obj.setAmount(Optional.of(BigInteger.valueOf(42)));

            SampleOptional restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(Optional.of(BigInteger.valueOf(42)), restored.getAmount());
        }

        @Test
        void nullOptional_keyAbsentInMap() {
            SampleOptional obj = new SampleOptional();
            obj.setAmount(null);

            assertNull(converter.toMetadataMap(obj).get("amount"));
        }

        @Test
        void emptyOptional_deserializesToOptionalEmpty() {
            SampleOptional obj = new SampleOptional();
            obj.setAmount(Optional.empty());

            SampleOptional restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertFalse(restored.getAmount().isPresent());
        }
    }

    @Nested
    class IntegerOptionalField {

        @Test
        void present_roundTrips() {
            SampleOptional obj = new SampleOptional();
            obj.setCount(Optional.of(7));

            SampleOptional restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(Optional.of(7), restored.getCount());
        }

        @Test
        void emptyOptional_deserializesToOptionalEmpty() {
            SampleOptional obj = new SampleOptional();
            obj.setCount(Optional.empty());

            SampleOptional restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertFalse(restored.getCount().isPresent());
        }
    }

    @Nested
    class BooleanOptionalField {

        @Test
        void present_roundTrips() {
            SampleOptional obj = new SampleOptional();
            obj.setActive(Optional.of(true));

            SampleOptional restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(Optional.of(true), restored.getActive());
        }

        @Test
        void emptyOptional_deserializesToOptionalEmpty() {
            SampleOptional obj = new SampleOptional();
            obj.setActive(Optional.empty());

            SampleOptional restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertFalse(restored.getActive().isPresent());
        }
    }

    @Nested
    class BigDecimalOptionalField {

        @Test
        void present_roundTrips() {
            SampleOptional obj = new SampleOptional();
            obj.setPrice(Optional.of(new BigDecimal("9.99")));

            SampleOptional restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(Optional.of(new BigDecimal("9.99")), restored.getPrice());
        }

        @Test
        void emptyOptional_deserializesToOptionalEmpty() {
            SampleOptional obj = new SampleOptional();
            obj.setPrice(Optional.empty());

            SampleOptional restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertFalse(restored.getPrice().isPresent());
        }
    }

    @Nested
    class ByteArrayOptionalField {

        @Test
        void present_roundTrips() {
            byte[] data = {1, 2, 3};
            SampleOptional obj = new SampleOptional();
            obj.setPayload(Optional.of(data));

            SampleOptional restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertTrue(restored.getPayload().isPresent());
            assertArrayEquals(data, restored.getPayload().get());
        }

        @Test
        void emptyOptional_deserializesToOptionalEmpty() {
            SampleOptional obj = new SampleOptional();
            obj.setPayload(Optional.empty());

            SampleOptional restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertFalse(restored.getPayload().isPresent());
        }
    }
}
