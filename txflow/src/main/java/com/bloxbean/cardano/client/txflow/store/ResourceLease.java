package com.bloxbean.cardano.client.txflow.store;

import java.time.Instant;
import java.util.Objects;

/**
 * Time-bounded ownership and fencing token for a canonical spending resource.
 *
 * <p>A resource lease associates the resource with both an execution and its current worker.
 * The store validates all three identities plus the acquisition epoch when the lease appears in
 * a {@link MutationFence}. This prevents a superseded owner from committing state after another
 * execution has acquired the same resource.</p>
 *
 * <p>The lease protects TxFlow coordination state; by itself it cannot stop a partitioned worker
 * from submitting bytes that were already signed.</p>
 *
 * @param resourceId canonical identity of the protected spending resource
 * @param executionId execution that owns the resource
 * @param ownerToken opaque identity of the owning worker
 * @param epoch monotonically increasing fencing epoch assigned on acquisition
 * @param expiresAt exclusive lease expiry
 */
public record ResourceLease(String resourceId, String executionId, String ownerToken,
                            long epoch, Instant expiresAt) {
    /**
     * Creates a validated spending-resource lease value.
     *
     * @param resourceId non-blank canonical identity of the protected spending resource
     * @param executionId non-blank execution that owns the resource
     * @param ownerToken non-blank opaque identity of the owning worker
     * @param epoch positive fencing epoch assigned on acquisition
     * @param expiresAt exclusive lease expiry
     */
    public ResourceLease {
        if (resourceId == null || resourceId.isBlank() || executionId == null
                || executionId.isBlank() || ownerToken == null || ownerToken.isBlank()) {
            throw new IllegalArgumentException("resource lease identities cannot be blank");
        }
        if (epoch < 1) throw new IllegalArgumentException("lease epoch must be positive");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
