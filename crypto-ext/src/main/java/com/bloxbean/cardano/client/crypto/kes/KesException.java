package com.bloxbean.cardano.client.crypto.kes;

/**
 * Exception thrown for KES-related errors.
 */
public class KesException extends RuntimeException {

    public KesException(String message) {
        super(message);
    }

    public KesException(String message, Throwable cause) {
        super(message, cause);
    }
}
