package com.bloxbean.cardano.vds.jmt.store;

/**
 * Thrown when persisted JMT metadata is absent, malformed, or incompatible with the requested
 * cryptographic/storage profile.
 */
public final class JmtFormatMismatchException extends IllegalStateException {

    public JmtFormatMismatchException(String message) {
        super(message);
    }

    public JmtFormatMismatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
