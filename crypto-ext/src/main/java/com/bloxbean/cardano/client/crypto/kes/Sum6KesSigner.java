package com.bloxbean.cardano.client.crypto.kes;

import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.crypto.api.impl.EdDSASigningProvider;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec;

import java.util.Arrays;

/**
 * KES (Key Evolving Signature) signer for Sum6Kes, used by Cardano Praos.
 * <p>
 * Sum6Kes uses a recursive binary tree of depth 6, supporting 64 KES periods.
 * The secret key is 608 bytes, structured as a recursive tree:
 * <pre>
 *   Size(0) = 32 bytes (Ed25519 seed)
 *   Size(d) = Size(d-1) + 96 bytes (inner_key + next_seed + lhs_vk + rhs_vk)
 *   Size(6) = 32 + 6*96 = 608 bytes
 * </pre>
 * <p>
 * The signature is 448 bytes: a 64-byte Ed25519 signature at the leaf,
 * plus 6 levels of (lhs_vk, rhs_vk) pairs (6 * 64 = 384 bytes).
 * <p>
 * This signer supports signing at any period (0-63) from the initial key state.
 * When the requested period requires the right subtree at any level, the right
 * subtree key is derived on-the-fly from the next_seed using Blake2b-256 expansion:
 * {@code r0 = blake2b_256(0x01 || seed), r1 = blake2b_256(0x02 || seed)}.
 */
public class Sum6KesSigner implements KesSigner {

    private static final int DEPTH = 6;
    private static final int TOTAL_PERIODS = 1 << DEPTH; // 64
    private static final int LEAF_KEY_SIZE = 32;
    private static final int ED25519_SIG_SIZE = 64;
    private static final int PUBLIC_KEY_SIZE = 32;
    private static final int SECRET_KEY_SIZE = LEAF_KEY_SIZE + DEPTH * (LEAF_KEY_SIZE + 2 * PUBLIC_KEY_SIZE); // 608

    /**
     * Expected KES signature size: 64 + 6 * 64 = 448 bytes
     */
    public static final int SIGNATURE_SIZE = ED25519_SIG_SIZE + DEPTH * (2 * PUBLIC_KEY_SIZE);

    private static final EdDSAParameterSpec ED25519_SPEC =
            EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);

    private final EdDSASigningProvider ed25519 = new EdDSASigningProvider();

    @Override
    public byte[] sign(byte[] secretKey, byte[] message, int period) {
        if (secretKey == null || secretKey.length != SECRET_KEY_SIZE) {
            throw new KesException("Invalid KES secret key size. Expected " + SECRET_KEY_SIZE
                    + " bytes, got " + (secretKey == null ? "null" : secretKey.length));
        }
        if (message == null) {
            throw new KesException("Message must not be null");
        }
        if (period < 0 || period >= TOTAL_PERIODS) {
            throw new KesException("KES period out of range: " + period
                    + ". Must be 0.." + (TOTAL_PERIODS - 1));
        }

        return signRecursive(secretKey, 0, DEPTH, message, period);
    }

    /**
     * Recursively navigate the KES tree and produce a signature.
     * <p>
     * Key layout at each depth:
     * <pre>
     *   depth 0: seed(32)
     *   depth d: inner_key(Size(d-1)) | next_seed(32) | lhs_vk(32) | rhs_vk(32)
     * </pre>
     * <p>
     * When the period requires the right subtree, the right subtree key is
     * generated from next_seed using {@link #genKeyFromSeed(byte[], int)}.
     */
    private byte[] signRecursive(byte[] key, int offset, int depth,
                                  byte[] message, int period) {
        if (depth == 0) {
            // Leaf: Ed25519 sign with 32-byte seed
            byte[] seed = Arrays.copyOfRange(key, offset, offset + LEAF_KEY_SIZE);
            return ed25519.sign(message, seed);
        }

        // Inner node layout
        int innerKeySize = keySize(depth - 1);
        int nextSeedOffset = offset + innerKeySize;
        int lhsVkOffset = nextSeedOffset + LEAF_KEY_SIZE;
        int rhsVkOffset = lhsVkOffset + PUBLIC_KEY_SIZE;

        byte[] lhsVk = Arrays.copyOfRange(key, lhsVkOffset, lhsVkOffset + PUBLIC_KEY_SIZE);
        byte[] rhsVk = Arrays.copyOfRange(key, rhsVkOffset, rhsVkOffset + PUBLIC_KEY_SIZE);

        int half = 1 << (depth - 1);

        byte[] innerSig;
        if (period < half) {
            // Left subtree: use inner_key directly
            innerSig = signRecursive(key, offset, depth - 1, message, period);
        } else {
            // Right subtree: generate from next_seed
            byte[] nextSeed = Arrays.copyOfRange(key, nextSeedOffset, nextSeedOffset + LEAF_KEY_SIZE);
            byte[] rightKey = genKeyFromSeed(nextSeed, depth - 1);
            innerSig = signRecursive(rightKey, 0, depth - 1, message, period - half);
        }

        // Append lhs_vk and rhs_vk to the signature
        byte[] sig = new byte[innerSig.length + 2 * PUBLIC_KEY_SIZE];
        System.arraycopy(innerSig, 0, sig, 0, innerSig.length);
        System.arraycopy(lhsVk, 0, sig, innerSig.length, PUBLIC_KEY_SIZE);
        System.arraycopy(rhsVk, 0, sig, innerSig.length + PUBLIC_KEY_SIZE, PUBLIC_KEY_SIZE);

        return sig;
    }

    /**
     * Generate a full KES key from a 32-byte seed at the given depth.
     * <p>
     * Seed expansion uses Blake2b-256:
     * <pre>
     *   r0 = blake2b_256(0x01 || seed)  — left child seed
     *   r1 = blake2b_256(0x02 || seed)  — right child seed (stored as next_seed)
     * </pre>
     *
     * @param seed  32-byte seed
     * @param depth tree depth (0 = leaf)
     * @return key bytes of size keySize(depth)
     */
    static byte[] genKeyFromSeed(byte[] seed, int depth) {
        if (depth == 0) {
            return seed.clone();
        }

        // Expand seed
        byte[] r0 = expandSeed(seed, (byte) 0x01);
        byte[] r1 = expandSeed(seed, (byte) 0x02);

        // Recursively generate left and right keys
        byte[] leftKey = genKeyFromSeed(r0, depth - 1);
        byte[] rightKey = genKeyFromSeed(r1, depth - 1);

        // Derive verification keys
        byte[] lhsVk = deriveVerificationKeyFromKey(leftKey, depth - 1);
        byte[] rhsVk = deriveVerificationKeyFromKey(rightKey, depth - 1);

        // Assemble: left_key || r1 || lhs_vk || rhs_vk
        int totalSize = keySize(depth);
        byte[] result = new byte[totalSize];
        System.arraycopy(leftKey, 0, result, 0, leftKey.length);
        System.arraycopy(r1, 0, result, leftKey.length, 32);
        System.arraycopy(lhsVk, 0, result, leftKey.length + 32, 32);
        System.arraycopy(rhsVk, 0, result, leftKey.length + 64, 32);

        return result;
    }

    /**
     * Expand a seed with a prefix byte using Blake2b-256.
     */
    private static byte[] expandSeed(byte[] seed, byte prefix) {
        byte[] input = new byte[1 + seed.length];
        input[0] = prefix;
        System.arraycopy(seed, 0, input, 1, seed.length);
        return Blake2bUtil.blake2bHash256(input);
    }

    /**
     * Derive the 32-byte verification key from a key at a given depth.
     */
    private static byte[] deriveVerificationKeyFromKey(byte[] key, int depth) {
        if (depth == 0) {
            // Leaf: derive Ed25519 public key from 32-byte seed
            return ed25519PublicKeyFromSeed(key);
        }

        // Inner node: vk = blake2b_256(lhs_vk || rhs_vk)
        int innerSize = keySize(depth - 1);
        int lhsVkOffset = innerSize + LEAF_KEY_SIZE;
        int rhsVkOffset = lhsVkOffset + PUBLIC_KEY_SIZE;

        byte[] topPks = new byte[2 * PUBLIC_KEY_SIZE];
        System.arraycopy(key, lhsVkOffset, topPks, 0, PUBLIC_KEY_SIZE);
        System.arraycopy(key, rhsVkOffset, topPks, PUBLIC_KEY_SIZE, PUBLIC_KEY_SIZE);
        return Blake2bUtil.blake2bHash256(topPks);
    }

    /**
     * Derive Ed25519 public key from a 32-byte seed.
     */
    private static byte[] ed25519PublicKeyFromSeed(byte[] seed) {
        EdDSAPrivateKeySpec privSpec = new EdDSAPrivateKeySpec(seed, ED25519_SPEC);
        return privSpec.getA().toByteArray();
    }

    /**
     * Compute the secret key size for a given depth.
     * Size(0) = 32, Size(d) = Size(d-1) + 96
     */
    private static int keySize(int depth) {
        return LEAF_KEY_SIZE + depth * (LEAF_KEY_SIZE + 2 * PUBLIC_KEY_SIZE);
    }

    /**
     * Derive the KES verification key (root public key) from a secret key.
     *
     * @param secretKey the 608-byte KES secret key
     * @return the 32-byte KES verification key (blake2b_256 of top-level lhs_vk || rhs_vk)
     */
    public byte[] deriveVerificationKey(byte[] secretKey) {
        if (secretKey == null || secretKey.length != SECRET_KEY_SIZE) {
            throw new KesException("Invalid KES secret key size. Expected " + SECRET_KEY_SIZE
                    + " bytes, got " + (secretKey == null ? "null" : secretKey.length));
        }

        // Top-level lhs_vk and rhs_vk are the last 64 bytes
        byte[] topPks = new byte[2 * PUBLIC_KEY_SIZE];
        System.arraycopy(secretKey, SECRET_KEY_SIZE - 2 * PUBLIC_KEY_SIZE, topPks, 0, 2 * PUBLIC_KEY_SIZE);
        return Blake2bUtil.blake2bHash256(topPks);
    }
}
