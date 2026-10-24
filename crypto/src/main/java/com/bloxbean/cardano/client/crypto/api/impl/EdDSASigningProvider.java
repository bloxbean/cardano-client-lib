package com.bloxbean.cardano.client.crypto.api.impl;

import com.bloxbean.cardano.client.crypto.CryptoException;
import com.bloxbean.cardano.client.crypto.api.SigningProvider;
import com.bloxbean.cardano.client.util.HexUtil;
import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;

import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;

public class EdDSASigningProvider implements SigningProvider {
    private static final EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);

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

    /**
     * Signs provided message using Ed25519 signing algorithm with ED25519 seed (private key)
     *
     * @param message the byte array of the message to be signed.
     * @param privateKey the (32 byte) ED25519 seed (private key) of the identity whose signature is going to be generated.
     *
     * @return the signature bytes of the signing operation's result.
     */
    @Override
    public byte[] sign(byte[] message, byte[] privateKey) {
        try{
            Signature signature = new EdDSAEngine(MessageDigest.getInstance(spec.getHashAlgorithm()));
            signature.initSign(new EdDSAPrivateKey(new EdDSAPrivateKeySpec(privateKey, spec)));
            signature.setParameter(EdDSAEngine.ONE_SHOT_MODE);
            signature.update(message);
            return signature.sign();
        }catch(Exception e){
            throw new CryptoException("Signing error", e);
        }
    }

    /**
     * Signs provided message with Ed25519 signing algorithm using BIP32-ED25519 private key
     *
     * @param message the byte array of the message to be signed.
     * @param privateKey the (64 byte) BIP32-ED25519 private key of the identity whose signature is going to be generated.
     * @param publicKey optional (kept for backwards compatibility)
     *
     * @return the signature bytes of the signing operation's result.
     */
    @Override
    public byte[] signExtended(byte[] message, byte[] privateKey, byte[] publicKey) {
        return signExtended(message, privateKey);
    }

    /**
     * Signs provided message with Ed25519 signing algorithm using BIP32-ED25519 private key
     *
     * @param message the byte array of the message to be signed.
     * @param privateKey the (64 byte) BIP32-ED25519 private key of the identity whose signature is going to be generated.
     *
     * @return the signature bytes of the signing operation's result.
     */
    @Override
    public byte[] signExtended(byte[] message, byte[] privateKey) {
        try{
            Signature signature = new EdDSAEngine(MessageDigest.getInstance(spec.getHashAlgorithm()));
            signature.initSign(new EdDSAPrivateKey(new EdDSAPrivateKeySpec(spec, privateKey)));
            signature.setParameter(EdDSAEngine.ONE_SHOT_MODE);
            signature.update(message);
            return signature.sign();
        }catch(Exception e){
            throw new CryptoException("Extended signing error", e);
        }
    }

    /**
     * Verify signature with public key.
     * <p>
     * Verification follows the Haskell cardano-node ({@code Ed25519DSIGN} in cardano-crypto-class), which calls
     * libsodium's {@code crypto_sign_ed25519_verify_detached}: before the cofactorless equation check, the scalar
     * {@code S} must be canonical ({@code S < L}), {@code R} must not be a small-order encoding, and the public key
     * must be a canonical encoding that is not of small order. The same signature therefore verifies here exactly
     * when it verifies on a cardano-node (for example the Ed25519 leaf signatures inside KES). Malformed input
     * returns {@code false} rather than throwing: {@code null}, a signature that is not 64 bytes, and a public key
     * that is not 32 bytes or not a curve point.
     *
     * @param signature Signature to verify
     * @param message payload
     * @param publicKey 32 bytes public key
     * @return true if signature verification is successful, otherwise false
     */
    @Override
    public boolean verify(byte[] signature, byte[] message, byte[] publicKey) {
        if (signature == null || publicKey == null || signature.length != 64 || publicKey.length != 32
                || !scalarIsCanonical(signature) || hasSmallOrder(signature) || !pointIsCanonical(publicKey)
                || hasSmallOrder(publicKey)) {
            return false;
        }
        EdDSAPublicKeySpec pubKey;
        try {
            pubKey = new EdDSAPublicKeySpec(publicKey, spec);
        } catch (IllegalArgumentException e) {
            return false; // not a curve point (libsodium: ge25519_frombytes_negate_vartime fails)
        }
        try {
            Signature sgr = new EdDSAEngine(MessageDigest.getInstance(spec.getHashAlgorithm()));
            PublicKey vKey = new EdDSAPublicKey(pubKey);
            sgr.initVerify(vKey);
            sgr.setParameter(EdDSAEngine.ONE_SHOT_MODE);
            sgr.update(message);

            return sgr.verify(signature);
        } catch (Exception e) {
            throw new CryptoException("Signature verification error", e);
        }
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
