package com.bloxbean.cardano.client.crypto.api.impl;

import com.bloxbean.cardano.client.crypto.CryptoException;
import com.bloxbean.cardano.client.crypto.api.Ed25519Verifier;
import com.bloxbean.cardano.client.crypto.api.SigningProvider;
import com.bloxbean.cardano.client.crypto.config.CryptoConfiguration;
import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec;

import java.security.MessageDigest;
import java.security.Signature;
import java.util.Objects;

public class EdDSASigningProvider implements SigningProvider {
    private static final EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);

    private final Ed25519Verifier verifier;

    /** Verifies with the {@link Ed25519Verifier} of {@link CryptoConfiguration}, at the time of each verification. */
    public EdDSASigningProvider() {
        this.verifier = null;
    }

    /**
     * @param verifier the {@link Ed25519Verifier} for {@link #verify}; signing is the same either way
     */
    public EdDSASigningProvider(Ed25519Verifier verifier) {
        this.verifier = Objects.requireNonNull(verifier, "verifier");
    }

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
     * <p>
     * The {@link Ed25519Verifier} given to the constructor verifies, or else the one of {@link CryptoConfiguration}:
     * {@link BouncyCastleEd25519Verifier} by default, {@link I2pEd25519Verifier} when selected. Both have these
     * outcomes. The default throws a {@link LinkageError} when the bcprov on the classpath fails its self-check (it
     * needs bcprov 1.67 or newer); select {@link I2pEd25519Verifier} to verify without BouncyCastle.
     *
     * @param signature Signature to verify
     * @param message payload
     * @param publicKey 32 bytes public key
     * @return true if signature verification is successful, otherwise false
     */
    @Override
    public boolean verify(byte[] signature, byte[] message, byte[] publicKey) {
        Ed25519Verifier v = verifier != null ? verifier : CryptoConfiguration.INSTANCE.getEd25519Verifier();
        return v.verify(signature, message, publicKey);
    }
}
