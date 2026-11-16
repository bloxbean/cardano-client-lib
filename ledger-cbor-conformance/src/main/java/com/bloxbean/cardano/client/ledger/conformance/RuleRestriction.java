package com.bloxbean.cardano.client.ledger.conformance;

/**
 * A dataset rule narrower than the type the strict read takes it as (a nullable position, another form of auxiliary
 * data, another alternative of {@code plutus_data}, a language): the harness refuses the item before or after the
 * policy reads it, as Haskell's decoder of the narrower type would. Recorded as {@code R:RULE}, apart from the
 * policy's own rejections, so the outcomes file tells one from the other (ADR 0005 D10).
 */
public final class RuleRestriction extends RuntimeException {

    /** @param message what the item is, for people */
    public RuleRestriction(String message) {
        super(message);
    }
}
