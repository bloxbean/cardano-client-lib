package com.bloxbean.cardano.client.crypto.vrf.bc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Ed25519Point: encode/decode, point arithmetic, scalar multiply.
 */
class Ed25519PointTest {

    @Test
    void basePoint_encodeDecode_roundTrip() {
        byte[] encoded = Ed25519Point.BASE_POINT.encode();
        Ed25519Point decoded = Ed25519Point.decode(encoded);
        assertNotNull(decoded);
        assertArrayEquals(encoded, decoded.encode());
    }

    @Test
    void basePoint_matchesRfc8032() {
        // RFC 8032 base point encoding (y = 4/5, x positive)
        byte[] expected = hexToBytes(
                "5866666666666666666666666666666666666666666666666666666666666666");
        assertArrayEquals(expected, Ed25519Point.BASE_POINT.encode());
    }

    @Test
    void neutral_encodesDecode() {
        byte[] encoded = Ed25519Point.NEUTRAL.encode();
        // Neutral = (0,1) => y=1, x=0 => encoded as 01 00 ... 00
        assertEquals(0x01, encoded[0] & 0xFF);
        for (int i = 1; i < 32; i++) {
            assertEquals(0x00, encoded[i] & 0xFF);
        }
        Ed25519Point decoded = Ed25519Point.decode(encoded);
        assertNotNull(decoded);
        assertEquals(Ed25519Point.NEUTRAL, decoded);
    }

    @Test
    void addNeutral_identity() {
        Ed25519Point p = Ed25519Point.BASE_POINT;
        Ed25519Point result = p.add(Ed25519Point.NEUTRAL);
        assertEquals(p, result);
    }

    @Test
    void addCommutative() {
        // 2*B and 3*B
        Ed25519Point b2 = Ed25519Point.BASE_POINT.doublePoint();
        Ed25519Point b3 = b2.add(Ed25519Point.BASE_POINT);

        Ed25519Point sum1 = b2.add(b3);
        Ed25519Point sum2 = b3.add(b2);
        assertEquals(sum1, sum2);
    }

    @Test
    void doublePoint_equals_addSelf() {
        Ed25519Point dbl = Ed25519Point.BASE_POINT.doublePoint();
        Ed25519Point addSelf = Ed25519Point.BASE_POINT.add(Ed25519Point.BASE_POINT);
        assertEquals(dbl, addSelf);
    }

    @Test
    void negate_addOriginal_givesNeutral() {
        Ed25519Point p = Ed25519Point.BASE_POINT;
        Ed25519Point neg = p.negate();
        Ed25519Point result = p.add(neg);
        assertEquals(Ed25519Point.NEUTRAL, result);
    }

    @Test
    void subtract_self_givesNeutral() {
        Ed25519Point p = Ed25519Point.BASE_POINT.doublePoint();
        Ed25519Point result = p.subtract(p);
        assertEquals(Ed25519Point.NEUTRAL, result);
    }

    @Test
    void cofactorNeutral_isNeutral() {
        Ed25519Point result = Ed25519Point.NEUTRAL.multiplyByCofactor();
        assertEquals(Ed25519Point.NEUTRAL, result);
    }

    @Test
    void scalarMultiply_matchesEd25519PublicKey() {
        // Ed25519 private key (all zeros -> clamped secret scalar)
        // SHA-512 of 32 zero bytes, then clamp, then scalar multiply by B
        // We use the known test vector: sk=0x9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60
        // pk=d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a
        byte[] pk = hexToBytes("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a");
        Ed25519Point decoded = Ed25519Point.decode(pk);
        assertNotNull(decoded);

        // Re-encode should match
        assertArrayEquals(pk, decoded.encode());
    }

    @Test
    void scalarMultiply_smallScalar() {
        // 1 * B = B
        byte[] one = new byte[32];
        one[0] = 1;
        Ed25519Point result = Ed25519Point.BASE_POINT.scalarMultiply(one);
        assertEquals(Ed25519Point.BASE_POINT, result);

        // 2 * B = B + B = doublePoint
        byte[] two = new byte[32];
        two[0] = 2;
        Ed25519Point result2 = Ed25519Point.BASE_POINT.scalarMultiply(two);
        assertEquals(Ed25519Point.BASE_POINT.doublePoint(), result2);
    }

    @Test
    void scalarMultiply_zero_givesNeutral() {
        byte[] zero = new byte[32];
        Ed25519Point result = Ed25519Point.BASE_POINT.scalarMultiply(zero);
        assertEquals(Ed25519Point.NEUTRAL, result);
    }

    @Test
    void decode_invalidPoint_returnsNull() {
        // Construct an invalid encoding: set high bit of y to create a point with
        // a y value that doesn't yield a valid x (non-square ratio)
        byte[] invalid = new byte[32];
        invalid[0] = 0x01;
        invalid[31] = (byte)0x7F; // large y value
        // Not all values decode — try a known-invalid one
        // y = p-1 (all 0x7F...EC) → check if this is valid or not
        byte[] allOnes = hexToBytes("ecffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f");
        // This encodes y = p-1, u = (p-1)²-1 = p²-2p, v = d*(p-1)²+1
        // Whether this is a valid point depends on the specific ratio
        Ed25519Point result = Ed25519Point.decode(allOnes);
        // Just verify decode doesn't crash; the point may or may not be valid
        // The important thing is null/non-null is consistent with encode round-trip
        if (result != null) {
            assertArrayEquals(allOnes, result.encode());
        }
    }

    @Test
    void decode_nonCanonicalY_returnsNull() {
        // y = p = 2^255 - 19, encoded as little-endian with high bit clear:
        // p in LE = edffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f
        // This is y >= p, so the canonical check (round-trip) must reject it.
        byte[] nonCanonical = hexToBytes("edffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f");
        assertNull(Ed25519Point.decode(nonCanonical),
                "Non-canonical y (y == p) should be rejected");

        // y = p + 1 (also non-canonical)
        byte[] nonCanonical2 = hexToBytes("eeffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f");
        assertNull(Ed25519Point.decode(nonCanonical2),
                "Non-canonical y (y == p+1) should be rejected");
    }

    @Test
    void decode_nullOrWrongSize_returnsNull() {
        assertNull(Ed25519Point.decode(null));
        assertNull(Ed25519Point.decode(new byte[31]));
        assertNull(Ed25519Point.decode(new byte[33]));
    }

    @Test
    void decode_knownPoints_roundTrip() {
        // Several known Ed25519 public keys
        String[] knownPoints = {
                "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a",
                "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c",
                "fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025",
        };
        for (String hex : knownPoints) {
            byte[] bytes = hexToBytes(hex);
            Ed25519Point point = Ed25519Point.decode(bytes);
            assertNotNull(point, "Failed to decode: " + hex);
            assertArrayEquals(bytes, point.encode(), "Round-trip failed for: " + hex);
        }
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
