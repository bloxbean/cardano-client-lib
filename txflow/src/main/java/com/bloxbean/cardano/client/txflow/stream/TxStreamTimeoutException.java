package com.bloxbean.cardano.client.txflow.stream;

/**
 * Typed timeout thrown when a bounded stream wait does not finish before its
 * caller-supplied deadline. A timeout does not cancel, fail, or otherwise
 * change the underlying work.
 */
public final class TxStreamTimeoutException extends TxStreamException {
    /** Latest projection for item-specific waits; absent for stream-wide waits. */
    private final TxStreamItemResult result;

    /**
     * Creates a drain-timeout exception.
     *
     * @param message human-readable diagnostic message
     * @param cause underlying timeout, or {@code null}
     */
    public TxStreamTimeoutException(String message, Throwable cause) {
        this(message, cause, null);
    }

    /**
     * Creates a timeout carrying the latest item projection observed by the
     * timed operation.
     *
     * @param message human-readable diagnostic message
     * @param cause underlying timeout, or {@code null}
     * @param result latest item projection, or {@code null} for stream-wide waits
     */
    public TxStreamTimeoutException(String message, Throwable cause,
                                    TxStreamItemResult result) {
        super("TXSTREAM_TIMEOUT", message, cause);
        this.result = result;
    }

    /**
     * Returns the latest item projection observed at timeout.
     *
     * @return latest result, or {@code null} for a stream-wide wait
     */
    public TxStreamItemResult result() {
        return result;
    }
}
