package com.bloxbean.cardano.client.crypto.vrf.bc;

import com.bloxbean.cardano.client.crypto.vrf.VrfException;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Shared VRF utility methods for ECVRF-EDWARDS25519-SHA512-Elligator2.
 * Used by both {@link BcVrfVerifier} and {@link BcVrfProver}.
 * <p>
 * Reference: https://tools.ietf.org/pdf/draft-irtf-cfrg-vrf-06.pdf
 */
class VrfUtil {

    static final int SUITE = 0x04;

    // Curve25519 / Ed25519 constants for Elligator2
    static final BigInteger PRIME = BigInteger.TWO.pow(255).subtract(BigInteger.valueOf(19));
    static final BigInteger MONTGOMERY_A = BigInteger.valueOf(486662);
    static final BigInteger TWO_INV = BigInteger.TWO.modInverse(PRIME);

    // Ed25519 group order L
    static final BigInteger L = BigInteger.TWO.pow(252)
            .add(new BigInteger("27742317777372353535851937790883648493"));

    private VrfUtil() {
    }

    /**
     * Elligator2 hash-to-curve per Section 5.4.1.2 of the VRF spec.
     */
    static Ed25519Point hashToCurveElligator2(byte[] publicKey, byte[] alpha) {
        // 1. hash_string = SHA-512(suite || 0x01 || PK || alpha)
        byte[] hashInput = new byte[1 + 1 + publicKey.length + alpha.length];
        hashInput[0] = (byte) SUITE;
        hashInput[1] = 0x01;
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
        Ed25519Point hPrelim = Ed25519Point.decode(yBytes);
        if (hPrelim == null) return null;

        // Cofactor multiplication: H = 8 * H_prelim
        return hPrelim.multiplyByCofactor();
    }

    /**
     * ECVRF Hash Points per Section 5.4.3.
     */
    static BigInteger hashPoints(Ed25519Point h, Ed25519Point gamma,
                                 Ed25519Point u, Ed25519Point v) {
        byte[] hEnc = h.encode();
        byte[] gammaEnc = gamma.encode();
        byte[] uEnc = u.encode();
        byte[] vEnc = v.encode();

        byte[] input = new byte[1 + 1 + 32 * 4];
        input[0] = (byte) SUITE;
        input[1] = 0x02;
        System.arraycopy(hEnc, 0, input, 2, 32);
        System.arraycopy(gammaEnc, 0, input, 34, 32);
        System.arraycopy(uEnc, 0, input, 66, 32);
        System.arraycopy(vEnc, 0, input, 98, 32);

        byte[] hash = sha512(input);

        // Truncate to first 16 bytes (128 bits), interpret as little-endian integer
        byte[] truncated = Arrays.copyOfRange(hash, 0, 16);
        return littleEndianToBigInteger(truncated);
    }

    static byte[] sha512(byte[] input) {
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
        for (int i = 0; i < len; i++) {
            result[i] = bigEndian[bigEndian.length - 1 - i];
        }
        return result;
    }
}
