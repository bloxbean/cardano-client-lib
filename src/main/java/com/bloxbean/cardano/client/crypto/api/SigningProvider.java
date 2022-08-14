package com.bloxbean.cardano.client.crypto.api;

/**
 * Implement this interface to provide signing capability
 */
public interface SigningProvider {

    /**
     * Sign a message with a ed25519 private key
     * @param message
     * @param privateKey
     * @return Signature
     */
    byte[] sign(byte[] message, byte[] privateKey);

    /**
     * Sign a message with a ed25519 expanded private key
     * @param message
     * @param privateKey
     * @return Signature
     * @deprecated use {@link #signExtended(byte[], byte[])} instead.
     */
    @Deprecated
    byte[] signExtended(byte[] message, byte[] privateKey, byte[] publicKey);

    /**
     * Sign a message with a ed25519 expanded private key
     * @param message
     * @param privateKey
     * @return
     */
    byte[] signExtended(byte[] message, byte[] privateKey);

    /**
     * Verify signature
     * @param signature
     * @param message
     * @param publicKey
     * @return true if signature verification is successful, otherwise false
     */
    boolean verify(byte[] signature, byte[] message, byte[] publicKey);

}
