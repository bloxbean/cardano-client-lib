package com.bloxbean.cardano.client.crypto.vrf.bc;

import com.bloxbean.cardano.client.crypto.vrf.VrfException;
import com.bloxbean.cardano.client.crypto.vrf.VrfProver;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * ECVRF-EDWARDS25519-SHA512-Elligator2 prover using BouncyCastle's X25519Field.
 * <p>
 * Generates VRF proofs per Section 5.1 of draft-irtf-cfrg-vrf-06.
 * Uses deterministic nonce generation (matching libsodium's VRF implementation).
 * <p>
 * Reference: https://tools.ietf.org/pdf/draft-irtf-cfrg-vrf-06.pdf
 */
public class BcVrfProver implements VrfProver {

    private static final int SECRET_KEY_SIZE = 64; // 32-byte seed + 32-byte public key

    @Override
    public byte[] prove(byte[] secretKey, byte[] alpha) {
        if (secretKey == null || secretKey.length != SECRET_KEY_SIZE) {
            throw new VrfException("Invalid secret key size. Expected " + SECRET_KEY_SIZE
                    + " bytes, got " + (secretKey == null ? "null" : secretKey.length));
        }
        if (alpha == null) {
            throw new VrfException("Alpha must not be null");
        }

        // 1. Extract seed and public key from secret key
        byte[] seed = Arrays.copyOfRange(secretKey, 0, 32);
        byte[] pk = Arrays.copyOfRange(secretKey, 32, 64);

        // 2. Expand seed: az = SHA-512(seed)
        byte[] az = VrfUtil.sha512(seed);
        byte[] scalarBytes = Arrays.copyOfRange(az, 0, 32);
        byte[] nonceKey = Arrays.copyOfRange(az, 32, 64);

        // 3. Clamp scalar (RFC 8032 Ed25519 clamping)
        scalarBytes[0] &= 248;
        scalarBytes[31] &= 127;
        scalarBytes[31] |= 64;

        BigInteger x = VrfUtil.littleEndianToBigInteger(scalarBytes);

        // 4. H = hash_to_curve_elligator2(pk, alpha)
        Ed25519Point h = VrfUtil.hashToCurveElligator2(pk, alpha);
        if (h == null) {
            throw new VrfException("Failed to hash to curve");
        }

        // 5. Gamma = x * H
        Ed25519Point gamma = h.scalarMultiply(scalarBytes);

        // 6. Deterministic nonce: k = SHA-512(nonce_key || H.encode()) mod L
        byte[] hEncoded = h.encode();
        byte[] nonceInput = new byte[nonceKey.length + hEncoded.length];
        System.arraycopy(nonceKey, 0, nonceInput, 0, nonceKey.length);
        System.arraycopy(hEncoded, 0, nonceInput, nonceKey.length, hEncoded.length);
        byte[] nonceHash = VrfUtil.sha512(nonceInput);
        BigInteger k = VrfUtil.littleEndianToBigInteger(nonceHash).mod(VrfUtil.L);

        // 7. U = k * B, V = k * H
        byte[] kBytes = VrfUtil.bigIntegerToLittleEndian32(k);
        Ed25519Point u = Ed25519Point.BASE_POINT.scalarMultiply(kBytes);
        Ed25519Point v = h.scalarMultiply(kBytes);

        // 8. c = hash_points(H, Gamma, U, V)
        BigInteger c = VrfUtil.hashPoints(h, gamma, u, v);

        // 9. s = (k + c * x) mod L
        BigInteger s = k.add(c.multiply(x)).mod(VrfUtil.L);

        // 10. Encode proof: Gamma(32) || c(16) || s(32) = 80 bytes
        byte[] proof = new byte[80];
        byte[] gammaEncoded = gamma.encode();
        System.arraycopy(gammaEncoded, 0, proof, 0, 32);

        byte[] cBytes = VrfUtil.bigIntegerToLittleEndian32(c);
        System.arraycopy(cBytes, 0, proof, 32, 16); // truncate to 16 bytes

        byte[] sBytes = VrfUtil.bigIntegerToLittleEndian32(s);
        System.arraycopy(sBytes, 0, proof, 48, 32);

        return proof;
    }
}
