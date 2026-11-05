package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;
import com.bloxbean.cardano.client.spec.UnitInterval;

/**
 * The prices of execution units as received, {@code [mem_price : nonnegative_interval, step_price :
 * nonnegative_interval]}.
 *
 * @param span   the prices, as encoded
 * @param memory the price of a memory unit
 * @param steps  the price of a step
 */
public record RawExUnitPrices(CborSpan span, UnitInterval memory, UnitInterval steps) {
    /** The {@code ex_unit_prices} rule. */
    static final RecordSpec RECORD = RecordSpec.positional("ex_unit_prices", FieldType.NONNEGATIVE_INTERVAL,
            FieldType.NONNEGATIVE_INTERVAL);

    /**
     * @param span prices as encoded
     * @return the view
     * @throws CborRuntimeException if they are not an array of two rationals
     */
    public static RawExUnitPrices of(CborSpan span) {
        return read(span, PositionalRecord.of(RECORD, span, true), false);
    }

    /**
     * Reads prices as CCL's model always has (ADR 0003 D7): definite, items after the last ignored, and the numbers of
     * a rational read as {@link RawItems#rational(CborSpan, boolean)} reads them when lenient.
     *
     * @param span prices as encoded
     * @return the view
     * @throws CborRuntimeException if they are not a definite array starting with two rationals
     */
    public static RawExUnitPrices lenient(CborSpan span) {
        return read(span, PositionalRecord.of(RECORD, RawItems.definite(span, RECORD.name()), false), true);
    }

    private static RawExUnitPrices read(CborSpan span, PositionalRecord record, boolean lenient) {
        return new RawExUnitPrices(span, RawItems.rational(record.value(FieldType.NONNEGATIVE_INTERVAL, 0), lenient),
                RawItems.rational(record.value(FieldType.NONNEGATIVE_INTERVAL, 1), lenient));
    }
}
