package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.MetadataList;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the generated {@code SampleSortedSetMetadataConverter}.
 */
class SampleSortedSetMetadataConverterIT {

    SampleSortedSetMetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SampleSortedSetMetadataConverter();
    }

    @Nested
    class StringSortedSetFields {

        @Test
        void nonEmpty_roundTrip() {
            SampleSortedSet obj = new SampleSortedSet();
            obj.setTags(new TreeSet<>(java.util.Set.of("cardano", "defi", "nft")));

            SampleSortedSet restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getTags());
            assertEquals(3, restored.getTags().size());
            assertTrue(restored.getTags().containsAll(java.util.Set.of("cardano", "defi", "nft")));
        }

        @Test
        void returnedCollectionIsSortedSet() {
            SampleSortedSet obj = new SampleSortedSet();
            obj.setTags(new TreeSet<>(java.util.Set.of("a")));

            SampleSortedSet restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertInstanceOf(SortedSet.class, restored.getTags());
        }

        @Test
        void emptySet_roundTrip() {
            SampleSortedSet obj = new SampleSortedSet();
            obj.setTags(new TreeSet<>());

            SampleSortedSet restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getTags());
            assertTrue(restored.getTags().isEmpty());
        }

        @Test
        void nullSet_notPresentInMap() {
            SampleSortedSet obj = new SampleSortedSet();
            obj.setTags(null);

            MetadataMap map = converter.toMetadataMap(obj);

            assertNull(map.get("tags"));
        }

        @Test
        void storedAsMetadataList() {
            SampleSortedSet obj = new SampleSortedSet();
            obj.setTags(new TreeSet<>(java.util.Set.of("hello")));

            MetadataMap map = converter.toMetadataMap(obj);

            assertInstanceOf(MetadataList.class, map.get("tags"));
        }

        @Test
        void longString_storedAsSubList() {
            String longTag = "a".repeat(70);
            SampleSortedSet obj = new SampleSortedSet();
            obj.setTags(new TreeSet<>(java.util.Set.of(longTag)));

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataList outer = (MetadataList) map.get("tags");

            assertInstanceOf(MetadataList.class, outer.getValueAt(0));
        }

        @Test
        void longString_roundTrip() {
            String longTag = "b".repeat(70);
            SampleSortedSet obj = new SampleSortedSet();
            obj.setTags(new TreeSet<>(java.util.Set.of(longTag)));

            SampleSortedSet restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertTrue(restored.getTags().contains(longTag));
        }

        @Test
        void naturalOrdering_preservedOnRoundTrip() {
            SampleSortedSet obj = new SampleSortedSet();
            obj.setTags(new TreeSet<>(java.util.Set.of("zebra", "apple", "mango")));

            SampleSortedSet restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            // TreeSet maintains natural (lexicographic) order
            assertEquals("apple", restored.getTags().first());
            assertEquals("zebra", restored.getTags().last());
        }
    }

    @Nested
    class BigIntegerSortedSetFields {

        @Test
        void nonEmpty_roundTrip() {
            SampleSortedSet obj = new SampleSortedSet();
            obj.setAmounts(new TreeSet<>(java.util.Set.of(BigInteger.valueOf(100), BigInteger.valueOf(200))));

            SampleSortedSet restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertTrue(restored.getAmounts().containsAll(java.util.Set.of(BigInteger.valueOf(100), BigInteger.valueOf(200))));
        }

        @Test
        void nullSet_notPresentInMap() {
            SampleSortedSet obj = new SampleSortedSet();
            obj.setAmounts(null);

            assertNull(converter.toMetadataMap(obj).get("amounts"));
        }

        @Test
        void naturalOrdering_preservedOnRoundTrip() {
            SampleSortedSet obj = new SampleSortedSet();
            obj.setAmounts(new TreeSet<>(java.util.Set.of(BigInteger.valueOf(300), BigInteger.valueOf(100), BigInteger.valueOf(200))));

            SampleSortedSet restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(BigInteger.valueOf(100), restored.getAmounts().first());
            assertEquals(BigInteger.valueOf(300), restored.getAmounts().last());
        }
    }

    @Nested
    class IntegerSortedSetFields {

        @Test
        void nonEmpty_roundTrip() {
            SampleSortedSet obj = new SampleSortedSet();
            obj.setIds(new TreeSet<>(java.util.Set.of(3, 1, 2)));

            SampleSortedSet restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertTrue(restored.getIds().containsAll(java.util.Set.of(1, 2, 3)));
        }

        @Test
        void returnedCollectionIsSortedSet() {
            SampleSortedSet obj = new SampleSortedSet();
            obj.setIds(new TreeSet<>(java.util.Set.of(42)));

            SampleSortedSet restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertInstanceOf(SortedSet.class, restored.getIds());
        }
    }

    @Nested
    class BooleanSortedSetFields {

        @Test
        void nonEmpty_roundTrip() {
            SampleSortedSet obj = new SampleSortedSet();
            obj.setFlags(new TreeSet<>(java.util.Set.of(true, false)));

            SampleSortedSet restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertTrue(restored.getFlags().containsAll(java.util.Set.of(true, false)));
        }
    }

    @Nested
    class BigDecimalSortedSetFields {

        @Test
        void nonEmpty_roundTrip() {
            SampleSortedSet obj = new SampleSortedSet();
            obj.setPrices(new TreeSet<>(java.util.Set.of(new BigDecimal("9.99"), new BigDecimal("19.99"))));

            SampleSortedSet restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertTrue(restored.getPrices().containsAll(java.util.Set.of(new BigDecimal("9.99"), new BigDecimal("19.99"))));
        }

        @Test
        void nullSet_notPresentInMap() {
            SampleSortedSet obj = new SampleSortedSet();
            obj.setPrices(null);

            assertNull(converter.toMetadataMap(obj).get("prices"));
        }
    }
}
