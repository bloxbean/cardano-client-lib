package com.bloxbean.cardano.client.crypto.vrf;

/**
 * Interface for VRF (Verifiable Random Function) verification.
 */
public interface VrfVerifier {

    /**
     * Verify a VRF proof and compute the VRF output.
     *
     * @param publicKey the VRF public key (32 bytes, encoded Ed25519 point)
     * @param proof     the VRF proof (80 bytes for ECVRF-ED25519-SHA512-Elligator2)
     * @param alpha     the VRF input (arbitrary length)
     * @return VrfResult containing validity and the VRF output hash if valid
     */
    VrfResult verify(byte[] publicKey, byte[] proof, byte[] alpha);
}
