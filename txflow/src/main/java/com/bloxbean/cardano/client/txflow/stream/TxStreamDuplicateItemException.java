package com.bloxbean.cardano.client.txflow.stream;

/**
 * Typed conflict raised when an item identity is redelivered with different
 * content.
 * <p>
 * Live redelivery with an identical item fingerprint attaches to the existing
 * receipt instead; this exception is the stream-level mirror of the engine's
 * {@code TXFLOW_IDEMPOTENCY_CONFLICT} and is never a silent replacement.
 */
public final class TxStreamDuplicateItemException extends TxStreamException {
    private final String itemId;

    /**
     * Creates a duplicate-item conflict.
     *
     * @param itemId conflicting item identity
     * @param message human-readable diagnostic message
     */
    public TxStreamDuplicateItemException(String itemId, String message) {
        super("TXSTREAM_DUPLICATE_ITEM", message);
        this.itemId = itemId;
    }

    /**
     * Returns the conflicting item identity.
     *
     * @return item id whose redelivered content differed
     */
    public String getItemId() {
        return itemId;
    }
}
