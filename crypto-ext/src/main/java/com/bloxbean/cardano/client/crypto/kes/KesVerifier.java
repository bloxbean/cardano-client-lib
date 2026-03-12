package com.bloxbean.cardano.client.crypto.kes;

/**
 * Interface for KES (Key Evolving Signature) verification.
 * KES is used in Cardano for block header signing where keys evolve over time periods.
 */
public interface KesVerifier {

    /**
     * Verify a KES signature.
     *
     * @param signature the KES signature bytes
     * @param message   the signed message
     * @param publicKey the 32-byte KES public key (root hash)
     * @param period    the KES period at which the signature was produced
     * @return true if the signature is valid
     * @throws KesException if the signature or public key has invalid format
     */
    boolean verify(byte[] signature, byte[] message, byte[] publicKey, int period);
}
