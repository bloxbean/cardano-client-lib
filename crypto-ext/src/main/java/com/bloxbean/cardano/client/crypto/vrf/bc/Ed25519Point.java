package com.bloxbean.cardano.client.crypto.vrf.bc;

import org.bouncycastle.math.ec.rfc7748.X25519Field;

import java.util.Arrays;

/**
 * Ed25519 point in extended coordinates (X, Y, Z, T) where:
 * <ul>
 *   <li>x = X/Z, y = Y/Z, T = X*Y/Z</li>
 *   <li>Curve: -x² + y² = 1 + d*x²*y² (Ed25519)</li>
 * </ul>
 * <p>
 * All field arithmetic uses BouncyCastle's public {@link X25519Field} API
 * (radix-2^26 limb representation, int[10]).
 */
public class Ed25519Point {

    // Curve constant d = -121665/121666 mod p
    static final int[] D = X25519Field.create();
    // 2*d precomputed
    static final int[] D2 = X25519Field.create();

    static {
        // d = -121665 * inv(121666) mod p
        int[] num = X25519Field.create();
        int[] den = X25519Field.create();
        X25519Field.one(num);
        X25519Field.mul(num, 121665, num);
        X25519Field.negate(num, num);
        X25519Field.one(den);
        X25519Field.mul(den, 121666, den);
        X25519Field.inv(den, den);
        X25519Field.mul(num, den, D);
        X25519Field.normalize(D);

        X25519Field.add(D, D, D2);
        X25519Field.normalize(D2);
    }

    /** Neutral element (identity): (0, 1, 1, 0) */
    public static final Ed25519Point NEUTRAL;

    /** Standard Ed25519 base point (generator) */
    public static final Ed25519Point BASE_POINT;

    static {
        int[] zeroFe = X25519Field.create();
        int[] oneFe = X25519Field.create();
        X25519Field.one(oneFe);
        NEUTRAL = new Ed25519Point(
                zeroFe.clone(),
                oneFe.clone(),
                oneFe.clone(),
                zeroFe.clone()
        );

        // Standard Ed25519 base point (from RFC 8032)
        byte[] baseBytes = new byte[] {
                (byte)0x58, (byte)0x66, (byte)0x66, (byte)0x66, (byte)0x66, (byte)0x66, (byte)0x66, (byte)0x66,
                (byte)0x66, (byte)0x66, (byte)0x66, (byte)0x66, (byte)0x66, (byte)0x66, (byte)0x66, (byte)0x66,
                (byte)0x66, (byte)0x66, (byte)0x66, (byte)0x66, (byte)0x66, (byte)0x66, (byte)0x66, (byte)0x66,
                (byte)0x66, (byte)0x66, (byte)0x66, (byte)0x66, (byte)0x66, (byte)0x66, (byte)0x66, (byte)0x66
        };
        BASE_POINT = decode(baseBytes);
        if (BASE_POINT == null) {
            throw new IllegalStateException("Failed to decode Ed25519 base point");
        }
    }

    private final int[] X;
    private final int[] Y;
    private final int[] Z;
    private final int[] T;

    Ed25519Point(int[] X, int[] Y, int[] Z, int[] T) {
        this.X = X;
        this.Y = Y;
        this.Z = Z;
        this.T = T;
    }

    /**
     * Decode a 32-byte compressed Ed25519 point.
     * Returns null if the point is not on the curve.
     */
    public static Ed25519Point decode(byte[] encoded) {
        if (encoded == null || encoded.length != 32) return null;

        // Extract sign bit from high bit of last byte
        int sign = (encoded[31] & 0x80) >>> 7;

        // Decode y coordinate (clear high bit)
        byte[] yBytes = encoded.clone();
        yBytes[31] &= 0x7F;

        // Canonical check: reject if y >= p (2^255 - 19)
        // Round-trip decode→normalize→encode and compare to input.
        // Matches cardano_ge25519_is_canonical() in libsodium reference.
        int[] yCheck = X25519Field.create();
        X25519Field.decode(yBytes, 0, yCheck);
        X25519Field.normalize(yCheck);
        byte[] reEncoded = new byte[32];
        X25519Field.encode(yCheck, reEncoded, 0);
        if (!Arrays.equals(yBytes, reEncoded)) return null;

        int[] y = X25519Field.create();
        X25519Field.decode(yBytes, 0, y);

        // Compute x² = (y² - 1) / (d * y² + 1)
        int[] ySquared = X25519Field.create();
        X25519Field.sqr(y, ySquared);

        int[] u = X25519Field.create(); // y² - 1
        int[] one = X25519Field.create();
        X25519Field.one(one);
        X25519Field.sub(ySquared, one, u);

        int[] v = X25519Field.create(); // d * y² + 1
        X25519Field.mul(D, ySquared, v);
        X25519Field.add(v, one, v);

        int[] x = X25519Field.create();
        // sqrtRatioVar computes x = sqrt(u/v), returns true if square
        if (!X25519Field.sqrtRatioVar(u, v, x)) {
            return null; // not a valid point
        }

        X25519Field.normalize(x);

        // Apply sign bit
        if (isNegative(x) != sign) {
            if (X25519Field.isZeroVar(x)) return null;
            X25519Field.negate(x, x);
            X25519Field.normalize(x);
        }

        int[] z = X25519Field.create();
        X25519Field.one(z);

        int[] t = X25519Field.create();
        X25519Field.mul(x, y, t);

        return new Ed25519Point(x, y, z, t);
    }

    /**
     * Encode this point to its 32-byte compressed representation.
     */
    public byte[] encode() {
        // Compute affine coordinates: x = X/Z, y = Y/Z
        int[] zInv = X25519Field.create();
        X25519Field.inv(Z, zInv);

        int[] x = X25519Field.create();
        int[] y = X25519Field.create();
        X25519Field.mul(X, zInv, x);
        X25519Field.mul(Y, zInv, y);
        X25519Field.normalize(x);
        X25519Field.normalize(y);

        byte[] result = new byte[32];
        X25519Field.encode(y, result, 0);

        // Set sign bit (high bit of last byte) from x
        result[31] |= (byte)(isNegative(x) << 7);
        return result;
    }

    /**
     * Add this point and q using the unified addition formula for twisted Edwards
     * curves with a=-1 (add-2008-hwcd-4).
     */
    public Ed25519Point add(Ed25519Point q) {
        int[] a = X25519Field.create();
        int[] b = X25519Field.create();
        int[] c = X25519Field.create();
        int[] dd = X25519Field.create();
        int[] e = X25519Field.create();
        int[] f = X25519Field.create();
        int[] g = X25519Field.create();
        int[] h = X25519Field.create();

        int[] t1 = X25519Field.create();
        int[] t2 = X25519Field.create();

        // A = (Y1 - X1) * (Y2 - X2)
        X25519Field.sub(Y, X, t1);
        X25519Field.sub(q.Y, q.X, t2);
        X25519Field.mul(t1, t2, a);

        // B = (Y1 + X1) * (Y2 + X2)
        X25519Field.add(Y, X, t1);
        X25519Field.add(q.Y, q.X, t2);
        X25519Field.mul(t1, t2, b);

        // C = 2 * d * T1 * T2
        X25519Field.mul(T, q.T, c);
        X25519Field.mul(c, D2, c);

        // D = 2 * Z1 * Z2
        X25519Field.mul(Z, q.Z, dd);
        X25519Field.add(dd, dd, dd);

        // E = B - A, F = D - C, G = D + C, H = B + A
        X25519Field.sub(b, a, e);
        X25519Field.sub(dd, c, f);
        X25519Field.add(dd, c, g);
        X25519Field.add(b, a, h);

        int[] rx = X25519Field.create();
        int[] ry = X25519Field.create();
        int[] rz = X25519Field.create();
        int[] rt = X25519Field.create();

        X25519Field.mul(e, f, rx);  // X3 = E * F
        X25519Field.mul(g, h, ry);  // Y3 = G * H
        X25519Field.mul(e, h, rt);  // T3 = E * H
        X25519Field.mul(f, g, rz);  // Z3 = F * G

        return new Ed25519Point(rx, ry, rz, rt);
    }

    /**
     * Double this point using dbl-2008-hwcd for twisted Edwards with a=-1.
     * <p>
     * From https://hyperelliptic.org/EFD/g1p/auto-twisted-extended.html#doubling-dbl-2008-hwcd:
     * A = X1², B = Y1², C = 2*Z1², D = a*A = -A,
     * E = (X1+Y1)²-A-B, G = D+B, F = G-C, H = D-B,
     * X3 = E*F, Y3 = G*H, T3 = E*H, Z3 = F*G
     */
    public Ed25519Point doublePoint() {
        int[] a = X25519Field.create();
        int[] b = X25519Field.create();
        int[] c = X25519Field.create();

        // A = X1², B = Y1², C = 2*Z1²
        X25519Field.sqr(X, a);
        X25519Field.sqr(Y, b);
        X25519Field.sqr(Z, c);
        X25519Field.add(c, c, c);

        // D = a*A = -A (since a = -1 for Ed25519)
        int[] d = X25519Field.create();
        X25519Field.negate(a, d);

        // E = (X1+Y1)² - A - B
        int[] e = X25519Field.create();
        int[] t = X25519Field.create();
        X25519Field.add(X, Y, t);
        X25519Field.sqr(t, e);
        X25519Field.sub(e, a, e);
        X25519Field.sub(e, b, e);

        // G = D + B
        int[] g = X25519Field.create();
        X25519Field.add(d, b, g);

        // F = G - C
        int[] f = X25519Field.create();
        X25519Field.sub(g, c, f);

        // H = D - B
        int[] h = X25519Field.create();
        X25519Field.sub(d, b, h);

        int[] rx = X25519Field.create();
        int[] ry = X25519Field.create();
        int[] rz = X25519Field.create();
        int[] rt = X25519Field.create();

        X25519Field.mul(e, f, rx);  // X3 = E * F
        X25519Field.mul(g, h, ry);  // Y3 = G * H
        X25519Field.mul(e, h, rt);  // T3 = E * H
        X25519Field.mul(f, g, rz);  // Z3 = F * G

        return new Ed25519Point(rx, ry, rz, rt);
    }

    /**
     * Negate this point: -(X, Y, Z, T) = (-X, Y, Z, -T).
     */
    public Ed25519Point negate() {
        int[] nx = X25519Field.create();
        int[] nt = X25519Field.create();
        X25519Field.negate(X, nx);
        X25519Field.negate(T, nt);
        return new Ed25519Point(nx, Y.clone(), Z.clone(), nt);
    }

    /**
     * Subtract q from this point: this - q = this + (-q).
     */
    public Ed25519Point subtract(Ed25519Point q) {
        return add(q.negate());
    }

    /**
     * Multiply by cofactor (8) via 3 doublings.
     */
    public Ed25519Point multiplyByCofactor() {
        return doublePoint().doublePoint().doublePoint();
    }

    /**
     * Scalar multiplication using right-to-left double-and-add.
     * Scalar is in little-endian byte order (standard Ed25519 convention).
     * <p>
     * <b>SECURITY WARNING:</b> This is a <em>variable-time</em> implementation and is
     * vulnerable to timing side-channel attacks. It MUST NOT be used with secret scalars
     * (e.g., private keys, nonces). It is only safe for public values such as VRF proof
     * components (c, s) during verification.
     *
     * @param scalar little-endian scalar bytes (must be a public value, never a secret)
     * @return the resulting point
     */
    public Ed25519Point scalarMultiply(byte[] scalar) {
        Ed25519Point result = NEUTRAL;
        Ed25519Point base = this;

        for (int i = 0; i < scalar.length; i++) {
            int byteVal = scalar[i] & 0xFF;
            for (int bit = 0; bit < 8; bit++) {
                if ((byteVal & 1) != 0) {
                    result = result.add(base);
                }
                base = base.doublePoint();
                byteVal >>>= 1;
            }
        }
        return result;
    }

    /**
     * Check if a field element is "negative" (odd) per RFC 8032.
     */
    static int isNegative(int[] fe) {
        byte[] encoded = new byte[32];
        X25519Field.encode(fe, encoded, 0);
        return encoded[0] & 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Ed25519Point)) return false;
        return Arrays.equals(encode(), ((Ed25519Point) o).encode());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(encode());
    }
}
