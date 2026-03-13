package com.bloxbean.cardano.client.crypto.kes;

/**
 * Interface for KES (Key Evolving Signature) signing.
 * KES is used in Cardano for block header signing where keys evolve over time periods.
 */
public interface KesSigner {

    /**
     * Sign a message using a KES secret key at the given period.
     *
     * @param secretKey the KES secret key bytes (608 bytes for Sum6Kes)
     * @param message   the message to sign
     * @param period    the KES period at which to sign (0-63 for Sum6Kes)
     * @return the KES signature (448 bytes for Sum6Kes)
     * @throws KesException if the key format is invalid or period is out of range
     */
    byte[] sign(byte[] secretKey, byte[] message, int period);
}
