package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.MetadataMap;
import com.bloxbean.cardano.client.util.HexUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the generated {@code SampleByteEncodingMetadataConverter}.
 *
 * <p>Verifies all three {@code byte[]} encoding modes from ADR metadata/0004:
 * <ul>
 *   <li>DEFAULT  — raw Cardano bytes</li>
 *   <li>STRING_HEX    — hex-encoded text string</li>
 *   <li>STRING_BASE64 — Base64-encoded text string</li>
 * </ul>
 */
class SampleByteEncodingMetadataConverterIT {

    SampleByteEncodingMetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SampleByteEncodingMetadataConverter();
    }

    // =========================================================================
    // DEFAULT — raw bytes
    // =========================================================================

    @Nested
    class RawBytesDefault {

        @Test
        void roundTrip() {
            byte[] data = {1, 2, 3, (byte) 0xFF, 0};
            SampleByteEncoding obj = new SampleByteEncoding();
            obj.setRawData(data);

            SampleByteEncoding restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertArrayEquals(data, restored.getRawData());
        }

        @Test
        void storedAsByteArray() {
            byte[] data = {10, 20, 30};
            SampleByteEncoding obj = new SampleByteEncoding();
            obj.setRawData(data);

            MetadataMap map = converter.toMetadataMap(obj);

            assertInstanceOf(byte[].class, map.get("rawData"));
            assertArrayEquals(data, (byte[]) map.get("rawData"));
        }

        @Test
        void null_keyAbsentInMap() {
            SampleByteEncoding obj = new SampleByteEncoding();
            obj.setRawData(null);

            assertNull(converter.toMetadataMap(obj).get("rawData"));
        }
    }

    // =========================================================================
    // STRING_HEX — lowercase hex text
    // =========================================================================

    @Nested
    class HexEncoded {

        @Test
        void roundTrip() {
            byte[] data = {(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF};
            SampleByteEncoding obj = new SampleByteEncoding();
            obj.setHexData(data);

            SampleByteEncoding restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertArrayEquals(data, restored.getHexData());
        }

        @Test
        void storedAsLowercaseHexString() {
            byte[] data = {(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF};
            SampleByteEncoding obj = new SampleByteEncoding();
            obj.setHexData(data);

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals("deadbeef", map.get("hexData"));
        }

        @Test
        void storedAsString_notByteArray() {
            byte[] data = {1, 2, 3};
            SampleByteEncoding obj = new SampleByteEncoding();
            obj.setHexData(data);

            MetadataMap map = converter.toMetadataMap(obj);

            assertInstanceOf(String.class, map.get("hexData"));
        }

        @Test
        void allZeroBytes_roundTrip() {
            byte[] data = new byte[4]; // {0, 0, 0, 0}
            SampleByteEncoding obj = new SampleByteEncoding();
            obj.setHexData(data);

            MetadataMap map = converter.toMetadataMap(obj);
            assertEquals("00000000", map.get("hexData"));

            SampleByteEncoding restored = converter.fromMetadataMap(map);
            assertArrayEquals(data, restored.getHexData());
        }

        @Test
        void singleByte_roundTrip() {
            byte[] data = {(byte) 0xAB};
            SampleByteEncoding obj = new SampleByteEncoding();
            obj.setHexData(data);

            MetadataMap map = converter.toMetadataMap(obj);
            assertEquals("ab", map.get("hexData"));

            assertArrayEquals(data, converter.fromMetadataMap(map).getHexData());
        }

        @Test
        void null_keyAbsentInMap() {
            SampleByteEncoding obj = new SampleByteEncoding();
            obj.setHexData(null);

            assertNull(converter.toMetadataMap(obj).get("hexData"));
        }

        @Test
        void storedValueMatchesHexUtil() {
            byte[] data = {1, 2, 3, 4, 5};
            SampleByteEncoding obj = new SampleByteEncoding();
            obj.setHexData(data);

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals(HexUtil.encodeHexString(data), map.get("hexData"));
        }
    }

    // =========================================================================
    // STRING_BASE64 — Base64 text
    // =========================================================================

    @Nested
    class Base64Encoded {

        @Test
        void roundTrip() {
            byte[] data = {(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF};
            SampleByteEncoding obj = new SampleByteEncoding();
            obj.setBase64Data(data);

            SampleByteEncoding restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertArrayEquals(data, restored.getBase64Data());
        }

        @Test
        void storedAsBase64String() {
            byte[] data = {(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF};
            SampleByteEncoding obj = new SampleByteEncoding();
            obj.setBase64Data(data);

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals(Base64.getEncoder().encodeToString(data), map.get("b64Data"));
        }

        @Test
        void storedAsString_notByteArray() {
            byte[] data = {1, 2, 3};
            SampleByteEncoding obj = new SampleByteEncoding();
            obj.setBase64Data(data);

            MetadataMap map = converter.toMetadataMap(obj);

            assertInstanceOf(String.class, map.get("b64Data"));
        }

        @Test
        void allZeroBytes_roundTrip() {
            byte[] data = new byte[3]; // {0, 0, 0}
            SampleByteEncoding obj = new SampleByteEncoding();
            obj.setBase64Data(data);

            MetadataMap map = converter.toMetadataMap(obj);
            assertEquals("AAAA", map.get("b64Data"));

            assertArrayEquals(data, converter.fromMetadataMap(map).getBase64Data());
        }

        @Test
        void null_keyAbsentInMap() {
            SampleByteEncoding obj = new SampleByteEncoding();
            obj.setBase64Data(null);

            assertNull(converter.toMetadataMap(obj).get("b64Data"));
        }

        @Test
        void storedValueMatchesBase64Encoder() {
            byte[] data = {10, 20, 30, 40, 50};
            SampleByteEncoding obj = new SampleByteEncoding();
            obj.setBase64Data(data);

            MetadataMap map = converter.toMetadataMap(obj);

            assertEquals(Base64.getEncoder().encodeToString(data), map.get("b64Data"));
        }
    }

    // =========================================================================
    // All three fields together — independence
    // =========================================================================

    @Nested
    class AllFieldsTogether {

        @Test
        void threeEncodings_roundTripTogether() {
            byte[] rawBytes  = {1, 2, 3};
            byte[] hexBytes  = {(byte) 0xCA, (byte) 0xFE};
            byte[] b64Bytes  = {(byte) 0xBA, (byte) 0xBE};

            SampleByteEncoding obj = new SampleByteEncoding();
            obj.setRawData(rawBytes);
            obj.setHexData(hexBytes);
            obj.setBase64Data(b64Bytes);

            SampleByteEncoding restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertArrayEquals(rawBytes, restored.getRawData());
            assertArrayEquals(hexBytes, restored.getHexData());
            assertArrayEquals(b64Bytes, restored.getBase64Data());
        }

        @Test
        void distinctKeys_inMap() {
            SampleByteEncoding obj = new SampleByteEncoding();
            obj.setRawData(new byte[]{1});
            obj.setHexData(new byte[]{2});
            obj.setBase64Data(new byte[]{3});

            MetadataMap map = converter.toMetadataMap(obj);

            assertNotNull(map.get("rawData"));
            assertNotNull(map.get("hexData"));
            assertNotNull(map.get("b64Data"));
        }
    }
}
