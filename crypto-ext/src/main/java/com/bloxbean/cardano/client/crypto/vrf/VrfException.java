package com.bloxbean.cardano.client.crypto.vrf;

/**
 * Exception thrown for VRF-related errors.
 */
public class VrfException extends RuntimeException {

    public VrfException(String message) {
        super(message);
    }

    public VrfException(String message, Throwable cause) {
        super(message, cause);
    }
}
