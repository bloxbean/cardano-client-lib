package com.bloxbean.cardano.client.txflow.store;

/**
 * Outcome of atomically claiming an execution idempotency key.
 *
 * <p>A new claim returns its initial snapshot with {@code created=true}. A matching prior claim
 * returns the existing execution with {@code created=false}. Reusing the key with a different
 * definition or request fingerprint is an error and therefore has no result representation.</p>
 *
 * @param snapshot newly created or matching existing execution snapshot
 * @param created whether this operation inserted the execution and claim
 */
public record IdempotencyClaimResult(FlowExecutionSnapshot snapshot, boolean created) {
}
