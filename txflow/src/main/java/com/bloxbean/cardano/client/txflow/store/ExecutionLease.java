package com.bloxbean.cardano.client.txflow.store;

import java.time.Instant;
import java.util.Objects;

/**
 * Time-bounded ownership and fencing token for mutations of one execution.
 *
 * <p>Every successful acquisition mints a newer positive {@code epoch}; renewal preserves it.
 * Stores compare the execution identity, opaque owner token, and epoch on each mutation so a
 * released or superseded worker cannot write with a stale lease. The lease is valid only while
 * {@code expiresAt} is strictly after the store's current time.</p>
 *
 * @param executionId execution protected by the lease
 * @param ownerToken opaque identity of the owning worker
 * @param epoch monotonically increasing fencing epoch assigned on acquisition
 * @param expiresAt exclusive lease expiry
 */
public record ExecutionLease(String executionId, String ownerToken, long epoch, Instant expiresAt) {
    /**
     * Creates a validated execution lease value.
     *
     * @param executionId non-blank execution protected by the lease
     * @param ownerToken non-blank opaque identity of the owning worker
     * @param epoch positive fencing epoch assigned on acquisition
     * @param expiresAt exclusive lease expiry
     */
    public ExecutionLease {
        FlowStoreTextPolicy.requireIdentifier(executionId, "lease execution identity",
                FlowStoreTextPolicy.MAX_EXECUTION_ID_BYTES);
        FlowStoreTextPolicy.requireIdentifier(ownerToken, "lease owner identity",
                FlowStoreTextPolicy.MAX_OWNER_TOKEN_BYTES);
        if (epoch < 1) throw new IllegalArgumentException("lease epoch must be positive");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
