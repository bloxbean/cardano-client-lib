package com.bloxbean.cardano.client.crypto.api;

/**
 * Implement this interface to provide signing capability
 */
public interface SigningProvider {

    /**
     * Sign a message with a private key
     * @param message
     * @param privateKey
     * @return Signature
     */
    byte[] sign(byte[] message, byte[] privateKey);

}
