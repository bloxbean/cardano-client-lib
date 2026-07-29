package com.bloxbean.cardano.client.txflow.stream;

import java.time.Instant;
import java.util.Objects;

/**
 * Time-bounded ownership and fencing token for one stream's dispatch.
 *
 * <p>Mirrors the engine's {@code ExecutionLease} epoch-fencing model, applied to
 * whole-stream dispatch instead of a single execution: at most one instance of a
 * stream id may hold a currently-valid lease and dispatch work
 * (active/standby); the others stand by and take over on the owner's crash or
 * expiry. Every successful {@link TxStreamStateStore#tryAcquireOwnership
 * acquisition} mints a strictly newer positive {@code epoch};
 * {@link TxStreamStateStore#renewOwnership renewal} preserves it. A store fences
 * every renewal on the epoch, so an instance whose lease was superseded (a new
 * owner acquired a higher epoch) or expired-and-taken cannot renew — it must
 * step down. The lease is a valid basis for dispatch only while
 * {@code expiresAt} is strictly after the current time.</p>
 *
 * @param streamId stream protected by the lease
 * @param ownerToken opaque, caller-supplied identity of the owning instance
 * @param epoch monotonically increasing fencing epoch assigned on acquisition
 * @param expiresAt exclusive lease expiry
 */
public record StreamOwnershipLease(String streamId, String ownerToken, long epoch, Instant expiresAt) {
    /**
     * Creates a validated stream-ownership lease value.
     *
     * @param streamId non-blank stream protected by the lease
     * @param ownerToken non-blank opaque identity of the owning instance
     * @param epoch positive fencing epoch assigned on acquisition
     * @param expiresAt exclusive lease expiry; never {@code null}
     */
    public StreamOwnershipLease {
        if (streamId == null || streamId.isBlank()) {
            throw new IllegalArgumentException("ownership lease streamId cannot be null or blank");
        }
        if (ownerToken == null || ownerToken.isBlank()) {
            throw new IllegalArgumentException("ownership lease ownerToken cannot be null or blank");
        }
        if (epoch < 1) {
            throw new IllegalArgumentException("ownership lease epoch must be positive");
        }
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
