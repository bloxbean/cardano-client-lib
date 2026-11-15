package com.bloxbean.cardano.client.crypto.api;

import com.bloxbean.cardano.client.crypto.CryptoException;

/**
 * Ed25519 signature verification with the outcomes of a cardano-node, which verifies through libsodium's
 * {@code crypto_sign_ed25519_verify_detached}.
 * <p>
 * Two implementations give the same outcomes: {@code BouncyCastleEd25519Verifier} (the default) and
 * {@code I2pEd25519Verifier}. Select one with
 * {@code CryptoConfiguration.INSTANCE.setEd25519Verifier(...)}.
 */
public interface Ed25519Verifier {

    /**
     * @param signature a 64-byte signature {@code R || S}
     * @param message   the message
     * @param publicKey a 32-byte public key
     * @return whether a cardano-node accepts the signature; false for a {@code null} signature or key, a signature
     *         that is not 64 bytes, a key that is not 32 bytes, a signature or key that libsodium's input checks
     *         refuse, or a key that is not a curve point
     * @throws CryptoException for a {@code null} message when the signature and key pass those checks and the key is
     *         a curve point; the message is read only after them, so otherwise a {@code null} message gives false
     * @throws LinkageError from {@code BouncyCastleEd25519Verifier} when the bcprov on the classpath fails its
     *         self-check (it needs bcprov 1.67 or newer)
     */
    boolean verify(byte[] signature, byte[] message, byte[] publicKey);
}
