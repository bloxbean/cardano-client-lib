package com.bloxbean.cardano.client.cip.cip113;

/** Raised when a CIP-113 transaction cannot be assembled or an index cannot be resolved. */
public class Cip113Exception extends RuntimeException {
    public Cip113Exception(String message) {
        super(message);
    }

    public Cip113Exception(String message, Throwable cause) {
        super(message, cause);
    }
}
