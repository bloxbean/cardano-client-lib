package com.bloxbean.cardano.client.txflow.stream;

/**
 * Typed timeout thrown by {@link TxFlowStream#awaitDrain(java.time.Duration)}
 * when accepted items do not settle before the deadline.
 */
public final class TxStreamTimeoutException extends TxStreamException {
    /**
     * Creates a drain-timeout exception.
     *
     * @param message human-readable diagnostic message
     * @param cause underlying timeout, or {@code null}
     */
    public TxStreamTimeoutException(String message, Throwable cause) {
        super("TXSTREAM_TIMEOUT", message, cause);
    }
}
