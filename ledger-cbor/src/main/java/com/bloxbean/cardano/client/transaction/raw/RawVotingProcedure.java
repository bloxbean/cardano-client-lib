package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;

import java.util.Optional;

/**
 * A voting procedure as received, {@code [vote, anchor / nil]}.
 */
public final class RawVotingProcedure {
    /** The {@code voting_procedure} rule. */
    static final RecordSpec RECORD = RecordSpec.positional("voting_procedure", FieldType.VOTE,
            FieldType.OPTIONAL_ANCHOR);

    private final PositionalRecord record;

    private RawVotingProcedure(PositionalRecord record) {
        this.record = record;
    }

    /**
     * @param span a procedure as encoded
     * @return the view
     * @throws CborRuntimeException if it is not an array of two items
     */
    public static RawVotingProcedure of(CborSpan span) {
        return new RawVotingProcedure(PositionalRecord.of(RECORD, span, true));
    }

    /**
     * Reads a procedure as CCL's models always have: an array that a decoded tree marks as indefinite is rejected (ADR
     * 0003 D7).
     *
     * @param span a procedure as encoded
     * @return the view
     * @throws CborRuntimeException if {@link #of(CborSpan)} rejects it, or it is indefinite
     */
    public static RawVotingProcedure lenient(CborSpan span) {
        return of(RawItems.definite(span, RECORD.name()));
    }

    /**
     * @return the vote as encoded: 0 no, 1 yes, 2 abstain; which values exist is not checked
     */
    public int vote() {
        return RawItems.smallUnsigned(record.value(FieldType.VOTE));
    }

    /**
     * @return the anchor, or empty if it is null
     */
    public Optional<RawAnchor> anchor() {
        return Optional.ofNullable(RawItems.nullable(record.value(FieldType.OPTIONAL_ANCHOR), RawAnchor::of));
    }
}
