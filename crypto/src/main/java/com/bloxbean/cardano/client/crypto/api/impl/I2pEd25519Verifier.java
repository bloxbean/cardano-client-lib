package com.bloxbean.cardano.client.crypto.api.impl;

import com.bloxbean.cardano.client.crypto.CryptoException;
import com.bloxbean.cardano.client.crypto.api.Ed25519Verifier;
import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;

import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;

/**
 * Ed25519 verification through BloxBean's i2p fork ({@code net-i2p-crypto-eddsa}), after libsodium's input checks:
 * the verifier this library used before {@link BouncyCastleEd25519Verifier}. The fork's equation is cofactorless and
 * compares the bytes of {@code R}, as libsodium does. Select it with
 * {@code CryptoConfiguration.INSTANCE.setEd25519Verifier(new I2pEd25519Verifier())}.
 */
public class I2pEd25519Verifier implements Ed25519Verifier {
    private static final EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);

    @Override
    public boolean verify(byte[] signature, byte[] message, byte[] publicKey) {
        if (!Ed25519InputChecks.pass(signature, publicKey)) {
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
}
