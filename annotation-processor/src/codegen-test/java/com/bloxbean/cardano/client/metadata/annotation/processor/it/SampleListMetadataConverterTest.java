package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.MetadataList;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the generated {@code SampleListMetadataConverter}.
 *
 * <p>The converter class is generated at compile time by {@code MetadataAnnotationProcessor}
 * from {@link SampleList}. These tests verify actual runtime round-trip behaviour
 * for all supported {@code List<T>} element types.
 */
class SampleListMetadataConverterTest {

    SampleListMetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SampleListMetadataConverter();
    }

    // =========================================================================
    // List<String>
    // =========================================================================

    @Nested
    class StringListFields {

        @Test
        void nonEmpty_roundTrip() {
            SampleList obj = new SampleList();
            obj.setTags(List.of("cardano", "defi", "nft"));

            SampleList restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(List.of("cardano", "defi", "nft"), restored.getTags());
        }

        @Test
        void emptyList_roundTrip() {
            SampleList obj = new SampleList();
            obj.setTags(List.of());

            SampleList restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getTags());
            assertTrue(restored.getTags().isEmpty());
        }

        @Test
        void nullList_notPresentInMap() {
            SampleList obj = new SampleList();
            obj.setTags(null);

            MetadataMap map = converter.toMetadataMap(obj);

            assertNull(map.get("tags"));
        }

        @Test
        void storedAsMetadataList() {
            SampleList obj = new SampleList();
            obj.setTags(List.of("hello"));

            MetadataMap map = converter.toMetadataMap(obj);

            assertTrue(map.get("tags") instanceof MetadataList);
        }

        @Test
        void longString_storedAsSubList() {
            String longTag = "a".repeat(70); // 70 bytes > 64
            SampleList obj = new SampleList();
            obj.setTags(List.of(longTag));

            MetadataMap map = converter.toMetadataMap(obj);

            MetadataList outer = (MetadataList) map.get("tags");
            assertNotNull(outer);
            // The single element should be a sub-MetadataList (chunked)
            assertTrue(outer.getValueAt(0) instanceof MetadataList);
        }

        @Test
        void longString_roundTrip() {
            String longTag = "b".repeat(70);
            SampleList obj = new SampleList();
            obj.setTags(List.of(longTag));

            SampleList restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(List.of(longTag), restored.getTags());
        }

        @Test
        void mixedShortAndLongStrings_roundTrip() {
            String shortStr = "short";
            String longStr = "c".repeat(70);
            SampleList obj = new SampleList();
            obj.setTags(List.of(shortStr, longStr, "another"));

            SampleList restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(List.of(shortStr, longStr, "another"), restored.getTags());
        }
    }

    // =========================================================================
    // List<BigInteger>
    // =========================================================================

    @Nested
    class BigIntegerListFields {

        @Test
        void nonEmpty_roundTrip() {
            SampleList obj = new SampleList();
            obj.setAmounts(List.of(BigInteger.valueOf(100), BigInteger.valueOf(200), BigInteger.ZERO));

            SampleList restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(List.of(BigInteger.valueOf(100), BigInteger.valueOf(200), BigInteger.ZERO),
                    restored.getAmounts());
        }

        @Test
        void negative_roundTrip() {
            SampleList obj = new SampleList();
            obj.setAmounts(List.of(BigInteger.valueOf(-100), new BigInteger("-999999999999999999")));

            SampleList restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(List.of(BigInteger.valueOf(-100), new BigInteger("-999999999999999999")),
                    restored.getAmounts());
        }

        @Test
        void nullList_notPresentInMap() {
            SampleList obj = new SampleList();
            obj.setAmounts(null);

            MetadataMap map = converter.toMetadataMap(obj);

            assertNull(map.get("amounts"));
        }

        @Test
        void storedAsMetadataList() {
            SampleList obj = new SampleList();
            obj.setAmounts(List.of(BigInteger.TEN));

            MetadataMap map = converter.toMetadataMap(obj);

            assertTrue(map.get("amounts") instanceof MetadataList);
        }
    }

    // =========================================================================
    // List<Integer>
    // =========================================================================

    @Nested
    class IntegerListFields {

        @Test
        void nonEmpty_roundTrip() {
            SampleList obj = new SampleList();
            obj.setIds(List.of(1, 2, 3, -10));

            SampleList restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(List.of(1, 2, 3, -10), restored.getIds());
        }

        @Test
        void negative_roundTrip() {
            SampleList obj = new SampleList();
            obj.setIds(List.of(-1, -42, -999));

            SampleList restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(List.of(-1, -42, -999), restored.getIds());
        }

        @Test
        void nullList_notPresentInMap() {
            SampleList obj = new SampleList();
            obj.setIds(null);

            MetadataMap map = converter.toMetadataMap(obj);

            assertNull(map.get("ids"));
        }

        @Test
        void emptyList_roundTrip() {
            SampleList obj = new SampleList();
            obj.setIds(List.of());

            SampleList restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getIds());
            assertTrue(restored.getIds().isEmpty());
        }
    }

    // =========================================================================
    // List<Boolean>
    // =========================================================================

    @Nested
    class BooleanListFields {

        @Test
        void nonEmpty_roundTrip() {
            SampleList obj = new SampleList();
            obj.setFlags(List.of(true, false, true));

            SampleList restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(List.of(true, false, true), restored.getFlags());
        }

        @Test
        void trueEncodedAsBigIntegerOne() {
            SampleList obj = new SampleList();
            obj.setFlags(List.of(true));

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataList flagList = (MetadataList) map.get("flags");

            assertEquals(BigInteger.ONE, flagList.getValueAt(0));
        }

        @Test
        void falseEncodedAsBigIntegerZero() {
            SampleList obj = new SampleList();
            obj.setFlags(List.of(false));

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataList flagList = (MetadataList) map.get("flags");

            assertEquals(BigInteger.ZERO, flagList.getValueAt(0));
        }
    }

    // =========================================================================
    // List<Double>
    // =========================================================================

    @Nested
    class DoubleListFields {

        @Test
        void nonEmpty_roundTrip() {
            SampleList obj = new SampleList();
            obj.setRates(List.of(1.5, 2.0, 3.14));

            SampleList restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getRates());
            assertEquals(3, restored.getRates().size());
            assertEquals(1.5, restored.getRates().get(0), 0.0001);
            assertEquals(2.0, restored.getRates().get(1), 0.0001);
            assertEquals(3.14, restored.getRates().get(2), 0.0001);
        }

        @Test
        void encodedAsString() {
            SampleList obj = new SampleList();
            obj.setRates(List.of(1.5));

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataList rateList = (MetadataList) map.get("rates");

            assertEquals("1.5", rateList.getValueAt(0));
        }
    }

    // =========================================================================
    // List<Long>
    // =========================================================================

    @Nested
    class LongListFields {

        @Test
        void nonEmpty_roundTrip() {
            SampleList obj = new SampleList();
            obj.setCounters(List.of(1_000_000_000L, -9_000_000_000L, 0L));

            SampleList restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(List.of(1_000_000_000L, -9_000_000_000L, 0L), restored.getCounters());
        }

        @Test
        void negative_roundTrip() {
            SampleList obj = new SampleList();
            obj.setCounters(List.of(-1L, -5_000_000_000L, -9_223_372_036_854_775_807L));

            SampleList restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(List.of(-1L, -5_000_000_000L, -9_223_372_036_854_775_807L), restored.getCounters());
        }

        @Test
        void storedAsBigInteger() {
            SampleList obj = new SampleList();
            obj.setCounters(List.of(42L));

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataList list = (MetadataList) map.get("counters");

            assertEquals(BigInteger.valueOf(42L), list.getValueAt(0));
        }

        @Test
        void nullList_notPresentInMap() {
            SampleList obj = new SampleList();
            obj.setCounters(null);

            assertNull(converter.toMetadataMap(obj).get("counters"));
        }
    }

    // =========================================================================
    // List<Short>
    // =========================================================================

    @Nested
    class ShortListFields {

        @Test
        void nonEmpty_roundTrip() {
            SampleList obj = new SampleList();
            obj.setCodes(List.of((short) 10, (short) -5, (short) 32767));

            SampleList restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(List.of((short) 10, (short) -5, (short) 32767), restored.getCodes());
        }

        @Test
        void storedAsBigInteger() {
            SampleList obj = new SampleList();
            obj.setCodes(List.of((short) 100));

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataList list = (MetadataList) map.get("codes");

            assertEquals(BigInteger.valueOf(100), list.getValueAt(0));
        }

        @Test
        void nullList_notPresentInMap() {
            SampleList obj = new SampleList();
            obj.setCodes(null);

            assertNull(converter.toMetadataMap(obj).get("codes"));
        }
    }

    // =========================================================================
    // List<Byte>
    // =========================================================================

    @Nested
    class ByteListFields {

        @Test
        void nonEmpty_roundTrip() {
            SampleList obj = new SampleList();
            obj.setByteValues(List.of((byte) 1, (byte) -1, (byte) 127));

            SampleList restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(List.of((byte) 1, (byte) -1, (byte) 127), restored.getByteValues());
        }

        @Test
        void storedAsBigInteger() {
            SampleList obj = new SampleList();
            obj.setByteValues(List.of((byte) 15));

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataList list = (MetadataList) map.get("byteValues");

            assertEquals(BigInteger.valueOf(15), list.getValueAt(0));
        }

        @Test
        void nullList_notPresentInMap() {
            SampleList obj = new SampleList();
            obj.setByteValues(null);

            assertNull(converter.toMetadataMap(obj).get("byteValues"));
        }
    }

    // =========================================================================
    // List<Float>
    // =========================================================================

    @Nested
    class FloatListFields {

        @Test
        void nonEmpty_roundTrip() {
            SampleList obj = new SampleList();
            obj.setFactors(List.of(1.5f, 0.5f, -3.0f));

            SampleList restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getFactors());
            assertEquals(3, restored.getFactors().size());
            assertEquals(1.5f, restored.getFactors().get(0), 0.0001f);
            assertEquals(0.5f, restored.getFactors().get(1), 0.0001f);
            assertEquals(-3.0f, restored.getFactors().get(2), 0.0001f);
        }

        @Test
        void storedAsString() {
            SampleList obj = new SampleList();
            obj.setFactors(List.of(2.0f));

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataList list = (MetadataList) map.get("factors");

            assertEquals("2.0", list.getValueAt(0));
        }

        @Test
        void nullList_notPresentInMap() {
            SampleList obj = new SampleList();
            obj.setFactors(null);

            assertNull(converter.toMetadataMap(obj).get("factors"));
        }
    }

    // =========================================================================
    // List<Character>
    // =========================================================================

    @Nested
    class CharacterListFields {

        @Test
        void nonEmpty_roundTrip() {
            SampleList obj = new SampleList();
            obj.setChars(List.of('A', 'z', '0'));

            SampleList restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(List.of('A', 'z', '0'), restored.getChars());
        }

        @Test
        void storedAsSingleCharString() {
            SampleList obj = new SampleList();
            obj.setChars(List.of('X'));

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataList list = (MetadataList) map.get("chars");

            assertEquals("X", list.getValueAt(0));
        }

        @Test
        void nullList_notPresentInMap() {
            SampleList obj = new SampleList();
            obj.setChars(null);

            assertNull(converter.toMetadataMap(obj).get("chars"));
        }
    }

    // =========================================================================
    // List<BigDecimal>
    // =========================================================================

    @Nested
    class BigDecimalListFields {

        @Test
        void nonEmpty_roundTrip() {
            SampleList obj = new SampleList();
            obj.setPrices(List.of(new BigDecimal("9.99"), new BigDecimal("100.00")));

            SampleList restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(List.of(new BigDecimal("9.99"), new BigDecimal("100.00")),
                    restored.getPrices());
        }

        @Test
        void encodedAsPlainString() {
            SampleList obj = new SampleList();
            obj.setPrices(List.of(new BigDecimal("1E+10")));

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataList priceList = (MetadataList) map.get("prices");

            assertEquals("10000000000", priceList.getValueAt(0));
        }

        @Test
        void nullList_notPresentInMap() {
            SampleList obj = new SampleList();
            obj.setPrices(null);

            MetadataMap map = converter.toMetadataMap(obj);

            assertNull(map.get("prices"));
        }
    }

    // =========================================================================
    // List<byte[]>
    // =========================================================================

    @Nested
    class ByteArrayListFields {

        @Test
        void nonEmpty_roundTrip() {
            byte[] a = {1, 2, 3};
            byte[] b = {4, 5, 6};
            SampleList obj = new SampleList();
            obj.setPayloads(List.of(a, b));

            SampleList restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getPayloads());
            assertEquals(2, restored.getPayloads().size());
            assertArrayEquals(a, restored.getPayloads().get(0));
            assertArrayEquals(b, restored.getPayloads().get(1));
        }

        @Test
        void storedAsBytesInMetadataList() {
            byte[] payload = {10, 20, 30};
            SampleList obj = new SampleList();
            obj.setPayloads(List.of(payload));

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataList list = (MetadataList) map.get("payloads");

            assertInstanceOf(byte[].class, list.getValueAt(0));
            assertArrayEquals(payload, (byte[]) list.getValueAt(0));
        }

        @Test
        void nullList_notPresentInMap() {
            SampleList obj = new SampleList();
            obj.setPayloads(null);

            MetadataMap map = converter.toMetadataMap(obj);

            assertNull(map.get("payloads"));
        }
    }
}
