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
 * Integration tests for {@code Map<String, V>} field support.
 */
class SampleMapMetadataConverterTest {

    SampleMapMetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SampleMapMetadataConverter();
    }

    @Nested
    class StringValues {

        @Test
        void roundTrip() {
            SampleMap obj = new SampleMap();
            Map<String, String> settings = new LinkedHashMap<>();
            settings.put("theme", "dark");
            settings.put("lang", "en");
            obj.setSettings(settings);

            SampleMap restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals("dark", restored.getSettings().get("theme"));
            assertEquals("en", restored.getSettings().get("lang"));
            assertEquals(2, restored.getSettings().size());
        }

        @Test
        void emptyMap_roundTrip() {
            SampleMap obj = new SampleMap();
            obj.setSettings(new LinkedHashMap<>());

            SampleMap restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getSettings());
            assertTrue(restored.getSettings().isEmpty());
        }

        @Test
        void nullMap_keyAbsent() {
            SampleMap obj = new SampleMap();
            obj.setSettings(null);

            MetadataMap map = converter.toMetadataMap(obj);

            assertNull(map.get("settings"));
        }
    }

    @Nested
    class IntegerValues {

        @Test
        void roundTrip() {
            SampleMap obj = new SampleMap();
            Map<String, Integer> scores = new LinkedHashMap<>();
            scores.put("alice", 100);
            scores.put("bob", 85);
            obj.setScores(scores);

            SampleMap restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(100, restored.getScores().get("alice"));
            assertEquals(85, restored.getScores().get("bob"));
        }
    }

    @Nested
    class BigIntegerValues {

        @Test
        void roundTrip() {
            SampleMap obj = new SampleMap();
            Map<String, BigInteger> amounts = new LinkedHashMap<>();
            amounts.put("deposit", BigInteger.valueOf(1000000));
            amounts.put("fee", BigInteger.valueOf(200000));
            obj.setAmounts(amounts);

            SampleMap restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(BigInteger.valueOf(1000000), restored.getAmounts().get("deposit"));
            assertEquals(BigInteger.valueOf(200000), restored.getAmounts().get("fee"));
        }
    }

    @Nested
    class EnumValues {

        @Test
        void roundTrip() {
            SampleMap obj = new SampleMap();
            Map<String, OrderStatus> statusMap = new LinkedHashMap<>();
            statusMap.put("order1", OrderStatus.CONFIRMED);
            statusMap.put("order2", OrderStatus.SHIPPED);
            obj.setStatusMap(statusMap);

            SampleMap restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertEquals(OrderStatus.CONFIRMED, restored.getStatusMap().get("order1"));
            assertEquals(OrderStatus.SHIPPED, restored.getStatusMap().get("order2"));
        }
    }

    @Nested
    class NestedValues {

        @Test
        void roundTrip() {
            SampleMap obj = new SampleMap();
            Map<String, SampleNestedAddress> addresses = new LinkedHashMap<>();
            addresses.put("home", new SampleNestedAddress("123 Main St", "Springfield", "62704"));
            addresses.put("work", new SampleNestedAddress("456 Office Dr", "Capital City", "90210"));
            obj.setAddresses(addresses);

            SampleMap restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getAddresses());
            assertEquals(2, restored.getAddresses().size());
            assertEquals("123 Main St", restored.getAddresses().get("home").getStreet());
            assertEquals("456 Office Dr", restored.getAddresses().get("work").getStreet());
        }

        @Test
        void nestedValueStoredAsMetadataMap() {
            SampleMap obj = new SampleMap();
            Map<String, SampleNestedAddress> addresses = new LinkedHashMap<>();
            addresses.put("office", new SampleNestedAddress("789 Elm", "City", "11111"));
            obj.setAddresses(addresses);

            MetadataMap map = converter.toMetadataMap(obj);
            Object addressesVal = map.get("addresses");
            assertInstanceOf(MetadataMap.class, addressesVal);

            MetadataMap addressesMap = (MetadataMap) addressesVal;
            Object officeVal = addressesMap.get("office");
            assertInstanceOf(MetadataMap.class, officeVal);

            MetadataMap officeMap = (MetadataMap) officeVal;
            assertEquals("789 Elm", officeMap.get("street"));
        }
    }
}
