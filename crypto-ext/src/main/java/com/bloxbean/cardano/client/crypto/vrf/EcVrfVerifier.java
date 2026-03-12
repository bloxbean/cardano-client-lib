package com.bloxbean.cardano.client.crypto.vrf;

import net.i2p.crypto.eddsa.math.Curve;
import net.i2p.crypto.eddsa.math.GroupElement;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveSpec;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * ECVRF-EDWARDS25519-SHA512-Elligator2 verifier per IETF draft-irtf-cfrg-vrf-06.
 * <p>
 * This implements the VRF verification algorithm used by Cardano for leader election.
 * The algorithm uses Ed25519 curve operations with SHA-512 hashing and the
 * Elligator2 hash-to-curve method.
 * <p>
 * Reference: https://tools.ietf.org/pdf/draft-irtf-cfrg-vrf-06.pdf
 */
public class EcVrfVerifier implements VrfVerifier {

    private static final int PROOF_SIZE = 80; // 32 (point) + 16 (c) + 32 (s)

    // Curve25519 / Ed25519 constants for Elligator2
    private static final BigInteger PRIME = BigInteger.TWO.pow(255).subtract(BigInteger.valueOf(19));
    private static final BigInteger MONTGOMERY_A = BigInteger.valueOf(486662);
    private static final BigInteger TWO_INV = BigInteger.TWO.modInverse(PRIME);

    private static final EdDSANamedCurveSpec ED25519_SPEC =
            EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
    private static final Curve CURVE = ED25519_SPEC.getCurve();
    private static final GroupElement BASE_POINT = ED25519_SPEC.getB();

    // Scalar for cofactor multiplication: 8 as little-endian 32 bytes
    private static final byte[] COFACTOR_SCALAR = new byte[32];
    static {
        COFACTOR_SCALAR[0] = 8;
    }

    @Override
    public VrfResult verify(byte[] publicKey, byte[] proof, byte[] alpha) {
        if (publicKey == null || publicKey.length != 32) {
            throw new VrfException("Invalid public key size. Expected 32 bytes, got "
                    + (publicKey == null ? "null" : publicKey.length));
        }
        if (proof == null || proof.length != PROOF_SIZE) {
            throw new VrfException("Invalid proof size. Expected " + PROOF_SIZE
                    + " bytes, got " + (proof == null ? "null" : proof.length));
        }
        if (alpha == null) {
            throw new VrfException("Alpha must not be null");
        }

        try {
            return doVerify(publicKey, proof, alpha);
        } catch (VrfException e) {
            throw e;
        } catch (Exception e) {
            // Point decoding or other crypto failures => INVALID
            return VrfResult.invalid();
        }
    }

    private VrfResult doVerify(byte[] publicKey, byte[] proof, byte[] alpha) {
        // 1. Decode proof: Gamma (point), c (16-byte int), s (32-byte int)
        byte[] gammaBytes = Arrays.copyOfRange(proof, 0, 32);
        byte[] cBytes = Arrays.copyOfRange(proof, 32, 48);
        byte[] sBytes = Arrays.copyOfRange(proof, 48, 80);

        GroupElement gamma = decodePoint(gammaBytes);
        if (gamma == null) return VrfResult.invalid();

        // 2. H = hash_to_curve_elligator2(suite, Y, alpha)
        GroupElement h = hashToCurveElligator2(publicKey, alpha);
        if (h == null) return VrfResult.invalid();

        // 3. Decode public key Y
        GroupElement yPoint = decodePoint(publicKey);
        if (yPoint == null) return VrfResult.invalid();

        // Pad c to 32 bytes for scalar operations
        byte[] cBytes32 = new byte[32];
        System.arraycopy(cBytes, 0, cBytes32, 0, 16);

        // 4. U = s*B - c*Y
        GroupElement sB = BASE_POINT.scalarMultiply(sBytes);
        GroupElement cY = precompute(yPoint).scalarMultiply(cBytes32);
        GroupElement u = addPoints(sB, negatePoint(cY));

        // 5. V = s*H - c*Gamma
        GroupElement sH = precompute(h).scalarMultiply(sBytes);
        GroupElement cGamma = precompute(gamma).scalarMultiply(cBytes32);
        GroupElement v = addPoints(sH, negatePoint(cGamma));

        // 6. c' = hash_points(H, Gamma, U, V)
        BigInteger cPrime = hashPoints(h, gamma, u, v);

        // 7. Check c == c'
        BigInteger cValue = littleEndianToBigInteger(cBytes);
        if (!cValue.equals(cPrime)) {
            return VrfResult.invalid();
        }

        // 8. Compute VRF output: beta = SHA-512(suite || 0x03 || encode(cofactor * Gamma))
        GroupElement cofactorGamma = precompute(gamma).scalarMultiply(COFACTOR_SCALAR);
        byte[] cofactorGammaEncoded = cofactorGamma.toP3().toByteArray();

        byte[] hashInput = new byte[1 + 1 + 32];
        hashInput[0] = 0x04; // suite
        hashInput[1] = 0x03; // three_string
        System.arraycopy(cofactorGammaEncoded, 0, hashInput, 2, 32);

        byte[] beta = sha512(hashInput);
        return VrfResult.valid(beta);
    }

    /**
     * Elligator2 hash-to-curve per Section 5.4.1.2 of the VRF spec.
     * Maps (publicKey, alpha) to an Ed25519 curve point.
     */
    GroupElement hashToCurveElligator2(byte[] publicKey, byte[] alpha) {
        // 1. hash_string = SHA-512(suite || 0x01 || PK || alpha)
        byte[] hashInput = new byte[1 + 1 + publicKey.length + alpha.length];
        hashInput[0] = 0x04; // suite
        hashInput[1] = 0x01; // one_string
        System.arraycopy(publicKey, 0, hashInput, 2, publicKey.length);
        System.arraycopy(alpha, 0, hashInput, 2 + publicKey.length, alpha.length);
        byte[] hashString = sha512(hashInput);

        // 2. Take first 32 bytes, clear high bit
        byte[] rString = Arrays.copyOfRange(hashString, 0, 32);
        rString[31] &= 0x7F;

        // 3. r = string_to_int(r_string) - little-endian
        BigInteger r = littleEndianToBigInteger(rString);

        // 4-12. Elligator2 computation in Montgomery domain
        // u = -A / (1 + 2*r^2) mod p
        BigInteger rSquared = r.multiply(r).mod(PRIME);
        BigInteger denom = BigInteger.ONE.add(BigInteger.TWO.multiply(rSquared)).mod(PRIME);
        BigInteger u = PRIME.subtract(MONTGOMERY_A).multiply(denom.modInverse(PRIME)).mod(PRIME);

        // w = u * (u^2 + A*u + 1) mod p
        BigInteger uSquared = u.multiply(u).mod(PRIME);
        BigInteger w = u.multiply(
                uSquared.add(MONTGOMERY_A.multiply(u)).add(BigInteger.ONE).mod(PRIME)
        ).mod(PRIME);

        // Legendre symbol: e = w^((p-1)/2) mod p
        BigInteger e = w.modPow(PRIME.subtract(BigInteger.ONE).divide(BigInteger.TWO), PRIME);

        // final_u = e*u + (e-1)*A*inv(2) mod p
        BigInteger finalU = e.multiply(u).add(
                e.subtract(BigInteger.ONE).multiply(MONTGOMERY_A).multiply(TWO_INV)
        ).mod(PRIME);

        // Montgomery to Edwards: y_coord = (finalU - 1) / (finalU + 1) mod p
        BigInteger yCoord = finalU.subtract(BigInteger.ONE)
                .multiply(finalU.add(BigInteger.ONE).modInverse(PRIME))
                .mod(PRIME);

        // Encode y_coord as 32 bytes little-endian and decode as Edwards point
        byte[] yBytes = bigIntegerToLittleEndian32(yCoord);
        GroupElement hPrelim = decodePoint(yBytes);
        if (hPrelim == null) return null;

        // Cofactor multiplication: H = 8 * H_prelim
        return precompute(hPrelim).scalarMultiply(COFACTOR_SCALAR).toP3();
    }

    /**
     * ECVRF Hash Points per Section 5.4.3.
     * Returns c = hash(suite || 0x02 || encode(H) || encode(Gamma) || encode(U) || encode(V)),
     * truncated to 128 bits.
     */
    private BigInteger hashPoints(GroupElement h, GroupElement gamma, GroupElement u, GroupElement v) {
        byte[] hEnc = encodePoint(h);
        byte[] gammaEnc = encodePoint(gamma);
        byte[] uEnc = encodePoint(u);
        byte[] vEnc = encodePoint(v);

        byte[] input = new byte[1 + 1 + 32 * 4];
        input[0] = 0x04; // suite
        input[1] = 0x02; // two_string
        System.arraycopy(hEnc, 0, input, 2, 32);
        System.arraycopy(gammaEnc, 0, input, 34, 32);
        System.arraycopy(uEnc, 0, input, 66, 32);
        System.arraycopy(vEnc, 0, input, 98, 32);

        byte[] hash = sha512(input);

        // Truncate to first 16 bytes (128 bits), interpret as little-endian integer
        byte[] truncated = Arrays.copyOfRange(hash, 0, 16);
        return littleEndianToBigInteger(truncated);
    }

    /**
     * Decode a 32-byte compressed Ed25519 point.
     */
    private GroupElement decodePoint(byte[] encoded) {
        try {
            GroupElement ge = new GroupElement(CURVE, encoded);
            if (!ge.isOnCurve()) return null;
            return ge;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Encode a point to its 32-byte compressed representation.
     */
    private byte[] encodePoint(GroupElement ge) {
        return ge.toP3().toByteArray();
    }

    /**
     * Create a GroupElement with precomputed single tables, enabling scalarMultiply.
     * The i2p library requires precomputed tables for the scalarMultiply operation.
     * We re-encode and decode with precompute=true since precmp is a final field.
     */
    private GroupElement precompute(GroupElement ge) {
        byte[] encoded = ge.toP3().toByteArray();
        return new GroupElement(CURVE, encoded, true);
    }

    /**
     * Negate a point (flip the sign of X coordinate in Edwards form).
     */
    private GroupElement negatePoint(GroupElement ge) {
        return ge.toP3().negate();
    }

    /**
     * Add two points: a + b.
     */
    private GroupElement addPoints(GroupElement a, GroupElement b) {
        return a.toP3().add(b.toP3().toCached()).toP3();
    }

    private static byte[] sha512(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-512").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new VrfException("SHA-512 not available", e);
        }
    }

    static BigInteger littleEndianToBigInteger(byte[] bytes) {
        byte[] reversed = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            reversed[bytes.length - 1 - i] = bytes[i];
        }
        return new BigInteger(1, reversed);
    }

    static byte[] bigIntegerToLittleEndian32(BigInteger value) {
        byte[] result = new byte[32];
        byte[] bigEndian = value.toByteArray();
        int len = Math.min(bigEndian.length, 32);
        // Write in reverse (big-endian -> little-endian)
        for (int i = 0; i < len; i++) {
            result[i] = bigEndian[bigEndian.length - 1 - i];
        }
        return result;
    }
}
