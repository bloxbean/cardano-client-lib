package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.MetadataMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@code Map<byte[], V>} field support.
 */
class SampleByteKeyMapMetadataConverterTest {

    SampleByteKeyMapMetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SampleByteKeyMapMetadataConverter();
    }

    @Nested
    class ByteKeyStringValue {

        @Test
        void serialization() {
            SampleByteKeyMap obj = new SampleByteKeyMap();
            Map<byte[], String> labels = new LinkedHashMap<>();
            byte[] key1 = new byte[]{0x01, 0x02};
            byte[] key2 = new byte[]{0x0A, 0x0B, 0x0C};
            labels.put(key1, "alpha");
            labels.put(key2, "beta");
            obj.setLabels(labels);

            MetadataMap map = converter.toMetadataMap(obj);
            Object labelsVal = map.get("labels");
            assertInstanceOf(MetadataMap.class, labelsVal);

            MetadataMap labelsMap = (MetadataMap) labelsVal;
            assertEquals("alpha", labelsMap.get(key1));
            assertEquals("beta", labelsMap.get(key2));
        }

        @Test
        void roundTrip() {
            SampleByteKeyMap obj = new SampleByteKeyMap();
            Map<byte[], String> labels = new LinkedHashMap<>();
            byte[] key1 = new byte[]{0x01, 0x02};
            byte[] key2 = new byte[]{0x0A, 0x0B, 0x0C};
            labels.put(key1, "alpha");
            labels.put(key2, "beta");
            obj.setLabels(labels);

            SampleByteKeyMap restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getLabels());
            assertEquals(2, restored.getLabels().size());

            // byte[] keys use reference equality in HashMap, so verify by iterating
            boolean foundKey1 = false;
            boolean foundKey2 = false;
            for (Map.Entry<byte[], String> entry : restored.getLabels().entrySet()) {
                if (Arrays.equals(entry.getKey(), key1)) {
                    assertEquals("alpha", entry.getValue());
                    foundKey1 = true;
                } else if (Arrays.equals(entry.getKey(), key2)) {
                    assertEquals("beta", entry.getValue());
                    foundKey2 = true;
                }
            }
            assertTrue(foundKey1, "Expected key {0x01, 0x02} not found");
            assertTrue(foundKey2, "Expected key {0x0A, 0x0B, 0x0C} not found");
        }
    }

    @Nested
    class ByteKeyBigIntegerValue {

        @Test
        void serialization_includingNegative() {
            SampleByteKeyMap obj = new SampleByteKeyMap();
            Map<byte[], BigInteger> amounts = new LinkedHashMap<>();
            byte[] key1 = new byte[]{(byte) 0xFF};
            byte[] key2 = new byte[]{0x00, 0x01};
            amounts.put(key1, BigInteger.valueOf(42));
            amounts.put(key2, BigInteger.valueOf(-100));
            obj.setAmounts(amounts);

            MetadataMap map = converter.toMetadataMap(obj);
            Object amountsVal = map.get("amounts");
            assertInstanceOf(MetadataMap.class, amountsVal);

            MetadataMap amountsMap = (MetadataMap) amountsVal;
            assertEquals(BigInteger.valueOf(42), amountsMap.get(key1));
            assertEquals(BigInteger.valueOf(-100), amountsMap.get(key2));
        }

        @Test
        void roundTrip() {
            SampleByteKeyMap obj = new SampleByteKeyMap();
            Map<byte[], BigInteger> amounts = new LinkedHashMap<>();
            byte[] key1 = new byte[]{(byte) 0xFF};
            byte[] key2 = new byte[]{0x00, 0x01};
            amounts.put(key1, BigInteger.valueOf(42));
            amounts.put(key2, BigInteger.valueOf(-100));
            obj.setAmounts(amounts);

            SampleByteKeyMap restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getAmounts());
            assertEquals(2, restored.getAmounts().size());

            boolean foundKey1 = false;
            boolean foundKey2 = false;
            for (Map.Entry<byte[], BigInteger> entry : restored.getAmounts().entrySet()) {
                if (Arrays.equals(entry.getKey(), key1)) {
                    assertEquals(BigInteger.valueOf(42), entry.getValue());
                    foundKey1 = true;
                } else if (Arrays.equals(entry.getKey(), key2)) {
                    assertEquals(BigInteger.valueOf(-100), entry.getValue());
                    foundKey2 = true;
                }
            }
            assertTrue(foundKey1, "Expected key {0xFF} not found");
            assertTrue(foundKey2, "Expected key {0x00, 0x01} not found");
        }
    }

    @Nested
    class NullHandling {

        @Test
        void nullMap_keyAbsent() {
            SampleByteKeyMap obj = new SampleByteKeyMap();
            obj.setLabels(null);
            obj.setAmounts(null);

            MetadataMap map = converter.toMetadataMap(obj);

            assertNull(map.get("labels"));
            assertNull(map.get("amounts"));
        }
    }
}
