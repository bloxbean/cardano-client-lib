package com.bloxbean.cardano.client.crypto;

import com.bloxbean.cardano.client.crypto.exception.KeyException;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.util.HexUtil;
import org.bouncycastle.crypto.digests.Blake2bDigest;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;

import java.security.SecureRandom;

public class KeyGenUtil {

    public static Keys generateKey() throws CborSerializationException {
        SecureRandom random = new SecureRandom();
        Ed25519PrivateKeyParameters pvtKeyParameters = new Ed25519PrivateKeyParameters(random);
        byte[] prvKeyBytes = pvtKeyParameters.getEncoded();
        byte[] pubKeyBytes =  pvtKeyParameters.generatePublicKey().getEncoded();

        SecretKey secretKey = SecretKey.create(prvKeyBytes);
        VerificationKey verificationKey = VerificationKey.create(pubKeyBytes);

        return new Keys(secretKey, verificationKey);
    }

    public static VerificationKey getPublicKeyFromPrivateKey(SecretKey secretKey) throws CborSerializationException {
        byte[] pvtKeyBytes = secretKey.getBytes();
        if(pvtKeyBytes == null || pvtKeyBytes.length == 0)
            throw new KeyException("Wrong private key length : " + pvtKeyBytes != null? pvtKeyBytes.length + "" : "");

        byte[] pubKeyBytes = getPublicKeyFromPrivateKey(pvtKeyBytes);

        return VerificationKey.create(pubKeyBytes);
    }

    public static String getKeyHash(VerificationKey vkey) {
        if(vkey == null)
            throw new KeyException("Verification key can't be null");

        try {
            byte[] hashBytes = blake2bHash224(vkey.getBytes());
            return HexUtil.encodeHexString(hashBytes);
        } catch (Exception e) {
            throw new KeyException("Key hash generation failed", e);
        }
    }

    public static byte[] getPublicKeyFromPrivateKey(byte[] privateKeyBytes) {
        Ed25519PrivateKeyParameters privateKeyRebuild = new Ed25519PrivateKeyParameters(privateKeyBytes, 0);
        Ed25519PublicKeyParameters publicKeyRebuild = privateKeyRebuild.generatePublicKey();
        return publicKeyRebuild.getEncoded();
    }

    /**
     * @deprecated Use {@link Blake2bUtil#blake2bHash224(byte[])}
     * @param in
     * @return
     */
    @Deprecated
    public static byte[] blake2bHash224(byte[] in) {
        return Blake2bUtil.blake2bHash224(in);
    }

    /**
     * @deprecated Use {@link Blake2bUtil#blake2bHash256(byte[])}
     * @param in
     * @return
     */
    @Deprecated
    public static byte[] blake2bHash256(byte[] in) {
        return Blake2bUtil.blake2bHash256(in);
    }
}
