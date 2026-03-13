package com.bloxbean.cardano.client.metadata.annotation.processor.it;

import com.bloxbean.cardano.client.metadata.MetadataList;
import com.bloxbean.cardano.client.metadata.MetadataMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the generated {@code SampleByteCollectionEncodingMetadataConverter}.
 *
 * <p>Verifies {@code List<byte[]>} with all three encoding modes:
 * <ul>
 *   <li>DEFAULT      — raw Cardano bytes</li>
 *   <li>STRING_HEX   — hex-encoded text strings</li>
 *   <li>STRING_BASE64 — Base64-encoded text strings</li>
 * </ul>
 */
class SampleByteCollectionEncodingMetadataConverterTest {

    SampleByteCollectionEncodingMetadataConverter converter;

    @BeforeEach
    void setUp() {
        converter = new SampleByteCollectionEncodingMetadataConverter();
    }

    // =========================================================================
    // STRING_HEX — List<byte[]> stored as hex strings
    // =========================================================================

    @Nested
    class HexEncodedList {

        @Test
        void roundTrip() {
            byte[] a = {(byte) 0xDE, (byte) 0xAD};
            byte[] b = {(byte) 0xBE, (byte) 0xEF};
            SampleByteCollectionEncoding obj = new SampleByteCollectionEncoding();
            obj.setHexHashes(List.of(a, b));

            SampleByteCollectionEncoding restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getHexHashes());
            assertEquals(2, restored.getHexHashes().size());
            assertArrayEquals(a, restored.getHexHashes().get(0));
            assertArrayEquals(b, restored.getHexHashes().get(1));
        }

        @Test
        void storedAsHexStrings() {
            byte[] a = {(byte) 0xDE, (byte) 0xAD};
            byte[] b = {(byte) 0xBE, (byte) 0xEF};
            SampleByteCollectionEncoding obj = new SampleByteCollectionEncoding();
            obj.setHexHashes(List.of(a, b));

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataList list = (MetadataList) map.get("hexHashes");

            assertNotNull(list);
            assertEquals("dead", list.getValueAt(0));
            assertEquals("beef", list.getValueAt(1));
        }

        @Test
        void storedAsString_notByteArray() {
            SampleByteCollectionEncoding obj = new SampleByteCollectionEncoding();
            obj.setHexHashes(List.of(new byte[]{1, 2}));

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataList list = (MetadataList) map.get("hexHashes");

            assertInstanceOf(String.class, list.getValueAt(0));
        }

        @Test
        void nullList_notPresentInMap() {
            SampleByteCollectionEncoding obj = new SampleByteCollectionEncoding();
            obj.setHexHashes(null);

            assertNull(converter.toMetadataMap(obj).get("hexHashes"));
        }

        @Test
        void emptyList_roundTrip() {
            SampleByteCollectionEncoding obj = new SampleByteCollectionEncoding();
            obj.setHexHashes(List.of());

            SampleByteCollectionEncoding restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getHexHashes());
            assertTrue(restored.getHexHashes().isEmpty());
        }
    }

    // =========================================================================
    // STRING_BASE64 — List<byte[]> stored as Base64 strings
    // =========================================================================

    @Nested
    class Base64EncodedList {

        @Test
        void roundTrip() {
            byte[] a = {(byte) 0xCA, (byte) 0xFE};
            byte[] b = {(byte) 0xBA, (byte) 0xBE};
            SampleByteCollectionEncoding obj = new SampleByteCollectionEncoding();
            obj.setBase64Blobs(List.of(a, b));

            SampleByteCollectionEncoding restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getBase64Blobs());
            assertEquals(2, restored.getBase64Blobs().size());
            assertArrayEquals(a, restored.getBase64Blobs().get(0));
            assertArrayEquals(b, restored.getBase64Blobs().get(1));
        }

        @Test
        void storedAsBase64Strings() {
            byte[] a = {(byte) 0xCA, (byte) 0xFE};
            byte[] b = {(byte) 0xBA, (byte) 0xBE};
            SampleByteCollectionEncoding obj = new SampleByteCollectionEncoding();
            obj.setBase64Blobs(List.of(a, b));

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataList list = (MetadataList) map.get("base64Blobs");

            assertNotNull(list);
            assertEquals(Base64.getEncoder().encodeToString(a), list.getValueAt(0));
            assertEquals(Base64.getEncoder().encodeToString(b), list.getValueAt(1));
        }

        @Test
        void storedAsString_notByteArray() {
            SampleByteCollectionEncoding obj = new SampleByteCollectionEncoding();
            obj.setBase64Blobs(List.of(new byte[]{1, 2}));

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataList list = (MetadataList) map.get("base64Blobs");

            assertInstanceOf(String.class, list.getValueAt(0));
        }

        @Test
        void nullList_notPresentInMap() {
            SampleByteCollectionEncoding obj = new SampleByteCollectionEncoding();
            obj.setBase64Blobs(null);

            assertNull(converter.toMetadataMap(obj).get("base64Blobs"));
        }

        @Test
        void emptyList_roundTrip() {
            SampleByteCollectionEncoding obj = new SampleByteCollectionEncoding();
            obj.setBase64Blobs(List.of());

            SampleByteCollectionEncoding restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getBase64Blobs());
            assertTrue(restored.getBase64Blobs().isEmpty());
        }
    }

    // =========================================================================
    // DEFAULT — List<byte[]> stored as raw bytes
    // =========================================================================

    @Nested
    class RawBytesList {

        @Test
        void roundTrip() {
            byte[] a = {1, 2, 3};
            byte[] b = {4, 5, 6};
            SampleByteCollectionEncoding obj = new SampleByteCollectionEncoding();
            obj.setRawPayloads(List.of(a, b));

            SampleByteCollectionEncoding restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getRawPayloads());
            assertEquals(2, restored.getRawPayloads().size());
            assertArrayEquals(a, restored.getRawPayloads().get(0));
            assertArrayEquals(b, restored.getRawPayloads().get(1));
        }

        @Test
        void storedAsByteArrayInMetadataList() {
            byte[] payload = {10, 20, 30};
            SampleByteCollectionEncoding obj = new SampleByteCollectionEncoding();
            obj.setRawPayloads(List.of(payload));

            MetadataMap map = converter.toMetadataMap(obj);
            MetadataList list = (MetadataList) map.get("rawPayloads");

            assertNotNull(list);
            assertInstanceOf(byte[].class, list.getValueAt(0));
            assertArrayEquals(payload, (byte[]) list.getValueAt(0));
        }

        @Test
        void nullList_notPresentInMap() {
            SampleByteCollectionEncoding obj = new SampleByteCollectionEncoding();
            obj.setRawPayloads(null);

            assertNull(converter.toMetadataMap(obj).get("rawPayloads"));
        }

        @Test
        void emptyList_roundTrip() {
            SampleByteCollectionEncoding obj = new SampleByteCollectionEncoding();
            obj.setRawPayloads(List.of());

            SampleByteCollectionEncoding restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertNotNull(restored.getRawPayloads());
            assertTrue(restored.getRawPayloads().isEmpty());
        }
    }

    // =========================================================================
    // All three fields together — independence
    // =========================================================================

    @Nested
    class AllFieldsTogether {

        @Test
        void threeEncodings_roundTripTogether() {
            byte[] hex1 = {(byte) 0xDE, (byte) 0xAD};
            byte[] b64  = {(byte) 0xCA, (byte) 0xFE};
            byte[] raw  = {1, 2, 3};

            SampleByteCollectionEncoding obj = new SampleByteCollectionEncoding();
            obj.setHexHashes(List.of(hex1));
            obj.setBase64Blobs(List.of(b64));
            obj.setRawPayloads(List.of(raw));

            SampleByteCollectionEncoding restored = converter.fromMetadataMap(converter.toMetadataMap(obj));

            assertArrayEquals(hex1, restored.getHexHashes().get(0));
            assertArrayEquals(b64, restored.getBase64Blobs().get(0));
            assertArrayEquals(raw, restored.getRawPayloads().get(0));
        }

        @Test
        void distinctKeys_inMap() {
            SampleByteCollectionEncoding obj = new SampleByteCollectionEncoding();
            obj.setHexHashes(List.of(new byte[]{1}));
            obj.setBase64Blobs(List.of(new byte[]{2}));
            obj.setRawPayloads(List.of(new byte[]{3}));

            MetadataMap map = converter.toMetadataMap(obj);

            assertNotNull(map.get("hexHashes"));
            assertNotNull(map.get("base64Blobs"));
            assertNotNull(map.get("rawPayloads"));
        }
    }
}
