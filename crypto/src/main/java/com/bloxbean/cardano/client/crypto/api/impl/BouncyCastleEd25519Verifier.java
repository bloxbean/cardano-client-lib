package com.bloxbean.cardano.client.crypto.api.impl;

import com.bloxbean.cardano.client.crypto.CryptoException;
import com.bloxbean.cardano.client.crypto.api.Ed25519Verifier;

/**
 * Ed25519 verification with libsodium's input checks and libsodium's cofactorless equation, run on BouncyCastle's
 * field arithmetic ({@link Ed25519Equation}). It has the outcomes of {@link I2pEd25519Verifier} and allocates about
 * 6 KB per verification instead of about 630 KB. The default {@link Ed25519Verifier}.
 * <p>
 * BouncyCastle's own {@code Ed25519.verify} is not used: it checks the equation times the cofactor and accepts keys
 * and {@code R} with small-order parts that libsodium rejects. On first use the equation checks itself against
 * libsodium's verdicts and, if the bcprov on the classpath disagrees, every verification throws a
 * {@link LinkageError} that names it. It needs bcprov 1.67 or newer.
 */
public class BouncyCastleEd25519Verifier implements Ed25519Verifier {

    @Override
    public boolean verify(byte[] signature, byte[] message, byte[] publicKey) {
        if (!Ed25519InputChecks.pass(signature, publicKey)) {
            return false;
        }
        try {
            // The key is decoded before the message is read, so a key that is not a curve point gives false.
            return Ed25519Equation.holds(publicKey, message, signature);
        } catch (NullPointerException e) {
            if (message != null) {
                throw e;
            }
            // As I2pEd25519Verifier, whose engine fails on a null message once the key has decoded.
            throw new CryptoException("Signature verification error", e);
        }
    }
}
