package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.MetadataMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the generated {@code SamplePrimitivesMetadataConverter}.
 *
 * <p>The converter class is generated at compile time by {@code MetadataAnnotationProcessor}
 * from {@link SamplePrimitives}. These tests verify actual runtime round-trip behaviour
 * for all newly supported primitive and boxed types.
 */
class SamplePrimitivesMetadataConverterIT {

    SamplePrimitivesMetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SamplePrimitivesMetadataConverter();
    }

    // =========================================================================
    // short / Short
    // =========================================================================

    @Nested
    class ShortFields {

        @Test
        void primitive_serialisedAsBigInteger() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setShortPrimitive((short) 42);

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals(BigInteger.valueOf(42), map.get("shortPrimitive"));
        }

        @Test
        void primitive_negative_serialisedAsBigInteger() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setShortPrimitive((short) -100);

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals(BigInteger.valueOf(-100), map.get("shortPrimitive"));
        }

        @Test
        void primitive_roundTrip() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setShortPrimitive((short) 1234);

            SamplePrimitives restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals((short) 1234, restored.getShortPrimitive());
        }

        @Test
        void boxed_presentValue_serialisedAsBigInteger() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setShortBoxed((short) 7);

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals(BigInteger.valueOf(7), map.get("shortBoxed"));
        }

        @Test
        void boxed_nullValue_notPresentInMap() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setShortBoxed(null);

            MetadataMap map = converter.toMetadataMap(obj);

            assertNull(map.get("shortBoxed"));
        }

        @Test
        void boxed_roundTrip() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setShortBoxed((short) -5);

            SamplePrimitives restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals((Short) (short) -5, restored.getShortBoxed());
        }

        @Test
        void asString_serialisedAsString() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setShortAsString((short) 99);

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals("99", map.get("shortStr"));
        }

        @Test
        void asString_roundTrip() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setShortAsString((short) 99);

            SamplePrimitives restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals((short) 99, restored.getShortAsString());
        }
    }

    // =========================================================================
    // byte / Byte
    // =========================================================================

    @Nested
    class ByteFields {

        @Test
        void primitive_serialisedAsBigInteger() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setBytePrimitive((byte) 10);

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals(BigInteger.valueOf(10), map.get("bytePrimitive"));
        }

        @Test
        void primitive_negative_serialisedAsBigInteger() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setBytePrimitive((byte) -5);

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals(BigInteger.valueOf(-5), map.get("bytePrimitive"));
        }

        @Test
        void primitive_roundTrip() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setBytePrimitive((byte) 127);

            SamplePrimitives restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals((byte) 127, restored.getBytePrimitive());
        }

        @Test
        void boxed_nullValue_notPresentInMap() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setByteBoxed(null);

            MetadataMap map = converter.toMetadataMap(obj);

            assertNull(map.get("byteBoxed"));
        }

        @Test
        void boxed_roundTrip() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setByteBoxed((byte) 33);

            SamplePrimitives restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals((Byte) (byte) 33, restored.getByteBoxed());
        }

        @Test
        void asString_serialisedAsString() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setByteAsString((byte) 15);

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals("15", map.get("byteStr"));
        }

        @Test
        void asString_roundTrip() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setByteAsString((byte) 15);

            SamplePrimitives restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals((byte) 15, restored.getByteAsString());
        }
    }

    // =========================================================================
    // boolean / Boolean
    // =========================================================================

    @Nested
    class BooleanFields {

        @Test
        void primitive_true_serialisedAsBigIntegerOne() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setBoolPrimitive(true);

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals(BigInteger.ONE, map.get("boolPrimitive"));
        }

        @Test
        void primitive_false_serialisedAsBigIntegerZero() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setBoolPrimitive(false);

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals(BigInteger.ZERO, map.get("boolPrimitive"));
        }

        @Test
        void primitive_roundTrip_true() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setBoolPrimitive(true);

            SamplePrimitives restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertTrue(restored.isBoolPrimitive());
        }

        @Test
        void primitive_roundTrip_false() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setBoolPrimitive(false);

            SamplePrimitives restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertFalse(restored.isBoolPrimitive());
        }

        @Test
        void boxed_nullValue_notPresentInMap() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setBoolBoxed(null);

            MetadataMap map = converter.toMetadataMap(obj);

            assertNull(map.get("boolBoxed"));
        }

        @Test
        void boxed_roundTrip_true() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setBoolBoxed(true);

            SamplePrimitives restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(Boolean.TRUE, restored.isBoolBoxed());
        }

        @Test
        void asString_true_serialisedAsStringTrue() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setBoolAsString(true);

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals("true", map.get("boolStr"));
        }

        @Test
        void asString_false_serialisedAsStringFalse() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setBoolAsString(false);

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals("false", map.get("boolStr"));
        }

        @Test
        void asString_roundTrip_true() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setBoolAsString(true);

            SamplePrimitives restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertTrue(restored.isBoolAsString());
        }

        @Test
        void asString_roundTrip_false() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setBoolAsString(false);

            SamplePrimitives restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertFalse(restored.isBoolAsString());
        }
    }

    // =========================================================================
    // double / Double
    // =========================================================================

    @Nested
    class DoubleFields {

        @Test
        void primitive_serialisedAsString() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setDoublePrimitive(3.14);

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals("3.14", map.get("doublePrimitive"));
        }

        @Test
        void primitive_roundTrip() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setDoublePrimitive(2.718);

            SamplePrimitives restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(2.718, restored.getDoublePrimitive(), 0.0001);
        }

        @Test
        void boxed_nullValue_notPresentInMap() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setDoubleBoxed(null);

            MetadataMap map = converter.toMetadataMap(obj);

            assertNull(map.get("doubleBoxed"));
        }

        @Test
        void boxed_roundTrip() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setDoubleBoxed(1.5);

            SamplePrimitives restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(1.5, restored.getDoubleBoxed(), 0.0001);
        }
    }

    // =========================================================================
    // float / Float
    // =========================================================================

    @Nested
    class FloatFields {

        @Test
        void primitive_serialisedAsString() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setFloatPrimitive(1.5f);

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals("1.5", map.get("floatPrimitive"));
        }

        @Test
        void primitive_roundTrip() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setFloatPrimitive(2.0f);

            SamplePrimitives restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(2.0f, restored.getFloatPrimitive(), 0.0001f);
        }

        @Test
        void boxed_nullValue_notPresentInMap() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setFloatBoxed(null);

            MetadataMap map = converter.toMetadataMap(obj);

            assertNull(map.get("floatBoxed"));
        }

        @Test
        void boxed_roundTrip() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setFloatBoxed(0.5f);

            SamplePrimitives restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(0.5f, restored.getFloatBoxed(), 0.0001f);
        }
    }

    // =========================================================================
    // char / Character
    // =========================================================================

    @Nested
    class CharFields {

        @Test
        void primitive_serialisedAsSingleCharString() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setCharPrimitive('A');

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals("A", map.get("charPrimitive"));
        }

        @Test
        void primitive_roundTrip() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setCharPrimitive('Z');

            SamplePrimitives restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals('Z', restored.getCharPrimitive());
        }

        @Test
        void boxed_nullValue_notPresentInMap() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setCharBoxed(null);

            MetadataMap map = converter.toMetadataMap(obj);

            assertNull(map.get("charBoxed"));
        }

        @Test
        void boxed_roundTrip() {
            SamplePrimitives obj = new SamplePrimitives();
            obj.setCharBoxed('x');

            SamplePrimitives restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(Character.valueOf('x'), restored.getCharBoxed());
        }
    }
}
