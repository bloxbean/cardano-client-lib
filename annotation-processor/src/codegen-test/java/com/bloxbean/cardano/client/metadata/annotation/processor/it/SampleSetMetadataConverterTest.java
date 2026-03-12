package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.MetadataList;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the generated {@code SampleSetMetadataConverter}.
 */
class SampleSetMetadataConverterTest {

    SampleSetMetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SampleSetMetadataConverter();
    }

    @Nested
    class StringSetFields {

        @Test
        void nonEmpty_roundTrip() {
            SampleSet obj = new SampleSet();
            obj.setTags(new LinkedHashSet<>(Set.of("cardano", "defi", "nft")));

            SampleSet restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getTags());
            assertEquals(3, restored.getTags().size());
            assertTrue(restored.getTags().containsAll(Set.of("cardano", "defi", "nft")));
        }

        @Test
        void returnedCollectionIsSet() {
            SampleSet obj = new SampleSet();
            obj.setTags(new LinkedHashSet<>(Set.of("a")));

            SampleSet restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertInstanceOf(Set.class, restored.getTags());
        }

        @Test
        void emptySet_roundTrip() {
            SampleSet obj = new SampleSet();
            obj.setTags(new LinkedHashSet<>());

            SampleSet restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getTags());
            assertTrue(restored.getTags().isEmpty());
        }

        @Test
        void nullSet_notPresentInMap() {
            SampleSet obj = new SampleSet();
            obj.setTags(null);

            MetadataMap map = converter.toMetadataMap(obj);

            assertNull(map.get("tags"));
        }

        @Test
        void storedAsMetadataList() {
            SampleSet obj = new SampleSet();
            obj.setTags(new LinkedHashSet<>(Set.of("hello")));

            MetadataMap map = converter.toMetadataMap(obj);

            assertInstanceOf(MetadataList.class, map.get("tags"));
        }

        @Test
        void longString_storedAsSubList() {
            String longTag = "a".repeat(70);
            SampleSet obj = new SampleSet();
            obj.setTags(new LinkedHashSet<>(Set.of(longTag)));

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataList outer = (MetadataList) map.get("tags");

            assertInstanceOf(MetadataList.class, outer.getValueAt(0));
        }

        @Test
        void longString_roundTrip() {
            String longTag = "b".repeat(70);
            SampleSet obj = new SampleSet();
            obj.setTags(new LinkedHashSet<>(Set.of(longTag)));

            SampleSet restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertTrue(restored.getTags().contains(longTag));
        }
    }

    @Nested
    class BigIntegerSetFields {

        @Test
        void nonEmpty_roundTrip() {
            SampleSet obj = new SampleSet();
            obj.setAmounts(new LinkedHashSet<>(Set.of(BigInteger.valueOf(100), BigInteger.valueOf(200))));

            SampleSet restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertTrue(restored.getAmounts().containsAll(Set.of(BigInteger.valueOf(100), BigInteger.valueOf(200))));
        }

        @Test
        void nullSet_notPresentInMap() {
            SampleSet obj = new SampleSet();
            obj.setAmounts(null);

            assertNull(converter.toMetadataMap(obj).get("amounts"));
        }
    }

    @Nested
    class IntegerSetFields {

        @Test
        void nonEmpty_roundTrip() {
            SampleSet obj = new SampleSet();
            obj.setIds(new LinkedHashSet<>(Set.of(1, 2, 3)));

            SampleSet restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertTrue(restored.getIds().containsAll(Set.of(1, 2, 3)));
        }

        @Test
        void returnedCollectionIsSet() {
            SampleSet obj = new SampleSet();
            obj.setIds(new LinkedHashSet<>(Set.of(42)));

            SampleSet restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertInstanceOf(Set.class, restored.getIds());
        }
    }

    @Nested
    class BooleanSetFields {

        @Test
        void nonEmpty_roundTrip() {
            SampleSet obj = new SampleSet();
            obj.setFlags(new LinkedHashSet<>(Set.of(true, false)));

            SampleSet restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertTrue(restored.getFlags().containsAll(Set.of(true, false)));
        }
    }

    @Nested
    class BigDecimalSetFields {

        @Test
        void nonEmpty_roundTrip() {
            SampleSet obj = new SampleSet();
            obj.setPrices(new LinkedHashSet<>(Set.of(new BigDecimal("9.99"), new BigDecimal("19.99"))));

            SampleSet restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertTrue(restored.getPrices().containsAll(Set.of(new BigDecimal("9.99"), new BigDecimal("19.99"))));
        }

        @Test
        void nullSet_notPresentInMap() {
            SampleSet obj = new SampleSet();
            obj.setPrices(null);

            assertNull(converter.toMetadataMap(obj).get("prices"));
        }
    }

    @Nested
    class LongSetFields {

        @Test
        void nonEmpty_roundTrip() {
            SampleSet obj = new SampleSet();
            obj.setCounters(new LinkedHashSet<>(Set.of(100L, 200L, -50L)));

            SampleSet restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertTrue(restored.getCounters().containsAll(Set.of(100L, 200L, -50L)));
        }

        @Test
        void nullSet_notPresentInMap() {
            SampleSet obj = new SampleSet();
            obj.setCounters(null);

            assertNull(converter.toMetadataMap(obj).get("counters"));
        }

        @Test
        void storedAsBigInteger() {
            SampleSet obj = new SampleSet();
            obj.setCounters(new LinkedHashSet<>(Set.of(42L)));

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataList list = (MetadataList) map.get("counters");

            assertEquals(BigInteger.valueOf(42L), list.getValueAt(0));
        }
    }

    @Nested
    class ShortSetFields {

        @Test
        void nonEmpty_roundTrip() {
            SampleSet obj = new SampleSet();
            obj.setCodes(new LinkedHashSet<>(Set.of((short) 10, (short) -5)));

            SampleSet restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertTrue(restored.getCodes().containsAll(Set.of((short) 10, (short) -5)));
        }

        @Test
        void nullSet_notPresentInMap() {
            SampleSet obj = new SampleSet();
            obj.setCodes(null);

            assertNull(converter.toMetadataMap(obj).get("codes"));
        }
    }

    @Nested
    class ByteValueSetFields {

        @Test
        void nonEmpty_roundTrip() {
            SampleSet obj = new SampleSet();
            obj.setByteValues(new LinkedHashSet<>(Set.of((byte) 1, (byte) -1, (byte) 127)));

            SampleSet restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertTrue(restored.getByteValues().containsAll(Set.of((byte) 1, (byte) -1, (byte) 127)));
        }

        @Test
        void nullSet_notPresentInMap() {
            SampleSet obj = new SampleSet();
            obj.setByteValues(null);

            assertNull(converter.toMetadataMap(obj).get("byteValues"));
        }
    }

    @Nested
    class DoubleSetFields {

        @Test
        void nonEmpty_roundTrip() {
            SampleSet obj = new SampleSet();
            obj.setRates(new LinkedHashSet<>(Set.of(1.5, 2.0, 3.14)));

            SampleSet restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getRates());
            assertEquals(3, restored.getRates().size());
            assertTrue(restored.getRates().contains(1.5));
            assertTrue(restored.getRates().contains(2.0));
        }

        @Test
        void storedAsString() {
            SampleSet obj = new SampleSet();
            obj.setRates(new LinkedHashSet<>(Set.of(1.5)));

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataList list = (MetadataList) map.get("rates");

            assertEquals("1.5", list.getValueAt(0));
        }

        @Test
        void nullSet_notPresentInMap() {
            SampleSet obj = new SampleSet();
            obj.setRates(null);

            assertNull(converter.toMetadataMap(obj).get("rates"));
        }
    }

    @Nested
    class FloatSetFields {

        @Test
        void nonEmpty_roundTrip() {
            SampleSet obj = new SampleSet();
            obj.setFactors(new LinkedHashSet<>(Set.of(1.5f, 2.0f)));

            SampleSet restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getFactors());
            assertTrue(restored.getFactors().contains(1.5f));
            assertTrue(restored.getFactors().contains(2.0f));
        }

        @Test
        void nullSet_notPresentInMap() {
            SampleSet obj = new SampleSet();
            obj.setFactors(null);

            assertNull(converter.toMetadataMap(obj).get("factors"));
        }
    }

    @Nested
    class CharacterSetFields {

        @Test
        void nonEmpty_roundTrip() {
            SampleSet obj = new SampleSet();
            obj.setChars(new LinkedHashSet<>(Set.of('A', 'B', 'C')));

            SampleSet restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertTrue(restored.getChars().containsAll(Set.of('A', 'B', 'C')));
        }

        @Test
        void storedAsSingleCharString() {
            SampleSet obj = new SampleSet();
            obj.setChars(new LinkedHashSet<>(Set.of('X')));

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataList list = (MetadataList) map.get("chars");

            assertEquals("X", list.getValueAt(0));
        }

        @Test
        void nullSet_notPresentInMap() {
            SampleSet obj = new SampleSet();
            obj.setChars(null);

            assertNull(converter.toMetadataMap(obj).get("chars"));
        }
    }

    @Nested
    class ByteArraySetFields {

        @Test
        void nullSet_notPresentInMap() {
            SampleSet obj = new SampleSet();
            obj.setPayloads(null);

            assertNull(converter.toMetadataMap(obj).get("payloads"));
        }

        @Test
        void storedAsBytesInMetadataList() {
            byte[] payload = {10, 20, 30};
            SampleSet obj = new SampleSet();
            LinkedHashSet<byte[]> set = new LinkedHashSet<>();
            set.add(payload);
            obj.setPayloads(set);

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataList list = (MetadataList) map.get("payloads");

            assertInstanceOf(byte[].class, list.getValueAt(0));
            assertArrayEquals(payload, (byte[]) list.getValueAt(0));
        }
    }
}
