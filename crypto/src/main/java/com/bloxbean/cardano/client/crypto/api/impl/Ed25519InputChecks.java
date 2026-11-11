package com.bloxbean.cardano.client.crypto.api.impl;

import com.bloxbean.cardano.client.util.HexUtil;

/**
 * The checks libsodium's {@code crypto_sign_ed25519_verify_detached} makes on its input before the equation, shared
 * by both {@link com.bloxbean.cardano.client.crypto.api.Ed25519Verifier} implementations.
 */
final class Ed25519InputChecks {

    /** The group order {@code L = 2^252 + 27742317777372353535851937790883648493}, little-endian. */
    private static final byte[] L = HexUtil.decodeHexString(
            "edd3f55c1a631258d69cf7a2def9de1400000000000000000000000000000010");

    /** libsodium's {@code ge25519_has_small_order} blacklist (last byte compared without its top bit). */
    private static final byte[][] SMALL_ORDER = {
            HexUtil.decodeHexString("0000000000000000000000000000000000000000000000000000000000000000"),
            HexUtil.decodeHexString("0100000000000000000000000000000000000000000000000000000000000000"),
            HexUtil.decodeHexString("26e8958fc2b227b045c3f489f2ef98f0d5dfac05d3c63339b13802886d53fc05"),
            HexUtil.decodeHexString("c7176a703d4dd84fba3c0b760d10670f2a2053fa2c39ccc64ec7fd7792ac037a"),
            HexUtil.decodeHexString("ecffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f"),
            HexUtil.decodeHexString("edffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f"),
            HexUtil.decodeHexString("eeffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff7f")};

    private Ed25519InputChecks() {
    }

    /**
     * Lengths ({@code null} and wrong lengths are refused, as this library always has), then libsodium's
     * {@code sc25519_is_canonical(S)}, {@code ge25519_has_small_order(R)}, {@code ge25519_is_canonical(A)} and
     * {@code ge25519_has_small_order(A)}.
     */
    static boolean pass(byte[] signature, byte[] publicKey) {
        return signature != null && publicKey != null && signature.length == 64 && publicKey.length == 32
                && scalarIsCanonical(signature) && !hasSmallOrder(signature) && pointIsCanonical(publicKey)
                && !hasSmallOrder(publicKey);
    }

    /** libsodium {@code sc25519_is_canonical(sig + 32)}: {@code S < L}. */
    private static boolean scalarIsCanonical(byte[] signature) {
        for (int i = 31; i >= 0; i--) {
            int s = signature[32 + i] & 0xff;
            int l = L[i] & 0xff;
            if (s != l) {
                return s < l;
            }
        }
        return false;
    }

    /** libsodium {@code ge25519_has_small_order}: the first 32 bytes match a blacklisted encoding. */
    private static boolean hasSmallOrder(byte[] bytes) {
        for (byte[] point : SMALL_ORDER) {
            boolean match = true;
            for (int j = 0; j < 31 && match; j++) {
                match = bytes[j] == point[j];
            }
            if (match && (bytes[31] & 0x7f) == (point[31] & 0xff)) {
                return true;
            }
        }
        return false;
    }

    /** libsodium {@code ge25519_is_canonical}: the encoded {@code y} (sign bit ignored) is below {@code p}. */
    private static boolean pointIsCanonical(byte[] point) {
        boolean allOnes = (point[31] & 0x7f) == 0x7f;
        for (int i = 30; i > 0 && allOnes; i--) {
            allOnes = (point[i] & 0xff) == 0xff;
        }
        return !(allOnes && (point[0] & 0xff) >= 0xed);
    }
}
