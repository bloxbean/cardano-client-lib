package com.bloxbean.cardano.statetrees.api;

import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import com.bloxbean.cardano.statetrees.common.hash.Blake2b256;

import java.io.ByteArrayOutputStream;

/**
 * Verifier for Sparse Merkle Tree proofs.
 *
 * <p>Uses the SMT encoding format to recompute node hashes from proof
 * data and compare with the provided root.</p>
 *
 * @since 0.8.0
 */
public final class SparseMerkleVerifier {

    private static final byte[][] EMPTY = buildEmptyCommitments();

    private SparseMerkleVerifier() {
    }

    /**
     * Verifies an inclusion proof.
     *
     * @param root     the SMT root hash (null allowed for empty tree)
     * @param hashFn   the key hashing function (for computing key hash)
     * @param key      the raw key bytes
     * @param value    the raw value bytes (as stored)
     * @param siblings 256-length array of sibling digests
     * @return true if proof is valid for the given root
     */
    public static boolean verifyInclusion(byte[] root, HashFunction hashFn, byte[] key, byte[] value, byte[][] siblings) {
        if (siblings == null || siblings.length != 256) return false;
        byte[] keyHash = hashFn.digest(key);
        byte[] curr = leafDigest(keyHash, value);
        for (int d = 255; d >= 0; d--) {
            int bit = bitAt(keyHash, d);
            byte[] sib = siblings[d] == null ? EMPTY[d + 1] : siblings[d];
            curr = internalDigest(bit == 0 ? curr : sib, bit == 0 ? sib : curr);
        }
        byte[] expectedRoot = root == null || root.length == 0 ? EMPTY[0] : root;
        return eq(curr, expectedRoot);
    }

    /**
     * Verifies a non-inclusion proof (empty path).
     *
     * @param root     the SMT root hash (null allowed for empty tree)
     * @param hashFn   the key hashing function (for computing key hash)
     * @param key      the raw key bytes
     * @param siblings 256-length array of sibling digests
     * @return true if proof is valid for the given root
     */
    public static boolean verifyNonInclusion(byte[] root, HashFunction hashFn, byte[] key, byte[][] siblings) {
        if (siblings == null || siblings.length != 256) return false;
        byte[] keyHash = hashFn.digest(key);
        byte[] curr = EMPTY[256];
        for (int d = 255; d >= 0; d--) {
            int bit = bitAt(keyHash, d);
            byte[] sib = siblings[d] == null ? EMPTY[d + 1] : siblings[d];
            curr = internalDigest(bit == 0 ? curr : sib, bit == 0 ? sib : curr);
        }
        byte[] expectedRoot = root == null || root.length == 0 ? EMPTY[0] : root;
        return eq(curr, expectedRoot);
    }

    // Helpers
    private static byte[] leafDigest(byte[] keyHash, byte[] value) {
        try {
            Array arr = new Array();
            arr.add(new ByteString(new byte[]{1}));
            arr.add(new ByteString(keyHash));
            arr.add(new ByteString(value));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new CborEncoder(baos).encode(arr);
            return Blake2b256.digest(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] internalDigest(byte[] left, byte[] right) {
        try {
            Array arr = new Array();
            arr.add(new ByteString(new byte[]{0}));
            arr.add(new ByteString(left));
            arr.add(new ByteString(right));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            new CborEncoder(baos).encode(arr);
            return Blake2b256.digest(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[][] buildEmptyCommitments() {
        byte[][] empty = new byte[257][];
        empty[256] = Blake2b256.digest(new byte[]{0x00});
        for (int d = 255; d >= 0; d--) {
            byte[] child = empty[d + 1];
            byte[] concat = new byte[child.length * 2];
            System.arraycopy(child, 0, concat, 0, child.length);
            System.arraycopy(child, 0, concat, child.length, child.length);
            // Internal node digest over [0, left=child, right=child]
            empty[d] = internalDigest(child, child);
        }
        return empty;
    }

    private static int bitAt(byte[] hash, int bitIndex) {
        int byteIndex = bitIndex >>> 3; // /8
        int bitInByte = 7 - (bitIndex & 7);
        return (hash[byteIndex] >>> bitInByte) & 1;
    }

    private static boolean eq(byte[] a, byte[] b) {
        if (a == null || b == null) return false;
        if (a.length != b.length) return false;
        int r = 0;
        for (int i = 0; i < a.length; i++) r |= (a[i] ^ b[i]);
        return r == 0;
    }
}

