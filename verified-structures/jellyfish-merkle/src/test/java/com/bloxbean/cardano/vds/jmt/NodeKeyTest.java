package com.bloxbean.cardano.vds.jmt;

import com.bloxbean.cardano.vds.core.NibblePath;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NodeKeyTest {

    @Test
    void roundTripEncodingPreservesPathAndVersion() {
        NibblePath path = NibblePath.of(0xA, 0xB, 0xC, 0xD, 0xE);
        long version = Long.parseUnsignedLong("1844674407370955161");
        NodeKey original = NodeKey.of(path, version);

        byte[] encoded = original.toBytes();
        NodeKey decoded = NodeKey.fromBytes(encoded);

        assertArrayEquals(path.getNibbles(), decoded.path().getNibbles());
        assertEquals(version, decoded.version());
    }

    @Test
    void emptyPathEncodesAndDecodesCorrectly() {
        NodeKey key = NodeKey.of(NibblePath.EMPTY, 0L);
        NodeKey decoded = NodeKey.fromBytes(key.toBytes());
        assertTrue(decoded.path().isEmpty());
        assertEquals(0L, decoded.version());
    }

    @Test
    void compareToOrdersByPathThenVersion() {
        NodeKey a = NodeKey.of(NibblePath.of(0x0, 0x1), 2);
        NodeKey b = NodeKey.of(NibblePath.of(0x0, 0x2), 1);
        NodeKey c = NodeKey.of(NibblePath.of(0x0, 0x2), 3);

        List<NodeKey> sorted = Arrays.asList(c, b, a);
        sorted.sort(NodeKey::compareTo);

        assertEquals(a, sorted.get(0));
        assertEquals(b, sorted.get(1));
        assertEquals(c, sorted.get(2));
    }

    @Test
    void roundTripEncodingCoversAllNibbleValues() {
        NibblePath path = NibblePath.of(0x0, 0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8, 0x9, 0xA, 0xB, 0xC, 0xD, 0xE, 0xF);
        NodeKey original = NodeKey.of(path, 42L);
        NodeKey decoded = NodeKey.fromBytes(original.toBytes());
        assertArrayEquals(path.getNibbles(), decoded.path().getNibbles());
        assertEquals(42L, decoded.version());
    }

    @Test
    void decodingRejectsWrongPrefix() {
        NodeKey key = NodeKey.of(NibblePath.of(0x0), 1L);
        byte[] bytes = key.toBytes();
        bytes[0] = 0x00;
        assertThrows(IllegalArgumentException.class, () -> NodeKey.fromBytes(bytes));
    }

    @Test
    void decodingRejectsTrailingBytes() {
        byte[] valid = NodeKey.of(NibblePath.of(0xA), 1L).toBytes();
        byte[] withTrailingByte = Arrays.copyOf(valid, valid.length + 1);
        assertThrows(IllegalArgumentException.class, () -> NodeKey.fromBytes(withTrailingByte));
    }

    @Test
    void decodingRejectsNegativeVersion() {
        byte[] encoded = NodeKey.of(NibblePath.EMPTY, 0L).toBytes();
        Arrays.fill(encoded, encoded.length - Long.BYTES, encoded.length, (byte) 0xFF);
        assertThrows(IllegalArgumentException.class, () -> NodeKey.fromBytes(encoded));
    }

    @Test
    void decodingRejectsNonCanonicalOddPathPadding() {
        byte[] encoded = NodeKey.of(NibblePath.of(0xA), 1L).toBytes();
        encoded[2] |= 0x0F;

        assertThrows(IllegalArgumentException.class, () -> NodeKey.fromBytes(encoded));
    }

    @Test
    void decodingRejectsPathLongerThanKeyHash() {
        byte[] oversizedLength = new byte[1 + 1 + 33 + Long.BYTES];
        oversizedLength[0] = 0x4E;
        oversizedLength[1] = 65;

        assertThrows(IllegalArgumentException.class, () -> NodeKey.fromBytes(oversizedLength));
    }
}
