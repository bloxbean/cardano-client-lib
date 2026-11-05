package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;

import java.util.Optional;

/**
 * A constitution as received, {@code [anchor, guardrails_script_hash / nil]}.
 */
public final class RawConstitution {
    /** The {@code constitution} rule. */
    static final RecordSpec RECORD = RecordSpec.positional("constitution", FieldType.ANCHOR,
            FieldType.OPTIONAL_HASH28);

    private final PositionalRecord record;

    private RawConstitution(PositionalRecord record) {
        this.record = record;
    }

    /**
     * @param span a constitution as encoded
     * @return the view
     * @throws CborRuntimeException if it is not an array of two items
     */
    public static RawConstitution of(CborSpan span) {
        return new RawConstitution(PositionalRecord.of(RECORD, span, true));
    }

    /**
     * Reads a constitution as CCL's models always have: an array that a decoded tree marks as indefinite is rejected
     * (ADR 0003 D7).
     *
     * @param span a constitution as encoded
     * @return the view
     * @throws CborRuntimeException if {@link #of(CborSpan)} rejects it, or it is indefinite
     */
    public static RawConstitution lenient(CborSpan span) {
        return of(RawItems.definite(span, RECORD.name()));
    }

    public RawAnchor anchor() {
        return RawAnchor.of(record.value(FieldType.ANCHOR));
    }

    /**
     * @return the guardrails script hash, of any length, or empty if it is null
     */
    public Optional<byte[]> scriptHash() {
        return Optional.ofNullable(RawItems.nullable(record.value(FieldType.OPTIONAL_HASH28), RawItems::bytes));
    }
}
