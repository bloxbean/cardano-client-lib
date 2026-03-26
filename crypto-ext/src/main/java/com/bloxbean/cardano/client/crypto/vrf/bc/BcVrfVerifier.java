package com.bloxbean.cardano.client.crypto.vrf.bc;

import com.bloxbean.cardano.client.crypto.vrf.VrfException;
import com.bloxbean.cardano.client.crypto.vrf.VrfResult;
import com.bloxbean.cardano.client.crypto.vrf.VrfVerifier;

import java.math.BigInteger;
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
        Ed25519Point h = VrfUtil.hashToCurveElligator2(publicKey, alpha);
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
        BigInteger cPrime = VrfUtil.hashPoints(h, gamma, u, v);

        // 7. Check c == c'
        BigInteger cValue = VrfUtil.littleEndianToBigInteger(cBytes);
        if (!cValue.equals(cPrime)) {
            return VrfResult.invalid();
        }

        // 8. Compute VRF output: beta = SHA-512(suite || 0x03 || encode(cofactor * Gamma))
        Ed25519Point cofactorGamma = gamma.multiplyByCofactor();
        byte[] cofactorGammaEncoded = cofactorGamma.encode();

        byte[] hashInput = new byte[1 + 1 + 32];
        hashInput[0] = (byte) VrfUtil.SUITE;
        hashInput[1] = 0x03;
        System.arraycopy(cofactorGammaEncoded, 0, hashInput, 2, 32);

        byte[] beta = VrfUtil.sha512(hashInput);
        return VrfResult.valid(beta);
    }

    /**
     * Elligator2 hash-to-curve per Section 5.4.1.2 of the VRF spec.
     * Exposed for testing intermediate values.
     */
    Ed25519Point hashToCurveElligator2(byte[] publicKey, byte[] alpha) {
        return VrfUtil.hashToCurveElligator2(publicKey, alpha);
    }
}
