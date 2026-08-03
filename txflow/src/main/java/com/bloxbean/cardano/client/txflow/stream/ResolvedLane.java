package com.bloxbean.cardano.client.txflow.stream;

import com.bloxbean.cardano.client.txflow.store.FlowStoreTextPolicy;

import java.util.Objects;

/**
 * Statically resolved stream lane.
 * <p>
 * A lane separates naming from identity: {@code laneName} is a user-facing
 * label used in receipts, stats, and diagnostics, while
 * {@code canonicalSpendingIdentity} is the identity the stream schedules on and
 * declares as the engine spending resource. Two labels that resolve to the same
 * canonical identity are one lane wearing two names — scheduling never keys on
 * the label. The funding scope records the UTXO set backing the lane so lane
 * enforcement can be mechanical rather than documentation.
 *
 * @param laneName user-facing label; never a scheduling key
 * @param canonicalSpendingIdentity scheduling key, also declared as the
 *        {@code FlowExecutionRequest} spending resource
 * @param fundingScope funding address (iteration 1A) backing this lane
 */
public record ResolvedLane(String laneName, String canonicalSpendingIdentity,
                           LaneFundingScope fundingScope) {
    /**
     * Validates lane naming and identity.
     *
     * @param laneName non-blank user-facing label
     * @param canonicalSpendingIdentity non-blank canonical spending identity
     * @param fundingScope funding scope backing the lane
     */
    public ResolvedLane {
        if (laneName == null || laneName.isBlank()) {
            throw new IllegalArgumentException("laneName cannot be blank");
        }
        FlowStoreTextPolicy.requireIdentifier(canonicalSpendingIdentity,
                "canonicalSpendingIdentity", FlowStoreTextPolicy.MAX_RESOURCE_ID_BYTES);
        Objects.requireNonNull(fundingScope, "fundingScope");
    }

    /**
     * Creates a lane funded by one address.
     * <p>
     * The canonical spending identity is derived from the address, so two lanes
     * created from the same address always share one identity regardless of
     * their labels.
     *
     * @param laneName user-facing lane label
     * @param address funding address whose UTXOs the lane spends
     * @return resolved lane with an address funding scope
     */
    public static ResolvedLane ofAddress(String laneName, String address) {
        LaneFundingScope scope = LaneFundingScope.address(address);
        return new ResolvedLane(laneName, "addr:" + scope.source(), scope);
    }

    /**
     * Creates a lane funded via a funding reference URI ({@code from_ref} /
     * alias source such as {@code account://sender}).
     * <p>
     * The canonical spending identity is derived from the reference string,
     * which is caller-asserted canonical: overlap detection is exact
     * {@code kind:source} equality TODAY, so two references naming the same
     * wallet — or a reference and an {@link #ofAddress(String, String)
     * address lane} naming the same wallet — are treated as distinct lanes.
     * Preventing such aliases from running concurrently is the caller's
     * responsibility until a future resolver performs real source resolution.
     *
     * @param laneName user-facing lane label
     * @param ref funding reference URI the lane's transactions draw from
     * @return resolved lane with a funding-ref funding scope
     */
    public static ResolvedLane ofFundingRef(String laneName, String ref) {
        LaneFundingScope scope = LaneFundingScope.fundingRef(ref);
        return new ResolvedLane(laneName, "ref:" + scope.source(), scope);
    }
}
