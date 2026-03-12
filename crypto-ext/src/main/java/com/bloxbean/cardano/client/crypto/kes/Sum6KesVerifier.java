package com.bloxbean.cardano.client.crypto.kes;

import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.crypto.api.impl.EdDSASigningProvider;

import java.util.Arrays;

/**
 * KES (Key Evolving Signature) verifier for Sum6Kes, used by Cardano Praos.
 * <p>
 * Sum6Kes uses a recursive binary tree of depth 6, supporting 64 KES periods.
 * The signature is a chain of 6 levels of (inner_sigma, lhs_pk, rhs_pk), with an
 * Ed25519 signature at the leaf. Total signature size is 448 bytes.
 * <p>
 * Verification algorithm (from kes-summed-ed25519):
 * <pre>
 *   verify(period, pk, message):
 *     1. Check: blake2b_256(lhs_pk || rhs_pk) == pk
 *     2. If period is in left half: recurse with lhs_pk
 *        Else: recurse with rhs_pk, adjusted period
 *     3. At leaf (depth 0): Ed25519 verify(message, signature, pk)
 * </pre>
 */
public class Sum6KesVerifier implements KesVerifier {

    private static final int PUBLIC_KEY_SIZE = 32;
    private static final int ED25519_SIG_SIZE = 64;
    private static final int DEPTH = 6;

    /**
     * Total KES periods supported (2^6 = 64)
     */
    public static final int TOTAL_PERIODS = 1 << DEPTH;

    /**
     * Expected signature size: 64 + 6 * 64 = 448 bytes
     */
    public static final int SIGNATURE_SIZE = ED25519_SIG_SIZE + DEPTH * (2 * PUBLIC_KEY_SIZE);

    private final EdDSASigningProvider ed25519 = new EdDSASigningProvider();

    @Override
    public boolean verify(byte[] signature, byte[] message, byte[] publicKey, int period) {
        if (signature == null || signature.length != SIGNATURE_SIZE) {
            throw new KesException("Invalid KES signature size. Expected " + SIGNATURE_SIZE
                    + " bytes, got " + (signature == null ? "null" : signature.length));
        }
        if (publicKey == null || publicKey.length != PUBLIC_KEY_SIZE) {
            throw new KesException("Invalid KES public key size. Expected " + PUBLIC_KEY_SIZE
                    + " bytes, got " + (publicKey == null ? "null" : publicKey.length));
        }
        if (period < 0 || period >= TOTAL_PERIODS) {
            throw new KesException("KES period out of range: " + period
                    + ". Must be 0.." + (TOTAL_PERIODS - 1));
        }

        return verifyRecursive(signature, 0, DEPTH, message, publicKey, period);
    }

    private boolean verifyRecursive(byte[] sigBytes, int offset, int depth,
                                    byte[] message, byte[] expectedPk, int period) {
        if (depth == 0) {
            // Leaf level: Ed25519 signature verification
            byte[] sig = Arrays.copyOfRange(sigBytes, offset, offset + ED25519_SIG_SIZE);
            try {
                return ed25519.verify(sig, message, expectedPk);
            } catch (Exception e) {
                return false;
            }
        }

        // Inner node: parse (sigma, lhs_pk, rhs_pk)
        int innerSigSize = ED25519_SIG_SIZE + (depth - 1) * (2 * PUBLIC_KEY_SIZE);
        int lhsOffset = offset + innerSigSize;
        int rhsOffset = lhsOffset + PUBLIC_KEY_SIZE;

        byte[] lhsPk = Arrays.copyOfRange(sigBytes, lhsOffset, lhsOffset + PUBLIC_KEY_SIZE);
        byte[] rhsPk = Arrays.copyOfRange(sigBytes, rhsOffset, rhsOffset + PUBLIC_KEY_SIZE);

        // Verify: blake2b_256(lhs_pk || rhs_pk) must equal expected public key
        byte[] computedPk = hashPair(lhsPk, rhsPk);
        if (!Arrays.equals(computedPk, expectedPk)) {
            return false;
        }

        // Recurse into left or right subtree based on period
        int half = 1 << (depth - 1); // 2^(depth-1)
        if (period < half) {
            return verifyRecursive(sigBytes, offset, depth - 1, message, lhsPk, period);
        } else {
            return verifyRecursive(sigBytes, offset, depth - 1, message, rhsPk, period - half);
        }
    }

    /**
     * Compute blake2b_256(left || right) - the hash_pair operation from KES.
     */
    private static byte[] hashPair(byte[] left, byte[] right) {
        byte[] combined = new byte[left.length + right.length];
        System.arraycopy(left, 0, combined, 0, left.length);
        System.arraycopy(right, 0, combined, left.length, right.length);
        return Blake2bUtil.blake2bHash256(combined);
    }
}
