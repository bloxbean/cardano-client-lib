package com.bloxbean.cardano.client.txflow.store;

/**
 * Store-contract failure with a stable machine-readable error code.
 *
 * <p>Adapters use codes to distinguish conditions such as optimistic revision conflicts, stale
 * fences, idempotency conflicts, and compacted event history without requiring callers to parse
 * a diagnostic message.</p>
 */
public class FlowStoreException extends RuntimeException {
    /** Stable machine-readable classification of the store failure. */
    private final String code;

    /**
     * Creates a store failure.
     *
     * @param code stable machine-readable error code
     * @param message human-readable diagnostic
     */
    public FlowStoreException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * Returns the stable error code suitable for programmatic handling.
     *
     * @return store error code
     */
    public String getCode() { return code; }
}
