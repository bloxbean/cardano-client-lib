package com.bloxbean.cardano.client.txflow.stream;

/**
 * Resolves a dynamically named lane to its {@link ResolvedLane} — the canonical
 * spending identity the stream schedules on and the funding scope it enforces.
 * <p>
 * Required by {@link LanePolicy#explicit()}, where each
 * {@link TxWorkItem.Builder#withLane(String) item names its lane}. The stream
 * invokes the resolver <em>once per lane name at first use</em> and caches the
 * successful result for the stream's lifetime, so resolution cost is paid once
 * per lane, not per item. A resolver that throws or returns {@code null} fails
 * the submitting <em>item</em> with a typed
 * {@code TXSTREAM_LANE_UNRESOLVED} planning failure — never the stream, and
 * never at startup, since dynamic lane names are not known at startup.
 * Failures are not cached and unresolved items are retained nowhere: a later
 * item — including a redelivery of the failed one — retries the resolver
 * fresh once the outage is over.
 * <p>
 * Implementations must be fast and non-blocking: the stream invokes the
 * resolver under an internal lock that serializes first-use resolution, so a
 * slow or blocking resolver stalls every submission that needs a
 * not-yet-cached lane name. Resolve from local configuration or a warmed
 * cache; do not perform network calls inside {@link #resolve(String)}.
 * <p>
 * Contract on the returned lanes:
 * <ul>
 *   <li>Alias lane names that resolve to the same
 *       {@link ResolvedLane#canonicalSpendingIdentity()} are one lane wearing
 *       two labels — they share a single dispatch FIFO and never run
 *       concurrently.</li>
 *   <li>Two lanes whose {@link ResolvedLane#fundingScope() funding scopes} are
 *       equal must claim the same canonical identity; a scope already owned by
 *       a different identity fails the later lane's items with a typed
 *       {@code TXSTREAM_LANE_SCOPE_OVERLAP} failure — overlapping scopes
 *       cannot be independent lanes.</li>
 *   <li>Overlap detection compares exact {@code kind:source} equality TODAY:
 *       a cross-kind overlap — an address scope and a funding-ref scope that
 *       name the same wallet — is NOT detected, and keeping such aliases from
 *       becoming independent lanes is the resolver author's responsibility
 *       until a future resolver performs real source resolution.</li>
 * </ul>
 * Implementations should be deterministic: the same lane name should resolve
 * to the same identity on every process, because the identity is declared as
 * the engine spending resource guarding cross-process safety.
 */
@FunctionalInterface
public interface LaneIdentityResolver {
    /**
     * Resolves one lane name to its scheduling identity and funding scope.
     *
     * @param laneName lane name submitted on a work item
     * @return resolved lane; {@code null} (or a thrown exception) fails the
     *         submitting item typed {@code TXSTREAM_LANE_UNRESOLVED}
     */
    ResolvedLane resolve(String laneName);
}
