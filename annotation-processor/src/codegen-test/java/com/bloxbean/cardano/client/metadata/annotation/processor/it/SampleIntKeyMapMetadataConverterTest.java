package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.MetadataMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@code Map<Integer/Long/BigInteger, V>} field support.
 */
class SampleIntKeyMapMetadataConverterTest {

    SampleIntKeyMapMetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SampleIntKeyMapMetadataConverter();
    }

    @Nested
    class IntegerKeys {

        @Test
        void roundTrip() {
            SampleIntKeyMap obj = new SampleIntKeyMap();
            Map<Integer, String> settings = new LinkedHashMap<>();
            settings.put(1, "dark");
            settings.put(2, "en");
            obj.setIntKeyedSettings(settings);

            SampleIntKeyMap restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals("dark", restored.getIntKeyedSettings().get(1));
            assertEquals("en", restored.getIntKeyedSettings().get(2));
            assertEquals(2, restored.getIntKeyedSettings().size());
        }

        @Test
        void emptyMap_roundTrip() {
            SampleIntKeyMap obj = new SampleIntKeyMap();
            obj.setIntKeyedSettings(new LinkedHashMap<>());

            SampleIntKeyMap restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getIntKeyedSettings());
            assertTrue(restored.getIntKeyedSettings().isEmpty());
        }

        @Test
        void nullMap_keyAbsent() {
            SampleIntKeyMap obj = new SampleIntKeyMap();
            obj.setIntKeyedSettings(null);

            MetadataMap map = converter.toMetadataMap(obj);

            assertNull(map.get("intKeyedSettings"));
        }

        @Test
        void serializesKeysAsBigInteger() {
            SampleIntKeyMap obj = new SampleIntKeyMap();
            Map<Integer, String> settings = new LinkedHashMap<>();
            settings.put(42, "value");
            obj.setIntKeyedSettings(settings);

            MetadataMap map = converter.toMetadataMap(obj);
            Object settingsVal = map.get("intKeyedSettings");
            assertInstanceOf(MetadataMap.class, settingsVal);

            MetadataMap settingsMap = (MetadataMap) settingsVal;
            assertEquals("value", settingsMap.get(BigInteger.valueOf(42)));
        }
    }

    @Nested
    class LongKeys {

        @Test
        void roundTrip() {
            SampleIntKeyMap obj = new SampleIntKeyMap();
            Map<Long, String> labels = new LinkedHashMap<>();
            labels.put(100L, "alpha");
            labels.put(200L, "beta");
            obj.setLongKeyedLabels(labels);

            SampleIntKeyMap restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals("alpha", restored.getLongKeyedLabels().get(100L));
            assertEquals("beta", restored.getLongKeyedLabels().get(200L));
        }
    }

    @Nested
    class BigIntegerKeys {

        @Test
        void roundTrip() {
            SampleIntKeyMap obj = new SampleIntKeyMap();
            Map<BigInteger, String> names = new LinkedHashMap<>();
            names.put(BigInteger.valueOf(999), "alice");
            names.put(BigInteger.valueOf(1000), "bob");
            obj.setBigIntKeyedNames(names);

            SampleIntKeyMap restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals("alice", restored.getBigIntKeyedNames().get(BigInteger.valueOf(999)));
            assertEquals("bob", restored.getBigIntKeyedNames().get(BigInteger.valueOf(1000)));
        }
    }

    @Nested
    class EnumValues {

        @Test
        void roundTrip() {
            SampleIntKeyMap obj = new SampleIntKeyMap();
            Map<Integer, OrderStatus> statuses = new LinkedHashMap<>();
            statuses.put(1, OrderStatus.CONFIRMED);
            statuses.put(2, OrderStatus.SHIPPED);
            obj.setIntKeyedStatuses(statuses);

            SampleIntKeyMap restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(OrderStatus.CONFIRMED, restored.getIntKeyedStatuses().get(1));
            assertEquals(OrderStatus.SHIPPED, restored.getIntKeyedStatuses().get(2));
        }
    }

    @Nested
    class NestedValues {

        @Test
        void roundTrip() {
            SampleIntKeyMap obj = new SampleIntKeyMap();
            Map<BigInteger, SampleNestedAddress> addresses = new LinkedHashMap<>();
            addresses.put(BigInteger.ONE, new SampleNestedAddress("123 Main St", "Springfield", "62704"));
            addresses.put(BigInteger.TWO, new SampleNestedAddress("456 Office Dr", "Capital City", "90210"));
            obj.setBigIntKeyedAddresses(addresses);

            SampleIntKeyMap restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getBigIntKeyedAddresses());
            assertEquals(2, restored.getBigIntKeyedAddresses().size());
            assertEquals("123 Main St", restored.getBigIntKeyedAddresses().get(BigInteger.ONE).getStreet());
            assertEquals("456 Office Dr", restored.getBigIntKeyedAddresses().get(BigInteger.TWO).getStreet());
        }

        @Test
        void nestedValueStoredAsMetadataMap() {
            SampleIntKeyMap obj = new SampleIntKeyMap();
            Map<BigInteger, SampleNestedAddress> addresses = new LinkedHashMap<>();
            addresses.put(BigInteger.TEN, new SampleNestedAddress("789 Elm", "City", "11111"));
            obj.setBigIntKeyedAddresses(addresses);

            MetadataMap map = converter.toMetadataMap(obj);
            Object addressesVal = map.get("bigIntKeyedAddresses");
            assertInstanceOf(MetadataMap.class, addressesVal);

            MetadataMap addressesMap = (MetadataMap) addressesVal;
            Object val = addressesMap.get(BigInteger.TEN);
            assertInstanceOf(MetadataMap.class, val);

            MetadataMap innerMap = (MetadataMap) val;
            assertEquals("789 Elm", innerMap.get("street"));
        }
    }
}
