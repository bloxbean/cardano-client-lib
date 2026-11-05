package com.bloxbean.cardano.client.transaction.raw;

import com.bloxbean.cardano.client.common.cbor.CborSpan;
import com.bloxbean.cardano.client.exception.CborRuntimeException;

/**
 * A transaction input as received, {@code [id : transaction_id, index : uint .size 2]}. The array is checked when the
 * view is created, and an item is read when it is asked for. Hash lengths and ranges are not checked (ADR 0003 D5).
 */
public final class RawInput {
    static final RecordSpec RECORD = RecordSpec.positional("transaction_input", FieldType.HASH32, FieldType.UINT);

    private final CborSpan span;
    private final PositionalRecord record;

    private RawInput(CborSpan span, PositionalRecord record) {
        this.span = span;
        this.record = record;
    }

    /**
     * @param span an input as encoded
     * @return the view
     * @throws CborRuntimeException if it is not an array of two items
     */
    public static RawInput of(CborSpan span) {
        return new RawInput(span, PositionalRecord.of(RECORD, span, true));
    }

    /**
     * @return the input as encoded
     */
    public CborSpan span() {
        return span;
    }

    /**
     * @return the id of the transaction it spends from, of any length
     */
    public byte[] transactionId() {
        return RawItems.bytes(record.value(FieldType.HASH32));
    }

    /**
     * @return the index of the output it spends
     * @throws CborRuntimeException if it is not an unsigned integer that fits an int
     */
    public int index() {
        return RawItems.smallUnsigned(record.value(FieldType.UINT));
    }
}
