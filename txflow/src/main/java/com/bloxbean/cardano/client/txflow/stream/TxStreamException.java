package com.bloxbean.cardano.client.txflow.stream;

import java.util.Objects;

/**
 * Base runtime exception for stream-level failures.
 * <p>
 * Every stream failure carries a stable {@code TXSTREAM_*} code intended for
 * programmatic handling, mirroring the engine's {@code TXFLOW_*} error codes.
 */
public class TxStreamException extends RuntimeException {
    private final String code;

    /**
     * Creates a stream exception.
     *
     * @param code stable {@code TXSTREAM_*} error code
     * @param message human-readable diagnostic message
     */
    public TxStreamException(String code, String message) {
        this(code, message, null);
    }

    /**
     * Creates a stream exception with a cause.
     *
     * @param code stable {@code TXSTREAM_*} error code
     * @param message human-readable diagnostic message
     * @param cause underlying failure, or {@code null}
     */
    public TxStreamException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    /**
     * Returns the stable stream error code.
     *
     * @return {@code TXSTREAM_*} error code
     */
    public String getCode() {
        return code;
    }
}
