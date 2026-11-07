package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;

import java.math.BigInteger;

/**
 * Execution units as received, {@code [mem, steps]}. Tags on an item are looked through, and ranges and the length form
 * are not checked (ADR 0003 D5).
 *
 * @param span  the units, as encoded
 * @param mem   the memory units
 * @param steps the step units
 */
public record RawExUnits(CborSpan span, BigInteger mem, BigInteger steps) {
    /** The {@code ex_units} rule. */
    static final RecordSpec RECORD = RecordSpec.positional("ex_units", FieldType.EX_UNIT, FieldType.EX_UNIT);

    /**
     * @param span units as encoded
     * @return the view
     * @throws CborRuntimeException if they are not an array of two unsigned integers
     */
    public static RawExUnits of(CborSpan span) {
        PositionalRecord record = PositionalRecord.of(RECORD, span, true);
        return new RawExUnits(span, RawItems.unsigned(record.value(FieldType.EX_UNIT, 0)),
                RawItems.unsigned(record.value(FieldType.EX_UNIT, 1)));
    }
}
