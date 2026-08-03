package com.bloxbean.cardano.client.txflow.store;

import java.time.Instant;

/**
 * One durable observation that a transaction was included in a block.
 *
 * <p>Attempt snapshots retain these records as history. A rollback marks the affected record
 * instead of deleting it, allowing recovery and operators to distinguish never-included attempts
 * from attempts whose inclusion was later removed.</p>
 *
 * @param blockHeight observed block height
 * @param blockHash block identity, or {@code null} when the backend did not provide it
 * @param slot observed slot, or {@code 0} when unavailable
 * @param observedAt time at which inclusion was recorded
 * @param rolledBack whether this inclusion was subsequently invalidated
 */
public record InclusionRecord(long blockHeight, String blockHash, long slot,
                              Instant observedAt, boolean rolledBack) {
}
