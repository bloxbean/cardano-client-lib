package com.bloxbean.cardano.client.txflow.stream;

import java.time.Instant;
import java.util.Objects;

/**
 * Authoritative registration record for one accepted work item.
 * <p>
 * The record captures the planning metadata the engine never sees: the item's
 * identity, its idempotency claim key, the lane it was accepted on, and the
 * versioned content fingerprint used to resolve live redeliveries.
 *
 * @param itemId caller-visible item identity
 * @param idempotencyKey claim key; defaults to the item id
 * @param laneName user-facing label of the accepting lane
 * @param fingerprint versioned item content fingerprint
 * @param acceptedAt acceptance time
 */
public record TxStreamItemRecord(String itemId, String idempotencyKey, String laneName,
                                 String fingerprint, Instant acceptedAt) {
    /**
     * Validates the registration record.
     *
     * @param itemId non-blank item identity
     * @param idempotencyKey non-blank claim key
     * @param laneName non-blank lane label
     * @param fingerprint non-blank content fingerprint
     * @param acceptedAt acceptance time
     */
    public TxStreamItemRecord {
        requireNonBlank(itemId, "itemId");
        requireNonBlank(idempotencyKey, "idempotencyKey");
        requireNonBlank(laneName, "laneName");
        requireNonBlank(fingerprint, "fingerprint");
        Objects.requireNonNull(acceptedAt, "acceptedAt");
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
    }
}
