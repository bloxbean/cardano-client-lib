package com.bloxbean.cardano.vds.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

class NodeHashTest {

    @Test
    void testValidHashCreation() {
        byte[] validHash = new byte[32];
        new SecureRandom().nextBytes(validHash);

        NodeHash nodeHash = NodeHash.of(validHash);
        assertNotNull(nodeHash);
        assertArrayEquals(validHash, nodeHash.getBytes());
        assertEquals(32, nodeHash.getLength());
    }

    @Test
    void testInvalidHashLength() {
        // Too short
        assertThrows(IllegalArgumentException.class, () ->
                NodeHash.of(new byte[31]));

        // Too long
        assertThrows(IllegalArgumentException.class, () ->
                NodeHash.of(new byte[33]));

        // Empty
        assertThrows(IllegalArgumentException.class, () ->
                NodeHash.of(new byte[0]));
    }

    @Test
    void testNullHashRejected() {
        assertThrows(NullPointerException.class, () ->
                NodeHash.of(null));
    }

    @Test
    void testImmutability() {
        byte[] originalHash = new byte[32];
        for (int i = 0; i < 32; i++) {
            originalHash[i] = (byte) i;
        }

        NodeHash nodeHash = NodeHash.of(originalHash);

        // Modify original array
        originalHash[0] = 99;

        // NodeHash should not be affected
        assertNotEquals(99, nodeHash.getBytes()[0]);
        assertEquals(0, nodeHash.getBytes()[0]);

        // Modify returned array
        byte[] returnedHash = nodeHash.getBytes();
        returnedHash[1] = 99;

        // NodeHash should not be affected
        assertNotEquals(99, nodeHash.getBytes()[1]);
        assertEquals(1, nodeHash.getBytes()[1]);
    }

    @Test
    void testHexStringConversion() {
        byte[] testHash = new byte[32];
        for (int i = 0; i < 32; i++) {
            testHash[i] = (byte) i;
        }

        NodeHash nodeHash = NodeHash.of(testHash);
        String hexString = nodeHash.toHexString();

        assertEquals(64, hexString.length()); // 32 bytes * 2 hex chars
        assertEquals("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f", hexString);

        // Round-trip conversion
        NodeHash recreated = NodeHash.fromHexString(hexString);
        assertEquals(nodeHash, recreated);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0x000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
            "000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F", // uppercase
            "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"   // lowercase
    })
    void testHexStringParsing(String hexString) {
        NodeHash nodeHash = NodeHash.fromHexString(hexString);

        byte[] expectedBytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            expectedBytes[i] = (byte) i;
        }

        assertArrayEquals(expectedBytes, nodeHash.getBytes());
    }

    @Test
    void testInvalidHexString() {
        // Wrong length
        assertThrows(IllegalArgumentException.class, () ->
                NodeHash.fromHexString("1234"));

        // Invalid characters
        assertThrows(IllegalArgumentException.class, () ->
                NodeHash.fromHexString("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1g"));

        // Null
        assertThrows(NullPointerException.class, () ->
                NodeHash.fromHexString(null));
    }

    @Test
    void testEqualsAndHashCode() {
        byte[] hash1 = new byte[32];
        byte[] hash2 = new byte[32];
        byte[] hash3 = new byte[32];
        hash3[0] = 1; // Different

        NodeHash nodeHash1a = NodeHash.of(hash1);
        NodeHash nodeHash1b = NodeHash.of(hash1);
        NodeHash nodeHash2 = NodeHash.of(hash2);
        NodeHash nodeHash3 = NodeHash.of(hash3);

        // Reflexive
        assertEquals(nodeHash1a, nodeHash1a);

        // Symmetric
        assertEquals(nodeHash1a, nodeHash1b);
        assertEquals(nodeHash1b, nodeHash1a);

        // Same content, different instances
        assertEquals(nodeHash1a, nodeHash2);
        assertEquals(nodeHash1a.hashCode(), nodeHash2.hashCode());

        // Different content
        assertNotEquals(nodeHash1a, nodeHash3);

        // Null and other types
        assertNotEquals(nodeHash1a, null);
        assertNotEquals(nodeHash1a, "not a hash");
    }

    @Test
    void testToString() {
        byte[] testHash = {0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xab, (byte) 0xcd, (byte) 0xef};
        byte[] fullHash = new byte[32];
        System.arraycopy(testHash, 0, fullHash, 0, 8);

        NodeHash nodeHash = NodeHash.of(fullHash);
        String toString = nodeHash.toString();

        assertTrue(toString.startsWith("NodeHash{"));
        assertTrue(toString.contains("0123456789abcdef"));
        assertTrue(toString.endsWith("}"));
    }

    @Test
    void testConstants() {
        assertEquals(32, NodeHash.HASH_LENGTH);
    }
}
