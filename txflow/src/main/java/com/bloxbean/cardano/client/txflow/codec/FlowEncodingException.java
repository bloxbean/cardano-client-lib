package com.bloxbean.cardano.client.txflow.codec;

/**
 * Signals that a {@code TxFlow} cannot be represented in the requested serialized
 * format or schema version.
 *
 * <p>Examples include requesting JSON for the legacy schema or attempting to write
 * a Java-only or multi-transaction step as a portable document.</p>
 */
public class FlowEncodingException extends RuntimeException {
    /**
     * Creates an encoding failure with a descriptive message.
     *
     * @param message description of the unsupported or failed encoding
     */
    public FlowEncodingException(String message) {
        super(message);
    }

    /**
     * Creates an encoding failure caused by serialization infrastructure.
     *
     * @param message description of the failed encoding
     * @param cause underlying serialization failure
     */
    public FlowEncodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
