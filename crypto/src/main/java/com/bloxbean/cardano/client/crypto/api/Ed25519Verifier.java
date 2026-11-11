package com.bloxbean.cardano.client.crypto.api;

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
     * @return whether a cardano-node accepts the signature; false for {@code null}, a signature that is not 64 bytes,
     *         a key that is not 32 bytes or a key that is not a curve point
     */
    boolean verify(byte[] signature, byte[] message, byte[] publicKey);
}
