package com.bloxbean.cardano.client.crypto.vrf;

/**
 * Interface for VRF (Verifiable Random Function) proof generation.
 */
public interface VrfProver {

    /**
     * Generate a VRF proof for the given input.
     *
     * @param secretKey the VRF secret key (64 bytes: 32-byte seed + 32-byte public key)
     * @param alpha     the VRF input (arbitrary length)
     * @return 80-byte VRF proof (32 bytes Gamma + 16 bytes c + 32 bytes s)
     * @throws VrfException if key format is invalid or proof generation fails
     */
    byte[] prove(byte[] secretKey, byte[] alpha);
}
