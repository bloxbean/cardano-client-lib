package com.bloxbean.cardano.statetrees.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class NibblePathTest {

    @Test
    void testValidNibbleCreation() {
        NibblePath path = NibblePath.of(1, 2, 3, 15);

        assertEquals(4, path.length());
        assertFalse(path.isEmpty());
        assertArrayEquals(new int[]{1, 2, 3, 15}, path.getNibbles());

        assertEquals(1, path.get(0));
        assertEquals(2, path.get(1));
        assertEquals(3, path.get(2));
        assertEquals(15, path.get(3));
    }

    @Test
    void testEmptyPath() {
        NibblePath empty = NibblePath.of();

        assertEquals(0, empty.length());
        assertTrue(empty.isEmpty());
        assertArrayEquals(new int[0], empty.getNibbles());

        // Test constant
        assertEquals(empty, NibblePath.EMPTY);
    }

    @Test
    void testInvalidNibbles() {
        assertThrows(IllegalArgumentException.class, () ->
                NibblePath.of(1, 2, 16)); // 16 is not a valid nibble

        assertThrows(IllegalArgumentException.class, () ->
                NibblePath.of(1, 2, -1)); // -1 is not a valid nibble

        assertThrows(NullPointerException.class, () ->
                NibblePath.of((int[]) null));
    }

    @Test
    void testFromBytes() {
        byte[] bytes = {(byte) 0x1a, (byte) 0xbc, (byte) 0xd3};
        NibblePath path = NibblePath.fromBytes(bytes);

        // Each byte becomes two nibbles: 0x1a -> [1, 10], 0xbc -> [11, 12], 0xd3 -> [13, 3]
        assertArrayEquals(new int[]{1, 10, 11, 12, 13, 3}, path.getNibbles());
        assertEquals(6, path.length());
    }

    @Test
    void testFromEmptyBytes() {
        NibblePath path = NibblePath.fromBytes(new byte[0]);
        assertTrue(path.isEmpty());
        assertEquals(NibblePath.EMPTY, path);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1abc",
            "0x1abc",
            "1ABC",   // uppercase
            "0X1ABC"  // uppercase with prefix
    })
    void testFromHexString(String hexString) {
        NibblePath path = NibblePath.fromHexString(hexString);
        assertArrayEquals(new int[]{1, 10, 11, 12}, path.getNibbles());
    }

    @Test
    void testFromHexStringOddLength() {
        // Odd length should be padded with leading zero
        NibblePath path = NibblePath.fromHexString("abc");
        assertArrayEquals(new int[]{0, 10, 11, 12}, path.getNibbles());
    }

    @Test
    void testFromHexStringEmpty() {
        NibblePath path = NibblePath.fromHexString("");
        assertTrue(path.isEmpty());
        assertEquals(NibblePath.EMPTY, path);
    }

    @Test
    void testInvalidHexString() {
        assertThrows(IllegalArgumentException.class, () ->
                NibblePath.fromHexString("1g")); // 'g' is not a valid hex digit

        assertThrows(NullPointerException.class, () ->
                NibblePath.fromHexString(null));
    }

    @Test
    void testImmutability() {
        int[] originalNibbles = {1, 2, 3, 4};
        NibblePath path = NibblePath.of(originalNibbles);

        // Modify original array
        originalNibbles[0] = 99;

        // Path should not be affected
        assertEquals(1, path.get(0));

        // Modify returned array
        int[] returnedNibbles = path.getNibbles();
        returnedNibbles[1] = 99;

        // Path should not be affected
        assertEquals(2, path.get(1));
    }

    @Test
    void testSlicing() {
        NibblePath path = NibblePath.of(0, 1, 2, 3, 4, 5);

        NibblePath slice = path.slice(2, 5);
        assertArrayEquals(new int[]{2, 3, 4}, slice.getNibbles());

        // Edge cases
        NibblePath emptySlice = path.slice(2, 2);
        assertTrue(emptySlice.isEmpty());

        NibblePath fullSlice = path.slice(0, path.length());
        assertEquals(path, fullSlice);
    }

    @Test
    void testInvalidSlicing() {
        NibblePath path = NibblePath.of(0, 1, 2, 3);

        assertThrows(IndexOutOfBoundsException.class, () ->
                path.slice(-1, 2)); // negative start

        assertThrows(IndexOutOfBoundsException.class, () ->
                path.slice(0, 5)); // end beyond length

        assertThrows(IndexOutOfBoundsException.class, () ->
                path.slice(3, 2)); // start > end
    }

    @Test
    void testSuffix() {
        NibblePath path = NibblePath.of(0, 1, 2, 3, 4);

        NibblePath suffix = path.suffix(2);
        assertArrayEquals(new int[]{2, 3, 4}, suffix.getNibbles());

        NibblePath fullSuffix = path.suffix(0);
        assertEquals(path, fullSuffix);

        NibblePath emptySuffix = path.suffix(5);
        assertTrue(emptySuffix.isEmpty());
    }

    @Test
    void testPrefix() {
        NibblePath path = NibblePath.of(0, 1, 2, 3, 4);

        NibblePath prefix = path.prefix(3);
        assertArrayEquals(new int[]{0, 1, 2}, prefix.getNibbles());

        NibblePath emptyPrefix = path.prefix(0);
        assertTrue(emptyPrefix.isEmpty());

        NibblePath fullPrefix = path.prefix(5);
        assertEquals(path, fullPrefix);
    }

    @Test
    void testStartsWith() {
        NibblePath path = NibblePath.of(1, 2, 3, 4, 5);

        assertTrue(path.startsWith(NibblePath.of(1, 2)));
        assertTrue(path.startsWith(NibblePath.of(1, 2, 3, 4, 5))); // same path
        assertTrue(path.startsWith(NibblePath.EMPTY)); // empty prefix

        assertFalse(path.startsWith(NibblePath.of(1, 3))); // different content
        assertFalse(path.startsWith(NibblePath.of(1, 2, 3, 4, 5, 6))); // longer than path
    }

    @Test
    void testCommonPrefixLength() {
        NibblePath path1 = NibblePath.of(1, 2, 3, 4, 5);
        NibblePath path2 = NibblePath.of(1, 2, 7, 8, 9);
        NibblePath path3 = NibblePath.of(6, 7, 8);

        assertEquals(2, path1.commonPrefixLength(path2));
        assertEquals(0, path1.commonPrefixLength(path3));
        assertEquals(5, path1.commonPrefixLength(path1)); // same path

        // Different lengths
        NibblePath shortPath = NibblePath.of(1, 2);
        assertEquals(2, path1.commonPrefixLength(shortPath));
        assertEquals(2, shortPath.commonPrefixLength(path1));
    }

    @Test
    void testConcat() {
        NibblePath path1 = NibblePath.of(1, 2, 3);
        NibblePath path2 = NibblePath.of(4, 5, 6);

        NibblePath concat = path1.concat(path2);
        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6}, concat.getNibbles());

        // Concat with empty
        assertEquals(path1, path1.concat(NibblePath.EMPTY));
        assertEquals(path2, NibblePath.EMPTY.concat(path2));
    }

    @Test
    void testToBytes() {
        // Even number of nibbles
        NibblePath evenPath = NibblePath.of(1, 10, 11, 12);
        byte[] evenBytes = evenPath.toBytes();
        assertArrayEquals(new byte[]{(byte) 0x1a, (byte) 0xbc}, evenBytes);

        // Odd number of nibbles (should be padded with leading zero)
        NibblePath oddPath = NibblePath.of(10, 11, 12);
        byte[] oddBytes = oddPath.toBytes();
        assertArrayEquals(new byte[]{(byte) 0x0a, (byte) 0xbc}, oddBytes);
    }

    @Test
    void testToHexString() {
        NibblePath path = NibblePath.of(1, 10, 11, 12);
        assertEquals("1abc", path.toHexString());

        NibblePath emptyPath = NibblePath.EMPTY;
        assertEquals("", emptyPath.toHexString());

        NibblePath singleNibble = NibblePath.of(15);
        assertEquals("f", singleNibble.toHexString());
    }

    @Test
    void testRoundTripConversions() {
        // Hex string round trip
        String originalHex = "1a2b3c4d5e6f";
        NibblePath fromHex = NibblePath.fromHexString(originalHex);
        assertEquals(originalHex, fromHex.toHexString());

        // Bytes round trip
        byte[] originalBytes = {(byte) 0x1a, (byte) 0x2b, (byte) 0x3c};
        NibblePath fromBytes = NibblePath.fromBytes(originalBytes);
        assertArrayEquals(originalBytes, fromBytes.toBytes());
    }

    @Test
    void testEqualsAndHashCode() {
        NibblePath path1a = NibblePath.of(1, 2, 3, 4);
        NibblePath path1b = NibblePath.of(1, 2, 3, 4);
        NibblePath path2 = NibblePath.of(1, 2, 3, 5);

        // Reflexive
        assertEquals(path1a, path1a);

        // Symmetric
        assertEquals(path1a, path1b);
        assertEquals(path1b, path1a);

        // Hash code consistency
        assertEquals(path1a.hashCode(), path1b.hashCode());

        // Different content
        assertNotEquals(path1a, path2);

        // Null and other types
        assertNotEquals(path1a, null);
        assertNotEquals(path1a, "not a path");
    }

    @Test
    void testToString() {
        NibblePath path = NibblePath.of(1, 10, 11, 12);
        String toString = path.toString();

        assertTrue(toString.startsWith("NibblePath{"));
        assertTrue(toString.contains("1abc"));
        assertTrue(toString.endsWith("}"));
    }

    @Test
    void testIndexOutOfBounds() {
        NibblePath path = NibblePath.of(1, 2, 3);

        assertThrows(IndexOutOfBoundsException.class, () -> path.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> path.get(3));
        assertThrows(IndexOutOfBoundsException.class, () -> path.suffix(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> path.prefix(-1));
    }

    @Test
    void testNullParameterHandling() {
        NibblePath path = NibblePath.of(1, 2, 3);

        assertThrows(NullPointerException.class, () -> path.startsWith(null));
        assertThrows(NullPointerException.class, () -> path.commonPrefixLength(null));
        assertThrows(NullPointerException.class, () -> path.concat(null));
        assertThrows(NullPointerException.class, () -> NibblePath.fromBytes(null));
    }
}
