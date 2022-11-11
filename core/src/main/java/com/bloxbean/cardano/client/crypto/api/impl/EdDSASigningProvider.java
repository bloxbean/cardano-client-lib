package com.bloxbean.cardano.client.crypto.api.impl;

import com.bloxbean.cardano.client.crypto.CryptoException;
import com.bloxbean.cardano.client.crypto.api.SigningProvider;
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
     * Verify signature with public key
     * @param signature Signature to verify
     * @param message payload
     * @param publicKey 32 bytes public key
     * @return true if signature verification is successful, otherwise false
     */
    @Override
    public boolean verify(byte[] signature, byte[] message, byte[] publicKey) {
        try {
            Signature sgr = new EdDSAEngine(MessageDigest.getInstance(spec.getHashAlgorithm()));
            EdDSAPublicKeySpec pubKey = new EdDSAPublicKeySpec(publicKey, spec);
            PublicKey vKey = new EdDSAPublicKey(pubKey);
            sgr.initVerify(vKey);
            sgr.setParameter(EdDSAEngine.ONE_SHOT_MODE);
            sgr.update(message);

            return sgr.verify(signature);
        } catch (Exception e) {
            throw new CryptoException("Signature verification error", e);
        }
    }

}
