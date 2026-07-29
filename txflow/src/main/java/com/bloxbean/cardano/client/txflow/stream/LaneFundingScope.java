package com.bloxbean.cardano.client.txflow.stream;

/**
 * Funding scope backing a stream lane: the declared funding source every
 * transaction dispatched on the lane must draw from.
 * <p>
 * Two source forms are supported. An {@link Kind#ADDRESS address} scope pins
 * the lane to one funding address ({@code from}). A
 * {@link Kind#FUNDING_REF funding-ref} scope pins the lane to a signer
 * reference URI ({@code from_ref}, e.g. {@code account://sender}). Both
 * sources are caller-asserted canonical: scope-overlap detection compares
 * exact {@code kind:source} equality TODAY, so a cross-kind overlap — an
 * address scope and a funding-ref scope that name the same wallet — is NOT
 * detected and is the caller's responsibility until a future resolver
 * performs real source resolution. Designated UTXO-chain scopes arrive with
 * partitioned lanes in a later iteration.
 *
 * @param kind form of the declared funding source
 * @param source declared funding source: an address or a {@code from_ref} URI
 */
public record LaneFundingScope(Kind kind, String source) {
    /** Form of the funding source declared for a lane. */
    public enum Kind {
        /** The lane spends from one funding address ({@code from}). */
        ADDRESS,
        /** The lane spends via a funding reference URI ({@code from_ref}). */
        FUNDING_REF
    }

    /**
     * Validates the funding source declaration.
     *
     * @param kind non-null source form
     * @param source non-blank funding source
     */
    public LaneFundingScope {
        if (kind == null) {
            throw new IllegalArgumentException("lane funding kind cannot be null");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("lane funding source cannot be blank");
        }
    }

    /**
     * Creates an address-backed funding scope.
     *
     * @param address funding address whose UTXOs the lane may spend
     * @return address funding scope
     */
    public static LaneFundingScope address(String address) {
        return new LaneFundingScope(Kind.ADDRESS, address);
    }

    /**
     * Creates a funding scope backed by a funding reference URI
     * ({@code from_ref} / alias source such as {@code account://sender}).
     * <p>
     * The reference string is caller-asserted canonical: two references
     * naming the same wallet are treated as different scopes, and a reference
     * naming the same wallet as an address scope is not detected as an
     * overlap. Keeping such aliases from becoming independent lanes is the
     * caller's responsibility until a future resolver performs real source
     * resolution.
     *
     * @param ref funding reference URI the lane's transactions draw from
     * @return funding-ref funding scope
     */
    public static LaneFundingScope fundingRef(String ref) {
        return new LaneFundingScope(Kind.FUNDING_REF, ref);
    }
}
