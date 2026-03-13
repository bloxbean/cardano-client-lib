package com.bloxbean.cardano.client.crypto.vrf.bc;

import com.bloxbean.cardano.client.crypto.vrf.VrfException;
import com.bloxbean.cardano.client.crypto.vrf.VrfResult;
import com.bloxbean.cardano.client.crypto.vrf.VrfVerifier;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * ECVRF-EDWARDS25519-SHA512-Elligator2 verifier using BouncyCastle's X25519Field.
 * <p>
 * This is a parallel implementation to {@link com.bloxbean.cardano.client.crypto.vrf.EcVrfVerifier}
 * that uses {@link Ed25519Point} (built on BC's public X25519Field API) instead of
 * i2p-crypto-eddsa's GroupElement.
 * <p>
 * Reference: https://tools.ietf.org/pdf/draft-irtf-cfrg-vrf-06.pdf
 */
public class BcVrfVerifier implements VrfVerifier {

    private static final int PROOF_SIZE = 80; // 32 (point) + 16 (c) + 32 (s)

    // Curve25519 / Ed25519 constants for Elligator2
    private static final BigInteger PRIME = BigInteger.TWO.pow(255).subtract(BigInteger.valueOf(19));
    private static final BigInteger MONTGOMERY_A = BigInteger.valueOf(486662);
    private static final BigInteger TWO_INV = BigInteger.TWO.modInverse(PRIME);

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
            return VrfResult.invalid();
        }
    }

    private VrfResult doVerify(byte[] publicKey, byte[] proof, byte[] alpha) {
        // 1. Decode proof: Gamma (point), c (16-byte int), s (32-byte int)
        byte[] gammaBytes = Arrays.copyOfRange(proof, 0, 32);
        byte[] cBytes = Arrays.copyOfRange(proof, 32, 48);
        byte[] sBytes = Arrays.copyOfRange(proof, 48, 80);

        Ed25519Point gamma = Ed25519Point.decode(gammaBytes);
        if (gamma == null) return VrfResult.invalid();

        // 2. H = hash_to_curve_elligator2(suite, Y, alpha)
        Ed25519Point h = hashToCurveElligator2(publicKey, alpha);
        if (h == null) return VrfResult.invalid();

        // 3. Decode public key Y
        Ed25519Point yPoint = Ed25519Point.decode(publicKey);
        if (yPoint == null) return VrfResult.invalid();

        // Reject small-order public keys (matches libsodium's has_small_order check)
        if (yPoint.multiplyByCofactor().equals(Ed25519Point.NEUTRAL)) {
            return VrfResult.invalid();
        }

        // Pad c to 32 bytes for scalar operations
        byte[] cBytes32 = new byte[32];
        System.arraycopy(cBytes, 0, cBytes32, 0, 16);

        // 4. U = s*B - c*Y
        Ed25519Point sB = Ed25519Point.BASE_POINT.scalarMultiply(sBytes);
        Ed25519Point cY = yPoint.scalarMultiply(cBytes32);
        Ed25519Point u = sB.subtract(cY);

        // 5. V = s*H - c*Gamma
        Ed25519Point sH = h.scalarMultiply(sBytes);
        Ed25519Point cGamma = gamma.scalarMultiply(cBytes32);
        Ed25519Point v = sH.subtract(cGamma);

        // 6. c' = hash_points(H, Gamma, U, V)
        BigInteger cPrime = hashPoints(h, gamma, u, v);

        // 7. Check c == c'
        BigInteger cValue = littleEndianToBigInteger(cBytes);
        if (!cValue.equals(cPrime)) {
            return VrfResult.invalid();
        }

        // 8. Compute VRF output: beta = SHA-512(suite || 0x03 || encode(cofactor * Gamma))
        Ed25519Point cofactorGamma = gamma.multiplyByCofactor();
        byte[] cofactorGammaEncoded = cofactorGamma.encode();

        byte[] hashInput = new byte[1 + 1 + 32];
        hashInput[0] = 0x04; // suite
        hashInput[1] = 0x03; // three_string
        System.arraycopy(cofactorGammaEncoded, 0, hashInput, 2, 32);

        byte[] beta = sha512(hashInput);
        return VrfResult.valid(beta);
    }

    /**
     * Elligator2 hash-to-curve per Section 5.4.1.2 of the VRF spec.
     */
    Ed25519Point hashToCurveElligator2(byte[] publicKey, byte[] alpha) {
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
        // u = -A / (1 + 2*r²) mod p
        BigInteger rSquared = r.multiply(r).mod(PRIME);
        BigInteger denom = BigInteger.ONE.add(BigInteger.TWO.multiply(rSquared)).mod(PRIME);
        BigInteger u = PRIME.subtract(MONTGOMERY_A).multiply(denom.modInverse(PRIME)).mod(PRIME);

        // w = u * (u² + A*u + 1) mod p
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
    private BigInteger hashPoints(Ed25519Point h, Ed25519Point gamma,
                                  Ed25519Point u, Ed25519Point v) {
        byte[] hEnc = h.encode();
        byte[] gammaEnc = gamma.encode();
        byte[] uEnc = u.encode();
        byte[] vEnc = v.encode();

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
        for (int i = 0; i < len; i++) {
            result[i] = bigEndian[bigEndian.length - 1 - i];
        }
        return result;
    }
}
